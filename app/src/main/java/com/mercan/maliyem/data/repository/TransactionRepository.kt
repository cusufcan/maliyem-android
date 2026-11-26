package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Kayıtlara ekranların tek kapısı; testte sahtesi verilebilsin diye arayüz. */
interface TransactionRepository {
    /** [month] içindeki kayıtlar, en yeni başta. */
    fun observeMonth(month: YearMonth): Flow<List<TransactionWithCategory>>

    /** En az bir kaydı olan aylar. */
    fun observeMonthsWithData(): Flow<Set<YearMonth>>

    /** Tek kayıt; silinince null. */
    fun observe(id: Long): Flow<TransactionWithCategory?>

    /** Kategori kimliği → kayıt sayısı. */
    fun observeCountsByCategory(): Flow<Map<Long, Int>>

    suspend fun get(id: Long): TransactionEntity?

    /** Yeni kaydı ekler, kimliğini döndürür. Kimliği dolu kayıt aynı kimlikle geri gelir (silmeyi geri al). */
    suspend fun add(transaction: TransactionEntity): Long

    suspend fun update(transaction: TransactionEntity)

    /** Kayıt yoksa bir şey yapmaz. */
    suspend fun delete(id: Long)
}

class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {
    override fun observeMonth(month: YearMonth) = dao.observeRange(month.atDay(1), month.atEndOfMonth())

    override fun observeMonthsWithData() = dao.observeDates().map { dates -> dates.mapTo(HashSet(), YearMonth::from) }

    override fun observe(id: Long) = dao.observeById(id)

    override fun observeCountsByCategory() = dao.observeCounts().map { list -> list.associate { it.categoryId to it.count } }

    override suspend fun get(id: Long) = dao.getById(id)

    override suspend fun add(transaction: TransactionEntity) = dao.insert(transaction)

    override suspend fun update(transaction: TransactionEntity) = dao.update(transaction)

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
