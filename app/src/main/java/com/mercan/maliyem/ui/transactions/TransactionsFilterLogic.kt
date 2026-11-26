package com.mercan.maliyem.ui.transactions

import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TransferEntity
import com.mercan.maliyem.util.parseMoney
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

enum class TransactionFilter(val transactionType: TransactionType? = null) {
    ALL, EXPENSE(TransactionType.EXPENSE), INCOME(TransactionType.INCOME), TRANSFER,
}

enum class TransactionsRange { MONTH, LAST_THREE_MONTHS, YEAR, ALL_TIME, CUSTOM }

data class TransactionsFilters(
    val type: TransactionFilter = TransactionFilter.ALL,
    val categoryIds: Set<Long> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
    val range: TransactionsRange = TransactionsRange.MONTH,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val minimum: Long? = null,
    val maximum: Long? = null,
) {
    val isActive: Boolean get() = this != TransactionsFilters()

    /** Aralık uçları dahil. Ay, Özet ile ortak seçimi izler. */
    fun dates(month: YearMonth, today: LocalDate): ClosedRange<LocalDate> = when (range) {
        TransactionsRange.MONTH -> month.atDay(1)..month.atEndOfMonth()
        TransactionsRange.LAST_THREE_MONTHS -> today.minusMonths(3)..today
        TransactionsRange.YEAR -> today.withDayOfYear(1)..today.withMonth(12).withDayOfMonth(31)
        TransactionsRange.ALL_TIME -> LocalDate.MIN..LocalDate.MAX
        TransactionsRange.CUSTOM -> requireNotNull(customStart)..requireNotNull(customEnd)
    }

    /** Transferin kategorisi yoktur; kategori seçimi yalnız gelir ve gideri kapsar. */
    val includesTransfers get() = type.transactionType == null && categoryIds.isEmpty()
}

private val marks = Regex("\\p{M}+")
private val amountPattern = Regex("""[0-9]+(?:[.,][0-9]{0,2})?""")

/** Dört I/i biçimini birleştirir ve birleşik/ayrık aksanları kaldırır. */
internal fun normalizeTransactionSearch(text: String): String =
    marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD).lowercase(Locale.ROOT), "").replace('ı', 'i')

/** Tutar aramasında ondalık ayıracı ortaklaştırır; kesir kısmı önek olarak kalır. */
internal fun transactionAmountPrefix(text: String): String? =
    text.trim().takeIf(amountPattern::matches)?.replace(',', '.')

internal fun parseTransactionAmountBound(text: String, locale: Locale): Long? =
    parseMoney(text, locale, alternateDecimal = true)

/** SQL'den gelen adaylarda not, görünen adlar ve lira öneki arasında eşleşme arar. */
internal class TransactionSearch(
    query: String,
    private val filters: TransactionsFilters,
    private val categoryNames: Map<Long, String>,
    private val accountNames: Map<Long, String>,
) {
    private val text = normalizeTransactionSearch(query.trim())
    private val amount = transactionAmountPrefix(query)

    fun matches(row: TransactionWithCategory): Boolean = with(row.transaction) {
        amountInRange(amountMinor) && (text.isEmpty() || matchesAmount(amountMinor) ||
            matchesText(note) || matchesText(categoryNames[categoryId]) || matchesText(accountNames[accountId]))
    }

    fun matches(transfer: TransferEntity): Boolean = with(transfer) {
        amountInRange(amountMinor) && (text.isEmpty() || matchesAmount(amountMinor) ||
            matchesText(note) || matchesText(accountNames[fromAccountId]) || matchesText(accountNames[toAccountId]))
    }

    private fun amountInRange(minor: Long) =
        minor >= (filters.minimum ?: 0L) && minor <= (filters.maximum ?: Long.MAX_VALUE)

    private fun matchesText(value: String?) = value?.let { text in normalizeTransactionSearch(it) } == true

    private fun matchesAmount(minor: Long): Boolean {
        val prefix = amount ?: return false
        val lira = minor / 100
        val cents = (minor % 100).toString().padStart(2, '0')
        return "$lira.$cents".startsWith(prefix)
    }
}
