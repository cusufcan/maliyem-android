package com.mercan.maliyem.ui.summary

import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.util.roundedPercent
import java.math.BigInteger
import java.time.YearMonth

/** Listede gösterilen tek kayıt; ad çözümü (key → strings.xml) ekranda yapılır. */
data class SummaryRow(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val note: String?,
    val categoryKey: String?,
    val categoryName: String?,
    val icon: String,
)

data class SummaryUiState(
    val month: YearMonth,
    val incomeMinor: BigInteger = BigInteger.ZERO,
    val expenseMinor: BigInteger = BigInteger.ZERO,
    val recent: List<SummaryRow> = emptyList(),
    /** ‹ › hedefleri; gidilecek ay yoksa null ve düğme pasif. */
    val previousMonth: YearMonth? = null,
    val nextMonth: YearMonth? = null,
    /** Boş ay kartı: kaydı olan bir önceki ayın neti. */
    val previousNet: Pair<YearMonth, BigInteger>? = null,
) {
    val netMinor: BigInteger = incomeMinor - expenseMinor
    val isEmpty: Boolean get() = recent.isEmpty()
    val overspent: Boolean get() = expenseMinor > incomeMinor

    /** Giderin gelire oranı (%); gelir yoksa null ve satır gizlenir. */
    val spentPercent: BigInteger? = if (incomeMinor.signum() == 0) null else roundedPercent(expenseMinor, incomeMinor)
}

const val RECENT_COUNT = 4

/** [rows] DAO'dan en yeni başta gelir; sıra yine de burada garanti edilir. */
fun buildSummary(rows: List<TransactionWithCategory>, month: YearMonth): SummaryUiState {
    var income = BigInteger.ZERO
    var expense = BigInteger.ZERO
    rows.forEach {
        when (it.transaction.type) {
            TransactionType.INCOME -> income += it.transaction.amountMinor.toBigInteger()
            TransactionType.EXPENSE -> expense += it.transaction.amountMinor.toBigInteger()
        }
    }
    val recent = rows
        .sortedWith(compareByDescending<TransactionWithCategory> { it.transaction.date }.thenByDescending { it.transaction.createdAt }.thenByDescending { it.transaction.id })
        .take(RECENT_COUNT)
        .map { (t, c) -> SummaryRow(t.id, t.type, t.amountMinor, t.note, c.key, c.name, c.icon) }
    return SummaryUiState(month, income, expense, recent)
}

/**
 * ‹ › ile gidilecek aylar: yalnız kaydı olan aylar ve [today]; aradaki boş aylar atlanır.
 * Bu ay boş olsa da gösterilir, çünkü yeni kayıt oraya girer.
 */
fun adjacentMonths(current: YearMonth, withData: Set<YearMonth>, today: YearMonth): Pair<YearMonth?, YearMonth?> {
    val months = withData + today
    return months.filter { it < current }.maxOrNull() to months.filter { it > current }.minOrNull()
}
