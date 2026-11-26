package com.mercan.maliyem.data.repository

import androidx.room.withTransaction
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.TransactionReportDao
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.util.roundedPercent
import java.math.BigInteger
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.map

data class ReportMonth(val month: YearMonth, val incomeMinor: BigInteger, val expenseMinor: BigInteger)

data class ReportCategory(
    val id: Long,
    val key: String?,
    val name: String?,
    val icon: String,
    val amountMinor: BigInteger,
    val sharePercent: Int,
)

data class YearlyReport(
    val year: Int,
    val years: List<Int>,
    val months: List<ReportMonth>,
    val incomeMinor: BigInteger,
    val expenseMinor: BigInteger,
    val topCategories: List<ReportCategory>,
) {
    val netMinor = incomeMinor - expenseMinor
    // Half a divisor rounds the nonnegative total to the nearest kuruş.
    val averageExpenseMinor = (expenseMinor + BigInteger.valueOf(6)) / BigInteger.valueOf(12)
    val highestExpenseMonth = months.maxByOrNull { it.expenseMinor }?.takeIf { it.expenseMinor.signum() > 0 }
}

class YearlyReportRepository @Inject constructor(private val db: MaliyemDatabase) {
    private val dao = db.transactionReportDao()

    fun observe(currentYear: Int, requestedYear: Int) =
        db.invalidationTracker.createFlow("transactions", "transfers", "categories").map {
            load(currentYear, requestedYear)
        }

    internal suspend fun load(currentYear: Int, requestedYear: Int): YearlyReport = db.withTransaction {
        val years = (dao.yearsWithData() + currentYear).distinct().sortedDescending()
        val selectedYear = requestedYear.takeIf { it in years } ?: currentYear
        val year = Year.of(selectedYear)
        val months = (1..12).map { number ->
            val month = year.atMonth(number)
            val totals = dao.totalsByType(month.atDay(1), month.atEndOfMonth()).associate { it.type to it.total.toMinor() }
            ReportMonth(
                month,
                totals[TransactionType.INCOME] ?: BigInteger.ZERO,
                totals[TransactionType.EXPENSE] ?: BigInteger.ZERO,
            )
        }
        val expense = months.sumOf { it.expenseMinor }
        val categories = if (expense.signum() == 0) emptyList() else {
            dao.expenseTotalsByCategory(year.atDay(1), year.atMonth(12).atEndOfMonth()).map {
                val amount = it.total.toMinor()
                ReportCategory(it.id, it.key, it.name, it.icon, amount, roundedPercent(amount, expense).toInt())
            }.sortedWith(compareByDescending<ReportCategory> { it.amountMinor }.thenBy { it.id }).take(5)
        }
        YearlyReport(selectedYear, years, months, months.sumOf { it.incomeMinor }, expense, categories)
    }
}

/** Each indexed lookup skips directly to the last record before the previous result's year. */
private suspend fun TransactionReportDao.yearsWithData(): List<Int> = buildList {
    var before = LocalDate.MAX.toEpochDay() + 1
    while (true) {
        val latest = latestDateBefore(before) ?: break
        val year = LocalDate.ofEpochDay(latest).year
        add(year)
        before = Year.of(year).atDay(1).toEpochDay()
    }
}
