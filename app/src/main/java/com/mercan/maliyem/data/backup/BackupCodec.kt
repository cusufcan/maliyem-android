package com.mercan.maliyem.data.backup

import com.mercan.maliyem.data.local.DefaultCategories
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json

sealed interface BackupDecodeResult {
    /** [categoryCount] dosyadaki sayıdır; [backup] eksik "Diğer" kategorilerini de içerir. */
    class Valid internal constructor(val backup: BackupFile, val categoryCount: Int) : BackupDecodeResult
    data object Invalid : BackupDecodeResult
}

/** Dosyayı veritabanına dokunmadan doğrular ve eksik "Diğer" kategorilerini tamamlar. */
object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(backup)

    fun decode(content: String): BackupDecodeResult {
        val backup = try {
            json.decodeFromString<BackupFile>(content.removePrefix("\uFEFF"))
        } catch (_: IllegalArgumentException) {
            return BackupDecodeResult.Invalid
        }
        return validate(backup)
    }

    fun validate(backup: BackupFile): BackupDecodeResult {
        if (backup.format != BackupFile.FORMAT || backup.version !in 1..BackupFile.VERSION) {
            return BackupDecodeResult.Invalid
        }

        val categoriesById = backup.categories.associateBy { it.id }
        val categoriesWithKey = backup.categories.filter { it.key != null }
        val categoriesByKey = categoriesWithKey.associateBy { it.key }
        if (categoriesById.size != backup.categories.size ||
            categoriesByKey.size != categoriesWithKey.size ||
            categoriesById.keys.any { it <= 0 || it == Long.MAX_VALUE }
        ) return BackupDecodeResult.Invalid

        val transactionIds = HashSet<Long>()
        for (transaction in backup.transactions) {
            if (transaction.id <= 0 || transaction.id == Long.MAX_VALUE || !transactionIds.add(transaction.id) ||
                transaction.amountMinor <= 0 || categoriesById[transaction.categoryId]?.type != transaction.type
            ) return BackupDecodeResult.Invalid
            try {
                LocalDate.parse(transaction.date)
            } catch (_: DateTimeParseException) {
                return BackupDecodeResult.Invalid
            }
        }

        val missing = mutableListOf<BackupCategory>()
        var nextId = 1L
        val keys = setOf(DefaultCategories.OTHER_EXPENSE, DefaultCategories.OTHER_INCOME)
        for (fallback in DefaultCategories.all.filter { it.key in keys }) {
            val existing = categoriesByKey[fallback.key]
            if (existing != null) {
                if (existing.type != fallback.type) return BackupDecodeResult.Invalid
                continue
            }
            while (nextId in categoriesById) nextId++
            val sortOrder = backup.categories.filter { it.type == fallback.type }.maxOfOrNull { it.sortOrder } ?: 0
            missing += BackupCategory(nextId++, fallback.type, key = fallback.key, icon = fallback.icon, sortOrder = sortOrder)
        }
        return BackupDecodeResult.Valid(backup.copy(categories = backup.categories + missing), backup.categories.size)
    }
}
