package com.mercan.maliyem.data.csv

import android.content.Context
import android.content.res.Configuration
import androidx.room.withTransaction
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransferEntity
import com.mercan.maliyem.data.repository.fallbackKey
import com.mercan.maliyem.widget.WidgetUpdater
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

internal data class CsvNewCategory(val name: String, val type: TransactionType)
internal data class CsvPreview(
    val total: Int,
    val ready: Int,
    val duplicates: Int,
    val issues: List<CsvIssue>,
    val newCategories: List<CsvNewCategory>,
    val newAccounts: List<String>,
)

internal data class CsvImportResult(val written: Int, val skippedDuplicates: Int, val errors: Int)

/** CSV exports use the app language for built-in names, so accept both translations. */
internal class CsvImportLabels(private val names: Map<Int, List<String>>) {
    operator fun get(id: Int?): List<String> = names[id].orEmpty()

    companion object {
        fun localized(context: Context): CsvImportLabels {
            val translations = listOf(Locale.ENGLISH, Locale.forLanguageTag("tr")).map { locale ->
                val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
                context.createConfigurationContext(config).resources
            }
            val ids = DefaultCategories.all.map { it.nameRes } + checkNotNull(DefaultAccounts.nameRes(DefaultAccounts.CASH))
            return CsvImportLabels(ids.associateWith { id -> translations.map { it.getString(id) } })
        }
    }
}

