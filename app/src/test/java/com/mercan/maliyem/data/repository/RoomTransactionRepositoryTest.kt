package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomTransactionRepositoryTest {
    private class RangeDao : TransactionDao {
        var range: Pair<LocalDate, LocalDate>? = null
        override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TransactionWithCategory>> {
            range = from to to
            return emptyFlow()
        }
        var dates: List<LocalDate> = emptyList()
        override fun observeDates() = flowOf(dates)
        var inserted: TransactionEntity? = null
        var deleted: Long? = null
        override suspend fun insert(transaction: TransactionEntity): Long { inserted = transaction; return 7L }
        override suspend fun deleteById(id: Long) { deleted = id }
        override suspend fun update(transaction: TransactionEntity) = Unit
        override suspend fun delete(transaction: TransactionEntity) = Unit
    }

    private fun rangeOf(month: YearMonth) = RangeDao().also { RoomTransactionRepository(it).observeMonth(month) }.range

    @Test fun `month bounds include first and last day`() {
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30), rangeOf(YearMonth.of(2026, 11)))
        assertEquals(LocalDate.of(2026, 12, 1) to LocalDate.of(2026, 12, 31), rangeOf(YearMonth.of(2026, 12)))
    }

    @Test fun `february follows leap years`() {
        assertEquals(LocalDate.of(2027, 2, 28), rangeOf(YearMonth.of(2027, 2))!!.second)
        assertEquals(LocalDate.of(2028, 2, 29), rangeOf(YearMonth.of(2028, 2))!!.second)
    }

    @Test fun `dates collapse into distinct months`() = runBlocking {
        val dao = RangeDao().apply {
            dates = listOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 28), LocalDate.of(2026, 9, 1))
        }
        assertEquals(setOf(YearMonth.of(2026, 11), YearMonth.of(2026, 9)), RoomTransactionRepository(dao).observeMonthsWithData().first())
    }

    @Test fun `add and delete reach the dao`() = runBlocking {
        val dao = RangeDao()
        val repo = RoomTransactionRepository(dao)
        val entity = TransactionEntity(type = TransactionType.EXPENSE, amountMinor = 100, categoryId = 1, date = LocalDate.of(2026, 11, 26), createdAt = 0)
        assertEquals(7L, repo.add(entity))
        assertEquals(entity, dao.inserted)
        repo.delete(7)
        assertEquals(7L, dao.deleted)
    }

    @Test fun `december rolls into january`() {
        assertEquals(YearMonth.of(2027, 1), YearMonth.of(2026, 12).plusMonths(1))
    }
}
