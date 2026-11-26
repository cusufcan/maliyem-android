package com.mercan.maliyem.data.repository

import android.database.sqlite.SQLiteException
import android.util.Log
import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.data.local.RecurringRuleDao
import com.mercan.maliyem.data.local.RecurringRuleEntity
import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.receipt.ReceiptStore
import com.mercan.maliyem.notifications.BudgetAlertTracker
import com.mercan.maliyem.notifications.ExpenseWrite
import com.mercan.maliyem.widget.WidgetUpdater
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RecurringRepository @Inject constructor(
    private val rules: RecurringRuleDao,
    private val transactions: TransactionDao,
    private val clock: Clock,
    private val budgetAlerts: BudgetAlertTracker,
    private val receipts: ReceiptStore,
    private val widgetUpdater: WidgetUpdater,
) {
    private val generation = Mutex()

    fun observeAll() = rules.observeAll()
    fun observe(id: Long) = rules.observe(id)
    fun observeCount() = rules.observeCount()

    suspend fun countDueOn(date: LocalDate) = rules.countDueOn(date)

    /** Kural ve ilk kayıtlar birlikte yazılır; gelecek tarihli kural sırada bekler. */
    suspend fun add(transaction: TransactionEntity, frequency: RecurringFrequency): Long {
        val today = LocalDate.now(clock)
        val (id, count) = receipts.save(transaction.receiptPath) { path ->
            budgetAlerts.track {
                val rule = RecurringRuleEntity(
                    type = transaction.type, amountMinor = transaction.amountMinor,
                    categoryId = transaction.categoryId, accountId = transaction.accountId, note = transaction.note,
                    frequency = frequency, startDate = transaction.date, nextDate = transaction.date,
                    createdAt = transaction.createdAt,
                )
                val id = rules.insert(rule)
                val generated = generate(rule.copy(id = id), today, path)
                ExpenseWrite(id to generated.size, generated)
            }
        }
        widgetUpdater.refresh()
        if (count == MAX_OCCURRENCES) completeBatches(id, today)
        return id
    }

    /** Güncel imleç korunur; sıklık değişirse yeni takvimde aynı gün veya sonrasına hizalanır. */
    suspend fun edit(
        id: Long,
        amountMinor: Long,
        categoryId: Long,
        accountId: Long,
        note: String?,
        frequency: RecurringFrequency,
        endDate: LocalDate?,
    ): Boolean {
        val today = LocalDate.now(clock)
        val count = budgetAlerts.track {
            val current = rules.getById(id) ?: return@track ExpenseWrite(null)
            require(endDate == null || endDate >= current.startDate)
            val edited = current.copy(
                amountMinor = amountMinor, categoryId = categoryId, accountId = accountId,
                note = note, frequency = frequency, endDate = endDate,
            )
            val nextDate = if (frequency == current.frequency) current.nextDate
                else occurrenceOnOrAfter(current.startDate, frequency, current.nextDate)
            val updated = edited.copy(nextDate = nextDate)
            rules.update(updated)
            val generated = generate(updated, today)
            ExpenseWrite(generated.size, generated)
        } ?: return false
        widgetUpdater.refresh()
        if (count == MAX_OCCURRENCES) completeBatches(id, today)
        return true
    }

    suspend fun setPaused(id: Long, paused: Boolean): Boolean {
        val today = LocalDate.now(clock)
        val count = budgetAlerts.track {
            val current = rules.getById(id) ?: return@track ExpenseWrite(null)
            // Devam ettirirken duraklatılan dönem geri doldurulmaz; bugünden itibaren sürer.
            val nextDate = if (paused || !current.paused) current.nextDate
                else maxOf(current.nextDate, occurrenceOnOrAfter(current.startDate, current.frequency, today))
            val updated = current.copy(paused = paused, nextDate = nextDate)
            rules.update(updated)
            val generated = if (paused) emptyList() else generate(updated, today)
            ExpenseWrite(generated.size, generated)
        } ?: return false
        if (!paused) widgetUpdater.refresh()
        if (count == MAX_OCCURRENCES) completeBatches(id, today)
        return true
    }

    suspend fun delete(id: Long) = rules.delete(id) > 0

    /** Her kural yazma işlemi içinde yeniden okunur; eşzamanlı çağrılar aynı tarihleri üretemez. */
    suspend fun generateDue(today: LocalDate = LocalDate.now(clock)): Int = generation.withLock {
        val ids = try {
            rules.dueIds(today)
        } catch (e: SQLiteException) {
            Log.w("RecurringRepository", "Tekrarlayan kurallar okunamadı", e)
            return@withLock 0
        }
        ids.sumOf { completeBatches(it, today) }
    }

    private suspend fun completeBatches(id: Long, today: LocalDate): Int {
        var total = 0
        try {
            do {
                val count = budgetAlerts.track {
                    val generated = rules.getById(id)?.let { generate(it, today) }.orEmpty()
                    ExpenseWrite(generated.size, generated)
                }
                total += count
                if (count > 0) widgetUpdater.refresh()
            } while (count == MAX_OCCURRENCES)
        } catch (e: SQLiteException) {
            Log.w("RecurringRepository", "Tekrarlayan kayıtlar yazılamadı", e)
        }
        return total
    }

    /** Çağıran, kayıtların eklenmesini ve imlecin ilerlemesini aynı Room işleminde tutar. */
    private suspend fun generate(
        rule: RecurringRuleEntity,
        today: LocalDate,
        receiptPath: String? = null,
    ): List<TransactionEntity> {
        val dates = occurrences(rule, today)
        if (dates.isEmpty()) return emptyList()
        val createdAt = clock.millis()
        val generated = dates.map { date ->
            TransactionEntity(
                type = rule.type, amountMinor = rule.amountMinor, categoryId = rule.categoryId,
                date = date, note = rule.note, createdAt = createdAt, accountId = rule.accountId,
                recurringRuleId = rule.id, receiptPath = receiptPath.takeIf { date == rule.startDate },
            )
        }
        transactions.insertAll(generated)
        rules.advance(rule.id, nextOccurrence(rule, dates.last()))
        return generated
    }
}
