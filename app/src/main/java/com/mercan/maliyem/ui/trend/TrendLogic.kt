package com.mercan.maliyem.ui.trend

import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TrendEntry
import com.mercan.maliyem.util.roundedPercent
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

/** Bir takvim ayının gelir, gider ve net toplamları, kuruş cinsinden. */
data class MonthlyTrend(val month: YearMonth, val incomeMinor: BigInteger, val expenseMinor: BigInteger) {
    val netMinor: BigInteger get() = incomeMinor - expenseMinor
}

private val firstSupportedMonth = YearMonth.from(LocalDate.MIN)

/** Yedekten gelebilen en erken tarihte önceki ay yoktur. */
fun previousTrendMonth(month: YearMonth): YearMonth? =
    if (month == firstSupportedMonth) null else month.minusMonths(1)

/** Seçili ay dahil son altı takvim ayı, eskiden yeniye. */
fun trendMonths(month: YearMonth): List<YearMonth> =
    generateSequence(month, ::previousTrendMonth).take(6).toList().asReversed()

/** Pencerenin her ayını döndürür. Kayıt olmayan ayların iki toplamı da sıfırdır. */
fun buildTrend(months: List<YearMonth>, rows: List<TrendEntry>): List<MonthlyTrend> {
    val byMonth = rows.groupBy { YearMonth.from(it.date) }
    return months.map { month ->
        val (income, expense) = byMonth[month].orEmpty().partition { it.type == TransactionType.INCOME }
        MonthlyTrend(month, income.sumOf { it.amountMinor.toBigInteger() }, expense.sumOf { it.amountMinor.toBigInteger() })
    }
}

/** İşaretli yüzde değişimi, en yakın tam sayıya yuvarlanır. Sıfır tabanda sonuç tanımsızdır. */
fun percentChange(current: BigInteger, previous: BigInteger): BigInteger? {
    if (previous.signum() == 0) return null
    val difference = current - previous
    val magnitude = roundedPercent(difference.abs(), previous)
    return if (difference.signum() < 0) magnitude.negate() else magnitude
}
