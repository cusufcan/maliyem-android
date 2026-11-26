package com.mercan.maliyem.ui.summary

import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryLogicTest {
    private val november = YearMonth.of(2026, 11)
    private val category = CategoryEntity(id = 1, type = EXPENSE, key = "groceries", icon = "cart", sortOrder = 0)

    private fun row(id: Long, type: TransactionType, minor: Long, day: Int, createdAt: Long = id) =
        TransactionWithCategory(
            TransactionEntity(id, type, minor, 1, LocalDate.of(2026, 11, day), "not $id", createdAt),
            category,
        )

    @Test fun `totals and net`() {
        val s = buildSummary(listOf(row(1, INCOME, 2_850_000, 1), row(2, EXPENSE, 1_100_000, 1), row(3, EXPENSE, 116_420, 3)), november)
        assertEquals(2_850_000.toBigInteger(), s.incomeMinor)
        assertEquals(1_216_420.toBigInteger(), s.expenseMinor)
        assertEquals(1_633_580.toBigInteger(), s.netMinor)
        assertEquals(43.toBigInteger(), s.spentPercent)
    }

    @Test fun `negative net and over hundred percent`() {
        val s = buildSummary(listOf(row(1, INCOME, 10_000, 1), row(2, EXPENSE, 25_000, 2)), november)
        assertEquals((-15_000).toBigInteger(), s.netMinor)
        assertEquals(250.toBigInteger(), s.spentPercent)
    }

    @Test fun `one minor unit over income still counts as overspent`() {
        val summary = buildSummary(listOf(row(1, INCOME, 100_000, 1), row(2, EXPENSE, 100_001, 1)), november)
        assertEquals(100.toBigInteger(), summary.spentPercent)
        assertTrue(summary.overspent)
    }

    @Test fun `no income hides spent percent`() {
        assertNull(buildSummary(listOf(row(1, EXPENSE, 5_000, 1)), november).spentPercent)
    }

    @Test fun `percent rounds half up`() {
        // 2/3 = %66,67 → 67; 1/8 = %12,5 → 13
        assertEquals(67.toBigInteger(), buildSummary(listOf(row(1, INCOME, 300, 1), row(2, EXPENSE, 200, 1)), november).spentPercent)
        assertEquals(13.toBigInteger(), buildSummary(listOf(row(1, INCOME, 800, 1), row(2, EXPENSE, 100, 1)), november).spentPercent)
    }

    @Test fun `empty month`() {
        val s = buildSummary(emptyList(), november)
        assertTrue(s.isEmpty)
        assertEquals(0.toBigInteger(), s.netMinor)
        assertNull(s.spentPercent)
    }

    @Test fun `recent keeps newest four by date then creation`() {
        val rows = listOf(
            row(1, EXPENSE, 100, 3),
            row(2, EXPENSE, 100, 26, createdAt = 10),
            row(3, EXPENSE, 100, 26, createdAt = 20),
            row(4, INCOME, 100, 25),
            row(5, EXPENSE, 100, 1),
            row(6, EXPENSE, 100, 10),
        )
        assertEquals(listOf(3L, 2L, 4L, 6L), buildSummary(rows, november).recent.map { it.id })
    }

    @Test fun `row carries category for display`() {
        val r = buildSummary(listOf(row(1, EXPENSE, 100, 1)), november).recent.single()
        assertEquals("groceries", r.categoryKey)
        assertEquals("cart", r.icon)
        assertEquals("not 1", r.note)
    }

    private val nov = YearMonth.of(2026, 11)

    @Test fun `navigation skips empty months`() {
        val withData = setOf(YearMonth.of(2026, 6), YearMonth.of(2026, 9))
        assertEquals(YearMonth.of(2026, 6) to nov, adjacentMonths(YearMonth.of(2026, 9), withData, nov))
        assertEquals(YearMonth.of(2026, 9) to null, adjacentMonths(nov, withData, nov))
        assertEquals(null to YearMonth.of(2026, 9), adjacentMonths(YearMonth.of(2026, 6), withData, nov))
    }

    @Test fun `no data keeps only current month`() {
        assertEquals(null to null, adjacentMonths(nov, emptySet(), nov))
    }

    @Test fun `future month with data is reachable`() {
        val jan = YearMonth.of(2027, 1)
        assertEquals(null to jan, adjacentMonths(nov, setOf(jan), nov))
        assertEquals(nov to null, adjacentMonths(jan, setOf(jan), nov))
    }

    @Test fun `totals and percentage stay exact above long and int limits`() {
        val max = Long.MAX_VALUE
        val summary = buildSummary(listOf(row(1, INCOME, 1, 1), row(2, EXPENSE, max, 1), row(3, EXPENSE, max, 1)), november)
        val total = max.toBigInteger() * 2.toBigInteger()
        assertEquals(total, summary.expenseMinor)
        assertEquals(1.toBigInteger() - total, summary.netMinor)
        assertEquals(total * 100.toBigInteger(), summary.spentPercent)
    }

    @Test fun `same date and creation timestamp use newest id first`() {
        val recent = buildSummary(listOf(row(1, EXPENSE, 100, 1, 10), row(2, EXPENSE, 100, 1, 10)), november).recent
        assertEquals(listOf(2L, 1L), recent.map { it.id })
    }

    @Test fun `current empty month remains reachable across the year boundary`() {
        val december = YearMonth.of(2026, 12)
        val january = december.plusMonths(1)
        assertEquals(null to null, adjacentMonths(december, setOf(december), december))
        assertEquals(null to january, adjacentMonths(december, setOf(december), january))
        assertEquals(december to null, adjacentMonths(january, setOf(december), january))
    }
}
