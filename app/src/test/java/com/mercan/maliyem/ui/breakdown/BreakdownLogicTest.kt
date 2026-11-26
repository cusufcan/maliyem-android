package com.mercan.maliyem.ui.breakdown

import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class BreakdownLogicTest {

    private val market = CategoryEntity(1, TransactionType.EXPENSE, "groceries", null, "cart", 0)
    private val rent = CategoryEntity(2, TransactionType.EXPENSE, "rent", null, "home", 1)
    private val salary = CategoryEntity(3, TransactionType.INCOME, "salary", null, "briefcase", 0)

    private fun tx(id: Long, amount: Long, c: CategoryEntity) =
        TransactionWithCategory(TransactionEntity(id, c.type, amount, c.id, LocalDate.of(2026, 11, 1), null, id), c)

    private val rows = listOf(tx(1, 10_000, market), tx(2, 30_000, rent), tx(3, 20_000, market), tx(4, 99_000, salary))

    @Test fun sumsPerCategoryLargestFirst() {
        val slices = buildSlices(rows, TransactionType.EXPENSE)
        // Eşit tutarda kimlik sırası belirler.
        assertEquals(listOf(1L, 2L), slices.map { it.categoryId })
        assertEquals(listOf(30_000.toBigInteger(), 30_000.toBigInteger()), slices.map { it.amountMinor })
    }

    @Test fun percentAndBarScaleToLargest() {
        val state = BreakdownUiState(YearMonth.of(2026, 11), TransactionType.EXPENSE, buildSlices(rows.drop(1), TransactionType.EXPENSE))
        assertEquals(50_000.toBigInteger(), state.totalMinor)
        assertEquals(listOf(60, 40), state.slices.map(state::percent))
        assertEquals(listOf(1f, 20f / 30f), state.slices.map(state::barFraction))
    }

    @Test fun incomeOnlyIncludesIncome() {
        assertEquals(listOf(3L), buildSlices(rows, TransactionType.INCOME).map { it.categoryId })
    }

    @Test fun aggregatesAndRanksCategoriesWithoutLongOverflow() {
        val max = Long.MAX_VALUE
        val items = listOf(tx(1, max, market), tx(2, max, market), tx(3, max, rent))
        val state = BreakdownUiState(YearMonth.of(2026, 11), TransactionType.EXPENSE, buildSlices(items, TransactionType.EXPENSE))
        assertEquals(max.toBigInteger() * 3.toBigInteger(), state.totalMinor)
        assertEquals(listOf(market.id, rent.id), state.slices.map { it.categoryId })
        assertEquals(listOf(67, 33), state.slices.map(state::percent))
        assertEquals(listOf(1f, .5f), state.slices.map(state::barFraction))
    }
}
