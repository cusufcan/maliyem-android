package com.mercan.maliyem.ui.transactions

import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionsLogicTest {

    private val market = CategoryEntity(1, TransactionType.EXPENSE, "groceries", null, "cart", 0)
    private val salary = CategoryEntity(2, TransactionType.INCOME, "salary", null, "briefcase", 0)

    private fun tx(id: Long, day: Int, amount: Long, category: CategoryEntity, createdAt: Long = id) = TransactionWithCategory(
        TransactionEntity(id, category.type, amount, category.id, LocalDate.of(2026, 11, day), null, createdAt),
        category,
    )

    private val rows = listOf(
        tx(1, 24, 10_000, market),
        tx(2, 26, 5_000, market, createdAt = 1),
        tx(3, 26, 100_000, salary, createdAt = 2),
    )

    @Test fun groupsByDayNewestFirstWithNet() {
        val days = groupByDay(rows, TransactionFilter.ALL)
        assertEquals(listOf(26, 24), days.map { it.date.dayOfMonth })
        assertEquals(95_000L, days[0].netMinor)
        assertEquals(listOf(3L, 2L), days[0].rows.map { it.id })
        assertEquals(-10_000L, days[1].netMinor)
    }

    @Test fun filterKeepsOnlyMatchingType() {
        val days = groupByDay(rows, TransactionFilter.EXPENSE)
        assertEquals(listOf(listOf(2L), listOf(1L)), days.map { d -> d.rows.map { it.id } })
        assertEquals(-5_000L, days[0].netMinor)
        assertEquals(1, groupByDay(rows, TransactionFilter.INCOME).size)
    }
}
