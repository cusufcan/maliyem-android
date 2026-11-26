package com.mercan.maliyem.data.backup

import com.mercan.maliyem.data.local.DefaultAccounts
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

    fun validate(file: BackupFile): BackupDecodeResult {
        if (file.format != BackupFile.FORMAT || file.version !in 1..BackupFile.VERSION) {
            return BackupDecodeResult.Invalid
        }

        val backup = if (file.version == 1) {
            val cash = DefaultAccounts.cash()
            file.copy(
                version = BackupFile.VERSION,
                accounts = listOf(BackupAccount(cash.id, cash.key, icon = cash.icon, sortOrder = cash.sortOrder)),
                transactions = file.transactions.map { it.copy(accountId = cash.id) },
                transfers = emptyList(),
            )
        } else file

        val accountIds = backup.accounts.map { it.id }.toSet()
        val accountKeys = backup.accounts.mapNotNull { it.key }
        if (accountIds.size != backup.accounts.size || accountIds.any { it <= 0 || it == Long.MAX_VALUE } ||
            accountKeys.distinct().size != accountKeys.size || DefaultAccounts.CASH !in accountKeys ||
            backup.accounts.none { !it.archived }
        ) return BackupDecodeResult.Invalid

        val transferIds = HashSet<Long>()
        for (transfer in backup.transfers) {
            if (transfer.id <= 0 || transfer.id == Long.MAX_VALUE || !transferIds.add(transfer.id) ||
                transfer.amountMinor <= 0 || transfer.fromAccountId == transfer.toAccountId ||
                transfer.fromAccountId !in accountIds || transfer.toAccountId !in accountIds ||
                !validDate(transfer.date)
            ) return BackupDecodeResult.Invalid
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
                transaction.amountMinor <= 0 || categoriesById[transaction.categoryId]?.type != transaction.type ||
                transaction.accountId !in accountIds || !validDate(transaction.date)
            ) return BackupDecodeResult.Invalid
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

    private fun validDate(value: String): Boolean = try {
        LocalDate.parse(value)
        true
    } catch (_: DateTimeParseException) {
        false
    }
}
