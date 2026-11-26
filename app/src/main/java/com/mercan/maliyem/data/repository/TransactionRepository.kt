package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TrendEntry
import com.mercan.maliyem.data.receipt.ReceiptStore
import com.mercan.maliyem.notifications.BudgetAlertTracker
import com.mercan.maliyem.notifications.ExpenseWrite
import com.mercan.maliyem.widget.WidgetUpdater
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Kayıtlara ekranların tek kapısı; testte sahtesi verilebilsin diye arayüz. */
interface TransactionRepository {
    /** [month] içindeki kayıtlar, en yeni başta. */
    fun observeMonth(month: YearMonth): Flow<List<TransactionWithCategory>>

    /** İlk ve son ay dahil, eğilim hesabında kullanılan gelir ve gider kayıtları. */
    fun observeTrendEntries(first: YearMonth, last: YearMonth): Flow<List<TrendEntry>>

    /** Tarih, tür, kategori ve hesap süzgeçlerini veritabanında uygular. */
    fun observeFiltered(
        from: LocalDate,
        to: LocalDate,
        type: TransactionType?,
        categoryIds: Set<Long>,
        accountIds: Set<Long>,
    ): Flow<List<TransactionWithCategory>>

    /** En az bir kaydı olan aylar. */
    fun observeMonthsWithData(): Flow<Set<YearMonth>>

    /** Kayıt tarihi; silinince null. */
    fun observeDate(id: Long): Flow<LocalDate?>

    /** Kategori kimliği → kayıt sayısı. */
    fun observeCountsByCategory(): Flow<Map<Long, Int>>

    suspend fun get(id: Long): TransactionEntity?

    /** Yeni kaydı ekler, kimliğini döndürür. */
    suspend fun add(transaction: TransactionEntity): Long

    suspend fun update(transaction: TransactionEntity): Boolean

    /** Kayıt yoksa bir şey yapmaz. */
    suspend fun delete(id: Long)

    suspend fun deleteAndGet(id: Long): TransactionEntity?

    fun discardUndo(transaction: TransactionEntity)

    suspend fun restore(transaction: TransactionEntity): Boolean
}

class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val budgetAlerts: BudgetAlertTracker,
    private val receipts: ReceiptStore,
    private val widgetUpdater: WidgetUpdater,
) : TransactionRepository {
    override fun observeMonth(month: YearMonth) = dao.observeRange(month.atDay(1), month.atEndOfMonth()).distinctUntilChanged()

    override fun observeTrendEntries(first: YearMonth, last: YearMonth) =
        dao.observeTrendEntries(first.atDay(1), last.atEndOfMonth()).distinctUntilChanged()

    override fun observeFiltered(
        from: LocalDate,
        to: LocalDate,
        type: TransactionType?,
        categoryIds: Set<Long>,
        accountIds: Set<Long>,
    ) = dao.observeFiltered(from, to, type, categoryIds.toList(), accountIds.toList(), categoryIds.isEmpty(), accountIds.isEmpty())
        .distinctUntilChanged()

    override fun observeMonthsWithData() = dao.observeDates().map { dates -> dates.mapTo(HashSet(), YearMonth::from) }.distinctUntilChanged()

    override fun observeDate(id: Long) = dao.observeDate(id).distinctUntilChanged()

    override fun observeCountsByCategory() = dao.observeCounts().map { list -> list.associate { it.categoryId to it.count } }

    override suspend fun get(id: Long) = dao.getById(id)

    override suspend fun add(transaction: TransactionEntity) = receipts.save(transaction.receiptPath) { path ->
        budgetAlerts.track {
            val entry = transaction.copy(receiptPath = path)
            ExpenseWrite(dao.insert(entry), listOf(entry))
        }
    }.also { widgetUpdater.refresh() }

    override suspend fun update(transaction: TransactionEntity) = receipts.save(transaction.receiptPath) { path ->
        var previousPath: String? = null
        try {
            budgetAlerts.track {
                val current = dao.getById(transaction.id) ?: return@track ExpenseWrite(false)
                previousPath = current.receiptPath
                // Düzenleme, kural silinmişse bağlantıyı geri getirmemeli.
                val entry = transaction.copy(recurringRuleId = current.recurringRuleId, receiptPath = path)
                val updated = dao.update(entry) > 0
                if (updated) ExpenseWrite(true, listOf(entry), current) else ExpenseWrite(false)
            }
        } finally {
            if (previousPath != path) receipts.cleanIfUnused(previousPath)
        }
    }.also { if (it) widgetUpdater.refresh() }

    override suspend fun delete(id: Long) {
        receipts.locked {
            withContext(NonCancellable) {
                val deleted = dao.deleteAndGet(id)
                receipts.cleanIfUnused(deleted?.receiptPath)
            }
        }
        widgetUpdater.refresh()
    }

    override suspend fun deleteAndGet(id: Long) = receipts.locked {
        dao.deleteAndGet(id)?.also { receipts.retainForUndo(it.receiptPath) }
    }.also { if (it != null) widgetUpdater.refresh() }

    override fun discardUndo(transaction: TransactionEntity) { receipts.discardUndo(transaction) }

    override suspend fun restore(transaction: TransactionEntity) = receipts.locked {
        budgetAlerts.track {
            val entry = transaction.copy(receiptPath = receipts.existingPath(transaction.receiptPath))
            val restored = dao.restore(entry, fallbackKey(entry.type))
            ExpenseWrite(restored != null, listOfNotNull(restored))
        }
    }.also { if (it) widgetUpdater.refresh() }
}
