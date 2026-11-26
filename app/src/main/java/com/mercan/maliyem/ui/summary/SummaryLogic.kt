package com.mercan.maliyem.ui.summary

import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
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
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val recent: List<SummaryRow> = emptyList(),
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
    val isEmpty: Boolean get() = recent.isEmpty()

    /** Giderin gelire oranı (%); gelir yoksa null ve satır gizlenir. */
    val spentPercent: Int?
        get() = if (incomeMinor <= 0) null else Math.round(expenseMinor * 100.0 / incomeMinor).toInt()
}

const val RECENT_COUNT = 4

/** [rows] DAO'dan en yeni başta gelir; sıra yine de burada garanti edilir. */
fun buildSummary(rows: List<TransactionWithCategory>, month: YearMonth): SummaryUiState {
    var income = 0L
    var expense = 0L
    rows.forEach {
        when (it.transaction.type) {
            TransactionType.INCOME -> income += it.transaction.amountMinor
            TransactionType.EXPENSE -> expense += it.transaction.amountMinor
        }
    }
    val recent = rows
        .sortedWith(compareByDescending<TransactionWithCategory> { it.transaction.date }.thenByDescending { it.transaction.createdAt })
        .take(RECENT_COUNT)
        .map { (t, c) -> SummaryRow(t.id, t.type, t.amountMinor, t.note, c.key, c.name, c.icon) }
    return SummaryUiState(month, income, expense, recent)
}
