package com.mercan.maliyem.ui.transactions

import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.ui.summary.SummaryRow
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/** Pano İşlemler süzgeci; sıra çiplerin sırasıdır. */
enum class TransactionFilter { ALL, INCOME, EXPENSE }

/** Bir günün başlığı (.dh) ve kayıtları; [netMinor] gelir − gider. */
data class DayGroup(val date: LocalDate, val netMinor: BigInteger, val rows: List<SummaryRow>)

data class TransactionsUiState(
    val month: YearMonth,
    val filter: TransactionFilter,
    val days: List<DayGroup>,
) {
    val count: Int = days.sumOf { it.rows.size }
}

/** Günler yeniden eskiye; gün içinde son eklenen üstte. Süzgeç gün netini de etkiler (pano böyle). */
fun groupByDay(rows: List<TransactionWithCategory>, filter: TransactionFilter): List<DayGroup> =
    rows.asSequence()
        .filter {
            when (filter) {
                TransactionFilter.ALL -> true
                TransactionFilter.INCOME -> it.transaction.type == TransactionType.INCOME
                TransactionFilter.EXPENSE -> it.transaction.type == TransactionType.EXPENSE
            }
        }
        .sortedWith(compareByDescending<TransactionWithCategory> { it.transaction.date }.thenByDescending { it.transaction.createdAt }.thenByDescending { it.transaction.id })
        .groupBy { it.transaction.date }
        .map { (date, items) ->
            DayGroup(
                date,
                items.sumOf {
                    val minor = it.transaction.amountMinor.toBigInteger()
                    if (it.transaction.type == TransactionType.INCOME) minor else -minor
                },
                items.map { (t, c) -> SummaryRow(t.id, t.type, t.amountMinor, t.note, c.key, c.name, c.icon) },
            )
        }
