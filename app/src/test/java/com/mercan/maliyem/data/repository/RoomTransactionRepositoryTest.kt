package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomTransactionRepositoryTest {
    private class RangeDao : TransactionDao {
        var range: Pair<LocalDate, LocalDate>? = null
        override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TransactionWithCategory>> {
            range = from to to
            return emptyFlow()
        }
        override suspend fun insert(transaction: TransactionEntity) = 0L
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

    @Test fun `december rolls into january`() {
        assertEquals(YearMonth.of(2027, 1), YearMonth.of(2026, 12).plusMonths(1))
    }
}
