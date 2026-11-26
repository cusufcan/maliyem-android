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

    /** Yeni kaydı ekler, kimliğini döndürür. */
    suspend fun add(transaction: TransactionEntity): Long

    /** "Geri al" için; kayıt yoksa bir şey yapmaz. */
    suspend fun delete(id: Long)
}

class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {
    override fun observeMonth(month: YearMonth) = dao.observeRange(month.atDay(1), month.atEndOfMonth())

    override fun observeMonthsWithData() = dao.observeDates().map { dates -> dates.mapTo(HashSet(), YearMonth::from) }

    override suspend fun add(transaction: TransactionEntity) = dao.insert(transaction)

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
