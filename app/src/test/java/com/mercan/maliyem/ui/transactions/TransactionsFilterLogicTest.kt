package com.mercan.maliyem.ui.transactions

import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TransferEntity
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionsFilterLogicTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val category = CategoryEntity(1, TransactionType.EXPENSE, "groceries", null, "cart", 0)
    private val record = TransactionWithCategory(
        TransactionEntity(1, category.type, 12_550, category.id, today, "IŞIK ve çağrı", 1, accountId = 1),
        category,
    )
    private val transfer = TransferEntity(1, 1, 2, 12_550, today, "Günlük ödeme", 1)

    private fun search(query: String, filters: TransactionsFilters = TransactionsFilters()) = TransactionSearch(
        query, filters, mapOf(1L to "Yeme İçme"), mapOf(1L to "Nakit", 2L to "Birikim"),
    )

    @Test fun turkishLettersAndDecomposedAccentsUseTheSameSearchForm() {
        assertEquals("iiii", normalizeTransactionSearch("iİıI"))
        assertEquals("sguoc", normalizeTransactionSearch("ŞĞÜÖÇ"))
        assertEquals("cagri", normalizeTransactionSearch("C\u0327ag\u0306rı"))
        assertTrue(search(" isik VE cagri ").matches(record))
        assertTrue(search("yeme icme").matches(record))
        assertTrue(search("NAKİT").matches(record))
        assertTrue(search("gunluk odeme").matches(transfer))
        assertTrue(search("nakit").matches(transfer))
        assertTrue(search("BIRIKIM").matches(transfer))
        assertFalse(search("yeme icme").matches(transfer))
    }

    @Test fun numericQueryMatchesLiraPrefixWithEitherDecimalSeparator() {
        listOf("125", "125,50", "125.50", "125,5", "125.").forEach {
            assertTrue(it, search(it).matches(record))
            assertTrue(it, search(it).matches(transfer))
        }
        listOf("12,5", "125,05", "2550", "-125", "125.500", "125.,5", "12a").forEach {
            assertFalse(it, search(it).matches(record))
        }
        assertTrue(search("12").matches(record.copy(transaction = record.transaction.copy(amountMinor = 125_099))))
        assertTrue(search("0,05").matches(record.copy(transaction = record.transaction.copy(amountMinor = 5))))
        assertTrue(search("92233720368547758.07").matches(record.copy(transaction = record.transaction.copy(amountMinor = Long.MAX_VALUE))))
    }

    @Test fun amountBoundsAreInclusiveAndAlsoApplyToTransfersAndSearch() {
        assertTrue(search("125", TransactionsFilters(minimum = 12_550, maximum = 12_550)).matches(record))
        assertFalse(search("125", TransactionsFilters(minimum = 12_551)).matches(record))
        assertFalse(search("", TransactionsFilters(maximum = 12_549)).matches(transfer))
        assertTrue(search("birikim", TransactionsFilters(maximum = 12_550)).matches(transfer))
        assertFalse(search("birikim", TransactionsFilters(minimum = 12_551)).matches(transfer))
    }

    @Test fun amountBoundParserSupportsBothSeparatorsAndRejectsInvalidValues() {
        listOf(Locale.US, Locale.forLanguageTag("tr")).forEach { locale ->
            assertEquals(12_550L, parseTransactionAmountBound("125,50", locale))
            assertEquals(12_550L, parseTransactionAmountBound("125.50", locale))
            assertEquals(0L, parseTransactionAmountBound("0", locale))
            assertNull(parseTransactionAmountBound("-1", locale))
            assertNull(parseTransactionAmountBound("1,2.3", locale))
            assertNull(parseTransactionAmountBound("92233720368547758,08", locale))
        }
    }

    @Test fun datePresetsUseSharedMonthOrTodayAsAppropriate() {
        val month = YearMonth.of(2024, 2)
        val start = today.minusDays(12)
        fun dates(range: TransactionsRange) =
            TransactionsFilters(range = range, customStart = start, customEnd = today).dates(month, today)

        assertEquals(month.atDay(1)..LocalDate.of(2024, 2, 29), dates(TransactionsRange.MONTH))
        assertEquals(LocalDate.of(2026, 8, 26)..today, dates(TransactionsRange.LAST_THREE_MONTHS))
        assertEquals(LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31), dates(TransactionsRange.YEAR))
        assertEquals(LocalDate.MIN..LocalDate.MAX, dates(TransactionsRange.ALL_TIME))
        assertEquals(start..today, dates(TransactionsRange.CUSTOM))
    }

    @Test fun transfersAreIncludedOnlyForCompatibleSqlFilters() {
        assertTrue(TransactionsFilters().includesTransfers)
        assertTrue(TransactionsFilters(type = TransactionFilter.TRANSFER, accountIds = setOf(2)).includesTransfers)
        assertFalse(TransactionsFilters(type = TransactionFilter.INCOME).includesTransfers)
        assertFalse(TransactionsFilters(type = TransactionFilter.EXPENSE).includesTransfers)
        assertFalse(TransactionsFilters(categoryIds = setOf(1)).includesTransfers)
    }
}
