package com.mercan.maliyem.ui.transactions

import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TransferEntity
import java.math.BigInteger
import java.time.LocalDate

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

/** Sonuç sayısı transferleri de kapsar; gelir ve gider yalnız kayıt satırlarından hesaplanır. */
data class TransactionsResult(val days: List<DayGroup>) {
    val count = days.sumOf { it.rows.size }
    val incomeMinor = days.sumOf { day -> day.rows.sumOf { it.netMinor.max(BigInteger.ZERO) } }
    val expenseMinor = days.sumOf { day -> day.rows.sumOf { it.netMinor.min(BigInteger.ZERO).negate() } }
}

/** Günler yeniden eskiye; gün içinde son eklenen üstte. */
fun groupByDay(
    rows: List<TransactionWithCategory>,
    transfers: List<TransferEntity> = emptyList(),
): List<DayGroup> =
    (rows.asSequence().map(DayRow::Record) + transfers.asSequence().map(DayRow::Transfer))
        .sortedWith(compareByDescending(DayRow::date).thenByDescending(DayRow::createdAt).thenByDescending(DayRow::id))
        .groupBy(DayRow::date)
        .map { (date, items) -> DayGroup(date, items.sumOf(DayRow::netMinor), items) }
