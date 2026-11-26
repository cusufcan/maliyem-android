package com.mercan.maliyem.ui.transactions

import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TransferEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionsLogicTest {

    private val market = CategoryEntity(1, TransactionType.EXPENSE, "groceries", null, "cart", 0)
    private val salary = CategoryEntity(2, TransactionType.INCOME, "salary", null, "briefcase", 0)

    private fun tx(id: Long, day: Int, amount: Long, category: CategoryEntity, createdAt: Long = id) = TransactionWithCategory(
        TransactionEntity(id, category.type, amount, category.id, LocalDate.of(2026, 11, day), null, createdAt, accountId = 1),
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
        assertEquals(95_000.toBigInteger(), days[0].netMinor)
        assertEquals(listOf(3L, 2L), days[0].rows.map { it.id })
        assertEquals((-10_000).toBigInteger(), days[1].netMinor)
    }

    @Test fun filterKeepsOnlyMatchingType() {
        val days = groupByDay(rows, TransactionFilter.EXPENSE)
        assertEquals(listOf(listOf(2L), listOf(1L)), days.map { d -> d.rows.map { it.id } })
        assertEquals((-5_000).toBigInteger(), days[0].netMinor)
        assertEquals(1, groupByDay(rows, TransactionFilter.INCOME).size)
    }

    @Test fun sameDayTotalsCanExceedLongAndTiesUseId() {
        val max = Long.MAX_VALUE
        val items = listOf(tx(1, 26, max, market, 1), tx(2, 26, max, market, 1))
        val day = groupByDay(items, TransactionFilter.ALL).single()
        assertEquals(max.toBigInteger() * (-2).toBigInteger(), day.netMinor)
        assertEquals(listOf(2L, 1L), day.rows.map { it.id })
    }

    @Test fun transfersAreNeutralAndOnlyAppearInAll() {
        val date = LocalDate.of(2026, 11, 26)
        val transfer = TransferEntity(3, 1, 2, Long.MAX_VALUE, date, createdAt = 3)
        val transferOnly = transfer.copy(id = 4, date = date.plusDays(1))
        val transfers = listOf(transfer, transferOnly)

        val days = groupByDay(rows, TransactionFilter.ALL, transfers)
        assertEquals(0.toBigInteger(), days.first().netMinor)
        assertEquals(95_000.toBigInteger(), days[1].netMinor)
        assertEquals(DayRow.Transfer(transfer), days[1].rows.first())
        assertEquals(groupByDay(rows, TransactionFilter.EXPENSE), groupByDay(rows, TransactionFilter.EXPENSE, transfers))
        assertEquals(groupByDay(rows, TransactionFilter.INCOME), groupByDay(rows, TransactionFilter.INCOME, transfers))
    }
}
