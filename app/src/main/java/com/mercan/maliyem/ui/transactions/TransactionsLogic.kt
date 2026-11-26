package com.mercan.maliyem.ui.transactions

import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TransferEntity
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/** Pano İşlemler süzgeci; sıra çiplerin sırasıdır. */
enum class TransactionFilter { ALL, INCOME, EXPENSE }

/** Bir günün başlığı (.dh) ve kayıtları; [netMinor] gelir − gider. */
data class DayGroup(val date: LocalDate, val netMinor: BigInteger, val rows: List<DayRow>)

sealed interface DayRow {
    val id: Long
    val date: LocalDate
    val createdAt: Long
    val netMinor: BigInteger

    data class Record(val item: TransactionWithCategory) : DayRow {
        override val id get() = item.transaction.id
        override val date get() = item.transaction.date
        override val createdAt get() = item.transaction.createdAt
        override val netMinor get() = item.transaction.amountMinor.toBigInteger().let {
            if (item.transaction.type == TransactionType.INCOME) it else -it
        }
    }

    data class Transfer(val item: TransferEntity) : DayRow {
        override val id get() = item.id
        override val date get() = item.date
        override val createdAt get() = item.createdAt
        override val netMinor get() = BigInteger.ZERO
    }
}

data class TransactionsUiState(
    val month: YearMonth,
    val filter: TransactionFilter,
    val days: List<DayGroup>,
    val accounts: List<AccountEntity>,
) {
    val count: Int = days.sumOf { it.rows.size }
}

/** Günler yeniden eskiye; gün içinde son eklenen üstte. Transfer yalnız Tümü'nde görünür. */
fun groupByDay(
    rows: List<TransactionWithCategory>,
    filter: TransactionFilter,
    transfers: List<TransferEntity> = emptyList(),
): List<DayGroup> {
    val records = rows.asSequence()
        .filter {
            when (filter) {
                TransactionFilter.ALL -> true
                TransactionFilter.INCOME -> it.transaction.type == TransactionType.INCOME
                TransactionFilter.EXPENSE -> it.transaction.type == TransactionType.EXPENSE
            }
        }
        .map(DayRow::Record)
    val visibleTransfers = if (filter == TransactionFilter.ALL) transfers.asSequence().map(DayRow::Transfer) else emptySequence()
    return (records + visibleTransfers)
        .sortedWith(compareByDescending(DayRow::date).thenByDescending(DayRow::createdAt).thenByDescending(DayRow::id))
        .groupBy(DayRow::date)
        .map { (date, items) -> DayGroup(date, items.sumOf(DayRow::netMinor), items) }
}
