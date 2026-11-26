package com.mercan.maliyem.ui.budgets

import com.mercan.maliyem.data.local.BudgetEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.math.BigInteger
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetLogicTest {
    private val month = YearMonth.of(2026, 11)
    private val market = category(1)
    private val rent = category(2)
    private val other = category(3)
    private val income = category(4, INCOME)
    private val categories = listOf(market, rent, other, income)

    @Test fun `counts only selected month expenses and includes uncapped expenses in overall spending`() {
        val rows = listOf(
            row(market, 800, month),
            row(market, 130, month, lastDay = true),
            row(other, 400, month),
            row(income, 5_000, month),
            row(market, 8_000, month.minusMonths(1), lastDay = true),
            row(market, 9_000, month.plusMonths(1)),
        )
        val result = buildMonthlyBudgets(month, rows, listOf(limit(market, 1_000), limit(rent, 2_000)), 5_000, categories)
        assertEquals(BudgetProgress(1_330.toBigInteger(), 5_000), result.overall)
        assertEquals(
            listOf(
                CategoryBudgetProgress(market, BudgetProgress(930.toBigInteger(), 1_000)),
                CategoryBudgetProgress(rent, BudgetProgress(BigInteger.ZERO, 2_000)),
            ),
            result.categories,
        )
    }

    @Test fun `uses exact 80 and 100 percent thresholds and caps the bar`() {
        val statuses = listOf(0, 799, 800, 999, 1_000, 1_001).map { BudgetProgress(it.toBigInteger(), 1_000) }
        val (normal, below, warning, near, full) = statuses
        val over = statuses.last()
        assertEquals(BudgetStatus.NORMAL, normal.status)
        assertEquals(BudgetStatus.NORMAL, below.status)
        assertEquals(79.toBigInteger(), below.percent)
        assertEquals(BudgetStatus.WARNING, warning.status)
        assertEquals(80.toBigInteger(), warning.percent)
        assertEquals(BudgetStatus.WARNING, near.status)
        assertEquals(BudgetStatus.WARNING, full.status)
        assertEquals(BigInteger.ZERO, full.remainingMinor)
        assertEquals(BudgetStatus.OVER, over.status)
        assertEquals((-1).toBigInteger(), over.remainingMinor)
        assertEquals(1f, over.fraction, 0f)
    }

    @Test fun `sorts by exact usage rather than spent amount or displayed percent`() {
        val rows = listOf(row(market, 9_310, month), row(rent, 932, month), row(other, 931, month))
        val limits = listOf(limit(other, 1_000), limit(market, 10_000), limit(rent, 1_000))
        val result = buildMonthlyBudgets(month, rows, limits, null, categories)
        assertEquals(listOf(rent, market, other), result.categories.map { it.category })
        assertTrue(result.categories.all { it.progress.percent == 93.toBigInteger() })
    }

    @Test fun `keeps totals larger than Long and handles an empty budget configuration`() {
        val rows = listOf(row(market, Long.MAX_VALUE, month), row(market, Long.MAX_VALUE, month))
        val result = buildMonthlyBudgets(month, rows, listOf(limit(market, Long.MAX_VALUE)), null, categories)
        assertEquals(200.toBigInteger(), result.categories.single().progress.percent)
        assertTrue(buildMonthlyBudgets(month, rows, emptyList(), null, categories).isEmpty)
    }

    private fun category(id: Long, type: TransactionType = EXPENSE) =
        CategoryEntity(id, type, icon = "cart", sortOrder = 0)

    private fun limit(category: CategoryEntity, limitMinor: Long) =
        BudgetEntity(categoryId = category.id, limitMinor = limitMinor, createdAt = 0)

    private fun row(category: CategoryEntity, amount: Long, rowMonth: YearMonth, lastDay: Boolean = false) =
        TransactionWithCategory(
            TransactionEntity(
                type = category.type, amountMinor = amount, categoryId = category.id,
                date = if (lastDay) rowMonth.atEndOfMonth() else rowMonth.atDay(1), createdAt = 0, accountId = 1,
            ),
            category,
        )
}
