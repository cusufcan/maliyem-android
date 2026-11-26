package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import java.time.LocalDate

private const val AmountBase = 4_294_967_296L

// Splitting the integer sum keeps large totals exact without SQLite SUM(amountMinor) overflow.
data class ReportSum(val whole: Long, val remainder: Long) {
    fun toMinor() = whole.toBigInteger() * AmountBase.toBigInteger() + remainder.toBigInteger()
}

data class ReportTypeTotal(val type: TransactionType, @Embedded val total: ReportSum)

data class ReportCategoryTotal(
    val id: Long,
    val key: String?,
    val name: String?,
    val icon: String,
    @Embedded val total: ReportSum,
)

@Dao
interface TransactionReportDao {
    @Query("SELECT * FROM transactions WHERE date IN (:dates)")
    suspend fun transactionsOnDates(dates: List<LocalDate>): List<TransactionEntity>

    @Query("SELECT * FROM transfers WHERE date IN (:dates)")
    suspend fun transfersOnDates(dates: List<LocalDate>): List<TransferEntity>

    @Query("""
        SELECT MAX(latest) FROM (
            SELECT MAX(date) AS latest FROM transactions WHERE date < :before
            UNION ALL
            SELECT MAX(date) AS latest FROM transfers WHERE date < :before
        )
    """)
    suspend fun latestDateBefore(before: Long): Long?

    @Query("""
        SELECT type, SUM(amountMinor / $AmountBase) AS whole, SUM(amountMinor % $AmountBase) AS remainder
        FROM transactions WHERE date BETWEEN :from AND :through GROUP BY type
    """)
    suspend fun totalsByType(from: LocalDate, through: LocalDate): List<ReportTypeTotal>

    @Query("""
        SELECT c.id, c.key, c.name, c.icon,
            SUM(t.amountMinor / $AmountBase) AS whole, SUM(t.amountMinor % $AmountBase) AS remainder
        FROM transactions AS t JOIN categories AS c ON c.id = t.categoryId
        WHERE t.type = 'EXPENSE' AND t.date BETWEEN :from AND :through
        GROUP BY c.id
    """)
    suspend fun expenseTotalsByCategory(from: LocalDate, through: LocalDate): List<ReportCategoryTotal>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :through ORDER BY date, createdAt, id")
    suspend fun transactionsForExport(from: LocalDate, through: LocalDate): List<TransactionEntity>

    @Query("SELECT * FROM transfers WHERE date BETWEEN :from AND :through ORDER BY date, createdAt, id")
    suspend fun transfersForExport(from: LocalDate, through: LocalDate): List<TransferEntity>
}