internal class CsvImportRepository @Inject constructor(
    private val db: MaliyemDatabase,
    private val clock: Clock,
    private val widgetUpdater: WidgetUpdater,
) {
    suspend fun preview(csv: MappedCsv, skipDuplicates: Boolean, labels: CsvImportLabels): CsvPreview =
        db.withTransaction { plan(csv, skipDuplicates, labels).preview }

    suspend fun import(csv: MappedCsv, skipDuplicates: Boolean, labels: CsvImportLabels): CsvImportResult {
        val result = db.withTransaction {
            // Preview may have been open while another writer changed the ledger.
            val plan = plan(csv, skipDuplicates, labels)
            val accountIds = createAccounts(plan)
            val categoryIds = createCategories(plan)
            val now = clock.millis()
            val transactions = mutableListOf<TransactionEntity>()
            val transfers = mutableListOf<TransferEntity>()
            for (row in plan.rows) {
                val entry = row.entry
                val accountId = row.account.id ?: accountIds.getValue(row.account.name)
                val category = row.category
                if (category != null) {
                    transactions.add(TransactionEntity(
                        type = category.type, amountMinor = entry.amountMinor,
                        categoryId = category.id ?: categoryIds.getValue(CsvNewCategory(category.name, category.type)),
                        date = entry.date, note = entry.note, createdAt = now, accountId = accountId,
                    ))
                } else {
                    val destination = checkNotNull(row.destination)
                    transfers.add(TransferEntity(
                        fromAccountId = accountId, toAccountId = destination.id ?: accountIds.getValue(destination.name),
                        amountMinor = entry.amountMinor, date = entry.date, note = entry.note, createdAt = now,
                    ))
                }
            }
            db.transactionDao().insertAll(transactions)
            db.transferDao().insertAll(transfers)
            CsvImportResult(plan.rows.size, plan.preview.duplicates, plan.preview.issues.size)
        }
        if (result.written > 0) widgetUpdater.refresh()
        return result
    }

    private data class CategoryRef(val name: String, val type: TransactionType, val id: Long? = null)
    private data class AccountRef(val name: String, val id: Long? = null)
    private data class ImportRow(
        val entry: CsvEntry,
        val account: AccountRef,
        val category: CategoryRef?,
        val destination: AccountRef?,
    )

    private data class ImportPlan(
        val preview: CsvPreview,
        val rows: List<ImportRow>,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
    )

    private suspend fun plan(csv: MappedCsv, skipDuplicates: Boolean, labels: CsvImportLabels): ImportPlan {
        val accounts = db.accountDao().getAll()
        val categories = db.categoryDao().getAll()
        val accountsByKey = mutableMapOf<String, AccountRef>()
        val accountNames = mutableMapOf<String, AccountRef>()
        val accountAliases = mutableMapOf<String, AccountRef>()
        for (account in accounts) {
            val names = labels[DefaultAccounts.nameRes(account.key)]
            val ref = AccountRef(account.name ?: names.firstOrNull().orEmpty(), account.id)
            account.key?.let { accountsByKey[it] = ref }
            account.name?.let { accountNames.putIfAbsent(it, ref) }
            for (label in names + listOfNotNull(account.name)) accountAliases.putIfAbsent(label.csvNameKey(), ref)
        }
        val categoriesByKey = mutableMapOf<String, CategoryRef>()
        val categoryNames = mutableMapOf<Pair<TransactionType, String>, CategoryRef>()
        val categoryAliases = mutableMapOf<Pair<TransactionType, String>, CategoryRef>()
        for (category in categories) {
            val names = labels[DefaultCategories.nameRes(category.key)]
            val ref = CategoryRef(category.name ?: names.firstOrNull().orEmpty(), category.type, category.id)
            category.key?.let { categoriesByKey[it] = ref }
            category.name?.let { categoryNames.putIfAbsent(category.type to it, ref) }
            for (label in names + listOfNotNull(category.name)) {
                categoryAliases.putIfAbsent(category.type to label.csvNameKey(), ref)
            }
        }
        val pendingAccounts = mutableMapOf<String, AccountRef>()
        val pendingCategories = mutableMapOf<Pair<TransactionType, String>, CategoryRef>()
        fun resolveAccount(name: String?): AccountRef {
            if (name == null) return accountsByKey.getValue(DefaultAccounts.CASH)
            val key = name.csvNameKey()
            return accountNames[name] ?: accountAliases[key] ?: pendingAccounts.getOrPut(key) { AccountRef(name) }
        }
        fun resolveCategory(type: TransactionType, name: String?): CategoryRef {
            if (name == null) return categoriesByKey.getValue(fallbackKey(type))
            val key = type to name.csvNameKey()
            return categoryNames[type to name] ?: categoryAliases[key] ?:
                pendingCategories.getOrPut(key) { CategoryRef(name, type) }
        }

        val issues = csv.issues.toMutableList()
        val candidates = csv.entries.mapNotNull { entry ->
            val account = resolveAccount(entry.account)
            val destination = entry.destination?.let(::resolveAccount)
            if (destination == account) {
                issues.add(CsvIssue(entry.line, CsvRowError.TRANSFER_ACCOUNTS))
                null
            } else {
                ImportRow(entry, account, entry.type?.let { resolveCategory(it, entry.category) }, destination)
            }
        }
        val rows = if (skipDuplicates) removeDuplicates(candidates) else candidates
        val newCategories = rows.mapNotNull { it.category?.takeIf { ref -> ref.id == null } }
            .distinct().map { CsvNewCategory(it.name, it.type) }
        val newAccounts = rows.asSequence().flatMap { sequenceOf(it.account, it.destination) }
            .filterNotNull().filter { it.id == null }.map { it.name }.distinct().toList()
        val preview = CsvPreview(
            csv.entries.size + csv.issues.size, rows.size, candidates.size - rows.size,
            issues.sortedBy { it.line }, newCategories, newAccounts,
        )
        return ImportPlan(preview, rows, accounts, categories)
    }

    private data class TransactionKey(
        val date: LocalDate, val amount: Long, val type: TransactionType, val note: String, val category: Long,
    )

    private data class TransferKey(
        val date: LocalDate, val amount: Long, val note: String, val from: Long, val to: Long,
    )

    private fun ImportRow.transactionKey(): TransactionKey? = category?.id?.let {
        TransactionKey(entry.date, entry.amountMinor, category.type, entry.note.orEmpty(), it)
    }

    private fun ImportRow.transferKey(): TransferKey? {
        val from = account.id ?: return null
        val to = destination?.id ?: return null
        return TransferKey(entry.date, entry.amountMinor, entry.note.orEmpty(), from, to)
    }

    private suspend fun removeDuplicates(rows: List<ImportRow>): List<ImportRow> {
        // Keep repeated purchases within the CSV. Compare only with records already in Room.
        val transactionKeys = rows.mapNotNull { it.transactionKey() }.toMutableSet()
        val transferKeys = rows.mapNotNull { it.transferKey() }.toMutableSet()
        val reports = db.transactionReportDao()
        for (dates in transactionKeys.map { it.date }.distinct().chunked(900)) {
            for (item in reports.transactionsOnDates(dates)) {
                transactionKeys.remove(TransactionKey(item.date, item.amountMinor, item.type, item.note.orEmpty(), item.categoryId))
            }
        }
        for (dates in transferKeys.map { it.date }.distinct().chunked(900)) {
            for (item in reports.transfersOnDates(dates)) {
                transferKeys.remove(TransferKey(item.date, item.amountMinor, item.note.orEmpty(), item.fromAccountId, item.toAccountId))
            }
        }
        return rows.filter { row ->
            val transaction = row.transactionKey()
            val transfer = row.transferKey()
            (transaction == null || transaction in transactionKeys) && (transfer == null || transfer in transferKeys)
        }
    }

    private suspend fun createAccounts(plan: ImportPlan): Map<String, Long> {
        if (plan.preview.newAccounts.isEmpty()) return emptyMap()
        val order = (plan.accounts.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val created = plan.preview.newAccounts.mapIndexed { index, name ->
            AccountEntity(name = name, icon = "wallet", sortOrder = order + index)
        }
        return db.accountDao().insertAllReturningIds(created).zip(plan.preview.newAccounts)
            .associate { (id, name) -> name to id }
    }

    private suspend fun createCategories(plan: ImportPlan): Map<CsvNewCategory, Long> {
        val nextOrder = plan.categories.groupBy { it.type }
            .mapValues { (_, categories) -> categories.maxOf { it.sortOrder } + 1 }.toMutableMap()
        val created = plan.preview.newCategories.map { category ->
            val order = nextOrder[category.type] ?: 0
            nextOrder[category.type] = order + 1
            CategoryEntity(type = category.type, name = category.name, icon = "dots", sortOrder = order)
        }
        return db.categoryDao().insertAllReturningIds(created).zip(plan.preview.newCategories)
            .associate { (id, category) -> category to id }
    }
}
