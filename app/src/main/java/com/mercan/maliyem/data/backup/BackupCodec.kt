package com.mercan.maliyem.data.backup

import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.occurrenceOnOrAfter
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface BackupDecodeResult {
    /** [categoryCount] dosyadaki sayıdır; [backup] eksik "Diğer" kategorilerini de içerir. */
    class Valid internal constructor(val backup: BackupFile, val categoryCount: Int) : BackupDecodeResult
    data object IdOutOfRange : BackupDecodeResult
    data object Invalid : BackupDecodeResult
}

/** Dosyayı veritabanına dokunmadan doğrular ve eksik "Diğer" kategorilerini tamamlar. */
object BackupCodec {
    const val MAX_ID = 9_007_199_254_740_992L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(backup)

    fun decode(content: String): BackupDecodeResult {
        val backup = try {
            val fields = json.parseToJsonElement(content.removePrefix("\uFEFF")).jsonObject
            val version = fields["version"]?.jsonPrimitive?.intOrNull
            // Eski dosyalar bu adlarla tanınmayan, farklı biçimde alanlar taşıyabilir.
            val unknownLegacyFields = buildSet {
                if (version != null && version < 4) addAll(listOf("budgets", "overallBudgetMinor"))
                if (version != null && version < 5) addAll(listOf("savingsGoals", "goalEntries"))
            }
            json.decodeFromJsonElement(BackupFile.serializer(), JsonObject(fields - unknownLegacyFields))
        } catch (_: IllegalArgumentException) {
            return BackupDecodeResult.Invalid
        }
        return validate(backup)
    }

    fun validate(file: BackupFile): BackupDecodeResult {
        if (file.format != BackupFile.FORMAT || file.version !in 1..BackupFile.VERSION) {
            return BackupDecodeResult.Invalid
        }

        val withAccounts = if (file.version == 1) {
            val cash = DefaultAccounts.cash()
            file.copy(
                accounts = listOf(BackupAccount(cash.id, cash.key, icon = cash.icon, sortOrder = cash.sortOrder)),
                transactions = file.transactions.map { it.copy(accountId = cash.id) },
                transfers = emptyList(),
            )
        } else file

        val withRules = if (file.version < 3) withAccounts.copy(
            recurringRules = emptyList(),
            transactions = withAccounts.transactions.map { it.copy(recurringRuleId = null) },
        ) else withAccounts

        val withBudgets = if (file.version < 4) withRules.copy(
            budgets = emptyList(),
            overallBudgetMinor = null,
        ) else withRules

        val backup = if (file.version < 5) withBudgets.copy(
            version = BackupFile.VERSION,
            savingsGoals = emptyList(),
            goalEntries = emptyList(),
        ) else withBudgets

        val ids = sequence {
            backup.accounts.forEach { yield(it.id) }
            backup.categories.forEach { yield(it.id) }
            backup.transactions.forEach {
                yield(it.id)
                yield(it.categoryId)
                yield(it.accountId)
                it.recurringRuleId?.let { id -> yield(id) }
            }
            backup.transfers.forEach {
                yield(it.id)
                yield(it.fromAccountId)
                yield(it.toAccountId)
            }
            backup.recurringRules.forEach {
                yield(it.id)
                yield(it.categoryId)
                yield(it.accountId)
            }
            backup.savingsGoals.forEach { yield(it.id) }
            backup.goalEntries.forEach {
                yield(it.id)
                yield(it.goalId)
            }
            backup.budgets.forEach {
                yield(it.id)
                yield(it.categoryId)
            }
        }
        if (ids.any { it > MAX_ID }) return BackupDecodeResult.IdOutOfRange

        val accountIds = backup.accounts.map { it.id }.toSet()
        val accountKeys = backup.accounts.mapNotNull { it.key }
        if (accountIds.size != backup.accounts.size || accountIds.any { it <= 0 } ||
            accountKeys.distinct().size != accountKeys.size || DefaultAccounts.CASH !in accountKeys ||
            backup.accounts.none { !it.archived }
        ) return BackupDecodeResult.Invalid

        val transferIds = HashSet<Long>()
        for (transfer in backup.transfers) {
            if (transfer.id <= 0 || !transferIds.add(transfer.id) ||
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
            categoriesById.keys.any { it <= 0 }
        ) return BackupDecodeResult.Invalid

        if (backup.overallBudgetMinor?.let { it <= 0 } == true) return BackupDecodeResult.Invalid
        val budgetIds = HashSet<Long>()
        val budgetCategories = HashSet<Long>()
        for (budget in backup.budgets) {
            if (budget.id <= 0 || !budgetIds.add(budget.id) ||
                budget.limitMinor <= 0 || categoriesById[budget.categoryId]?.type != TransactionType.EXPENSE ||
                !budgetCategories.add(budget.categoryId)
            ) return BackupDecodeResult.Invalid
        }

        val goalBalances = HashMap<Long, Long>()
        for (goal in backup.savingsGoals) {
            if (goal.id <= 0 || goalBalances.put(goal.id, 0L) != null ||
                goal.name.isBlank() || goal.targetMinor <= 0 || goal.icon.isBlank() ||
                goal.deadline?.let { !validDate(it) } == true
            ) return BackupDecodeResult.Invalid
        }
        var previousEntryId = 0L
        for (entry in backup.goalEntries.sortedBy { it.id }) {
            val saved = goalBalances[entry.goalId] ?: return BackupDecodeResult.Invalid
            if (entry.id <= previousEntryId || !validDate(entry.date) || entry.amountMinor == 0L ||
                entry.amountMinor < -saved || entry.amountMinor > Long.MAX_VALUE - saved
            ) return BackupDecodeResult.Invalid
            goalBalances[entry.goalId] = saved + entry.amountMinor
            previousEntryId = entry.id
        }

        val ruleIds = HashSet<Long>()
        for (rule in backup.recurringRules) {
            val start = parseDate(rule.startDate) ?: return BackupDecodeResult.Invalid
            val next = parseDate(rule.nextDate) ?: return BackupDecodeResult.Invalid
            val end = rule.endDate?.let { parseDate(it) ?: return BackupDecodeResult.Invalid }
            if (rule.id <= 0 || !ruleIds.add(rule.id) ||
                rule.amountMinor <= 0 || categoriesById[rule.categoryId]?.type != rule.type ||
                rule.accountId !in accountIds || next < start || (end != null && end < start) ||
                (next != LocalDate.MAX && next != occurrenceOnOrAfter(start, rule.frequency, next))
            ) return BackupDecodeResult.Invalid
        }

        val transactionIds = HashSet<Long>()
        for (transaction in backup.transactions) {
            if (transaction.id <= 0 || !transactionIds.add(transaction.id) ||
                transaction.amountMinor <= 0 || categoriesById[transaction.categoryId]?.type != transaction.type ||
                transaction.accountId !in accountIds || !validDate(transaction.date) ||
                (transaction.recurringRuleId != null && transaction.recurringRuleId !in ruleIds)
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

    private fun validDate(value: String): Boolean = parseDate(value) != null

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}
