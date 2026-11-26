package com.mercan.maliyem.data.repository

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.withTransaction
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.data.local.RecurringRuleDao
import com.mercan.maliyem.data.local.RecurringRuleEntity
import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.widget.WidgetUpdater
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class RecurringRepository @Inject constructor(
    private val db: MaliyemDatabase,
    private val rules: RecurringRuleDao,
    private val transactions: TransactionDao,
    private val clock: Clock,
    private val widgetUpdater: WidgetUpdater,
) {
    fun observeAll() = rules.observeAll()
    fun observe(id: Long) = rules.observe(id)
    fun observeCount() = rules.observeCount()

    /** Kural ve ilk kayıtlar birlikte yazılır; gelecek tarihli kural sırada bekler. */
    suspend fun add(transaction: TransactionEntity, frequency: RecurringFrequency): Long {
        val today = LocalDate.now(clock)
        val (id, generated) = db.withTransaction {
            val rule = RecurringRuleEntity(
                type = transaction.type, amountMinor = transaction.amountMinor,
                categoryId = transaction.categoryId, accountId = transaction.accountId, note = transaction.note,
                frequency = frequency, startDate = transaction.date, nextDate = transaction.date,
                createdAt = transaction.createdAt,
            )
            val id = rules.insert(rule)
            id to generate(rule.copy(id = id), today)
        }
        widgetUpdater.refresh()
        if (generated == MAX_OCCURRENCES) completeBatches(id, today)
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
        val generated = db.withTransaction {
            val current = rules.getById(id) ?: return@withTransaction null
            require(endDate == null || endDate >= current.startDate)
            val edited = current.copy(
                amountMinor = amountMinor, categoryId = categoryId, accountId = accountId,
                note = note, frequency = frequency, endDate = endDate,
            )
            val nextDate = if (frequency == current.frequency) current.nextDate
                else occurrenceOnOrAfter(current.startDate, frequency, current.nextDate)
            val updated = edited.copy(nextDate = nextDate)
            rules.update(updated)
            generate(updated, today)
        } ?: return false
        widgetUpdater.refresh()
        if (generated == MAX_OCCURRENCES) completeBatches(id, today)
        return true
    }

    suspend fun setPaused(id: Long, paused: Boolean): Boolean = db.withTransaction {
        val current = rules.getById(id) ?: return@withTransaction false
        val today = LocalDate.now(clock)
        // Devam ettirirken duraklatılan dönem geri doldurulmaz; bugünden itibaren sürer.
        val nextDate = if (paused || !current.paused) current.nextDate
            else maxOf(current.nextDate, occurrenceOnOrAfter(current.startDate, current.frequency, today))
        val updated = current.copy(paused = paused, nextDate = nextDate)
        rules.update(updated)
        if (!paused) generate(updated, today)
        true
    }.also { if (it && !paused) widgetUpdater.refresh() }

    suspend fun delete(id: Long) = rules.delete(id) > 0

    /** Her kural yazma işlemi içinde yeniden okunur; eşzamanlı çağrılar aynı tarihleri üretemez. */
    suspend fun generateDue() {
        val today = LocalDate.now(clock)
        val ids = try {
            rules.dueIds(today)
        } catch (e: SQLiteException) {
            Log.w("RecurringRepository", "Tekrarlayan kurallar okunamadı", e)
            return
        }
        for (id in ids) completeBatches(id, today)
    }

    private suspend fun completeBatches(id: Long, today: LocalDate) {
        try {
            do {
                val count = db.withTransaction { rules.getById(id)?.let { generate(it, today) } ?: 0 }
                if (count > 0) widgetUpdater.refresh()
            } while (count == MAX_OCCURRENCES)
        } catch (e: SQLiteException) {
            Log.w("RecurringRepository", "Tekrarlayan kayıtlar yazılamadı", e)
        }
    }

    /** Çağıran, kayıtların eklenmesini ve imlecin ilerlemesini aynı Room işleminde tutar. */
    private suspend fun generate(rule: RecurringRuleEntity, today: LocalDate): Int {
        val dates = occurrences(rule, today)
        if (dates.isEmpty()) return 0
        val createdAt = clock.millis()
        transactions.insertAll(dates.map { date ->
            TransactionEntity(
                type = rule.type, amountMinor = rule.amountMinor, categoryId = rule.categoryId,
                date = date, note = rule.note, createdAt = createdAt, accountId = rule.accountId,
                recurringRuleId = rule.id,
            )
        })
        rules.advance(rule.id, nextOccurrence(rule, dates.last()))
        return dates.size
    }
}
