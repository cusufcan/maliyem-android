package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TrendEntry
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
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

    suspend fun restore(transaction: TransactionEntity): Boolean
}

class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
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

    override suspend fun add(transaction: TransactionEntity) = dao.insert(transaction)

    override suspend fun update(transaction: TransactionEntity): Boolean = dao.updateKeepingRule(transaction) > 0

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteAndGet(id: Long) = dao.deleteAndGet(id)

    override suspend fun restore(transaction: TransactionEntity) = dao.restore(transaction, fallbackKey(transaction.type))
}
