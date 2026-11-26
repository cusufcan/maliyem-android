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
        assertEquals(2_850_000, s.incomeMinor)
        assertEquals(1_216_420, s.expenseMinor)
        assertEquals(1_633_580, s.netMinor)
        assertEquals(43, s.spentPercent)
    }

    @Test fun `negative net and over hundred percent`() {
        val s = buildSummary(listOf(row(1, INCOME, 10_000, 1), row(2, EXPENSE, 25_000, 2)), november)
        assertEquals(-15_000, s.netMinor)
        assertEquals(250, s.spentPercent)
    }

    @Test fun `no income hides spent percent`() {
        assertNull(buildSummary(listOf(row(1, EXPENSE, 5_000, 1)), november).spentPercent)
    }

    @Test fun `percent rounds half up`() {
        // 2/3 = %66,67 → 67; 1/8 = %12,5 → 13
        assertEquals(67, buildSummary(listOf(row(1, INCOME, 300, 1), row(2, EXPENSE, 200, 1)), november).spentPercent)
        assertEquals(13, buildSummary(listOf(row(1, INCOME, 800, 1), row(2, EXPENSE, 100, 1)), november).spentPercent)
    }

    @Test fun `empty month`() {
        val s = buildSummary(emptyList(), november)
        assertTrue(s.isEmpty)
        assertEquals(0, s.netMinor)
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
}
