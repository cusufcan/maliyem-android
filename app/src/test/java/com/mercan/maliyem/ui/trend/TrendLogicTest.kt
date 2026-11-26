package com.mercan.maliyem.ui.trend

import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import com.mercan.maliyem.data.local.TrendEntry
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendLogicTest {
    private val month = YearMonth.of(2028, 2)
    private val months = trendMonths(month)

    @Test fun `range ends at the selected month and crosses years`() {
        assertEquals((5L downTo 0L).map(month::minusMonths), months)
        assertEquals(YearMonth.of(2027, 9), months.first())
    }

    @Test fun `totals use calendar month and type without overflowing long`() {
        val rows = listOf(
            TrendEntry(month.atDay(1), INCOME, 1, Long.MAX_VALUE),
            TrendEntry(month.atEndOfMonth(), INCOME, 2, 45),
            TrendEntry(month.atEndOfMonth(), EXPENSE, 3, 20),
            TrendEntry(month.minusMonths(1).atEndOfMonth(), EXPENSE, 3, 100),
            TrendEntry(months.first().atDay(1).minusDays(1), INCOME, 1, 9),
            TrendEntry(month.atEndOfMonth().plusDays(1), EXPENSE, 3, 7),
        )
        val result = buildTrend(months, rows)
        val selected = result.last()
        assertEquals(Long.MAX_VALUE.toBigInteger() + 45.toBigInteger(), selected.incomeMinor)
        assertEquals(20.toBigInteger(), selected.expenseMinor)
        assertEquals(Long.MAX_VALUE.toBigInteger() + 25.toBigInteger(), selected.netMinor)
        assertEquals(100.toBigInteger(), result[4].expenseMinor)
        assertEquals(BigInteger.ZERO, result.first().incomeMinor)
    }

    @Test fun `empty months keep their places and both totals are zero`() {
        val result = buildTrend(months, listOf(TrendEntry(months.first().atDay(1), EXPENSE, 1, 5)))
        assertEquals(months, result.map { it.month })
        assertEquals(months.drop(1).map { MonthlyTrend(it, BigInteger.ZERO, BigInteger.ZERO) }, result.drop(1))
    }

    @Test fun `the earliest restorable month has no earlier months`() {
        val earliest = YearMonth.from(LocalDate.MIN)
        assertNull(previousTrendMonth(earliest))
        assertEquals(listOf(earliest), trendMonths(earliest))
    }

    @Test fun `zero previous amount has no percentage even when current is zero`() {
        assertNull(percentChange(BigInteger.ONE, BigInteger.ZERO))
        assertNull(percentChange(BigInteger.ZERO, BigInteger.ZERO))
    }

    @Test fun `percentage keeps its sign and rounds halves away from zero`() {
        val previous = 200.toBigInteger()
        assertEquals(13.toBigInteger(), percentChange(225.toBigInteger(), previous))
        assertEquals((-13).toBigInteger(), percentChange(175.toBigInteger(), previous))
        assertEquals(BigInteger.ZERO, percentChange(previous, previous))
        assertEquals((-100).toBigInteger(), percentChange(BigInteger.ZERO, previous))
    }
}
