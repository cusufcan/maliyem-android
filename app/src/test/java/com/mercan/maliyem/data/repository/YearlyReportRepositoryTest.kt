package com.mercan.maliyem.data.repository

import android.content.res.Resources
import androidx.room.Room
import com.mercan.maliyem.R
import com.mercan.maliyem.data.backup.CsvExporter
import com.mercan.maliyem.data.backup.TransactionCsvExport
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import com.mercan.maliyem.data.local.TransferEntity
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class YearlyReportRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, MaliyemDatabase::class.java).build()
    private val reports = YearlyReportRepository(db)

    @After fun closeDatabase() = db.close()

    @Test fun `year aggregates exact months and ranks expense categories against all expenses`() = runBlocking {
        seed()
        db.transactionDao().insertAll(
            listOf(
                transaction("2023-12-31", EXPENSE, 7_999),
                transaction("2024-01-01", EXPENSE, 28),
                transaction("2024-01-31", EXPENSE, 12, category = 2),
                transaction("2024-02-29", EXPENSE, 40),
                transaction("2024-12-31", INCOME, 170),
                transaction("2025-01-01", INCOME, 8_000),
            ) + (3L..6L).map { transaction("2024-07-01", EXPENSE, 6, category = it) },
        )
        db.transferDao().insert(TransferEntity(fromAccountId = 1, toAccountId = 2, amountMinor = 90, date = LocalDate.of(2024, 1, 1), createdAt = 0))
        db.transferDao().insert(TransferEntity(fromAccountId = 1, toAccountId = 2, amountMinor = 80, date = LocalDate.of(2022, 1, 1), createdAt = 0))

        val report = reports.observe(currentYear = 2026, requestedYear = 2024).first()
        assertEquals(listOf(2026, 2025, 2024, 2023, 2022), report.years)
        assertEquals(2024, report.year)
        assertEquals((1..12).map { YearMonth.of(2024, it) }, report.months.map { it.month })
        assertEquals(listOf(40L, 40, 0, 0, 0, 0, 24, 0, 0, 0, 0, 0).map { it.toBigInteger() }, report.months.map { it.expenseMinor })
        assertEquals(170.toBigInteger(), report.months.last().incomeMinor)
        assertEquals(170.toBigInteger(), report.incomeMinor)
        assertEquals(104.toBigInteger(), report.expenseMinor)
        assertEquals(66.toBigInteger(), report.netMinor)
        assertEquals(9.toBigInteger(), report.averageExpenseMinor)
        assertEquals(YearMonth.of(2024, 1), report.highestExpenseMonth?.month)
        assertEquals(listOf(1L, 2, 3, 4, 5), report.topCategories.map { it.id })
        assertEquals(listOf(65, 12, 6, 6, 6), report.topCategories.map { it.sharePercent })
        assertEquals("Category 1", report.topCategories.first().name)

        val transferOnly = reports.load(2026, 2022)
        assertEquals(2022, transferOnly.year)
        assertEquals(BigInteger.ZERO, transferOnly.expenseMinor)
        assertNull(transferOnly.highestExpenseMonth)
    }

    @Test fun `an empty or unavailable year opens the current year with twelve empty months`() = runBlocking {
        val report = reports.load(currentYear = 2026, requestedYear = 2024)
        assertEquals(listOf(2026), report.years)
        assertEquals(2026, report.year)
        assertEquals(12, report.months.size)
        assertTrue(report.months.all { it.incomeMinor == BigInteger.ZERO && it.expenseMinor == BigInteger.ZERO })
        assertEquals(BigInteger.ZERO, report.averageExpenseMinor)
        assertNull(report.highestExpenseMonth)
        assertTrue(report.topCategories.isEmpty())
    }

    @Test fun `aggregates beyond Long remain exact even at the supported date limits`() = runBlocking {
        seed()
        val earliest = LocalDate.MIN
        val latest = LocalDate.MAX
        db.transactionDao().insertAll(
            listOf(
                transaction(earliest.toString(), INCOME, 1),
                transaction(latest.toString(), EXPENSE, Long.MAX_VALUE),
                transaction(latest.toString(), EXPENSE, Long.MAX_VALUE),
            ),
        )
        val report = reports.load(currentYear = 2026, requestedYear = latest.year)
        val total = Long.MAX_VALUE.toBigInteger() * 2.toBigInteger()
        assertEquals(listOf(latest.year, 2026, earliest.year), report.years)
        assertEquals(total, report.expenseMinor)
        assertEquals(total.negate(), report.netMinor)
        assertEquals(total, report.topCategories.single().amountMinor)
        assertEquals(100, report.topCategories.single().sharePercent)
    }

    @Test fun `year CSV includes both boundaries and transfers while all-data export retains other years`() = runBlocking {
        seed()
        db.transactionDao().insertAll(
            listOf(
                transaction("2025-12-31", EXPENSE, 101),
                transaction("2026-01-01", INCOME, 572),
                transaction("2026-12-31", EXPENSE, 234),
                transaction("2027-01-01", EXPENSE, 204),
            ),
        )
        db.transferDao().insertAll(
            listOf(
                TransferEntity(fromAccountId = 1, toAccountId = 2, amountMinor = 625, date = LocalDate.of(2026, 6, 1), createdAt = 0),
                TransferEntity(fromAccountId = 1, toAccountId = 2, amountMinor = 999, date = LocalDate.of(2027, 1, 1), createdAt = 0),
            ),
        )
        val export = TransactionCsvExport(db, context)
        val resources = object : Resources(context.assets, context.resources.displayMetrics, context.resources.configuration) {
            override fun getString(id: Int): String = when (id) {
                R.string.add_expense -> "Expense"
                R.string.add_income -> "Income"
                R.string.add_transfer -> "Transfer"
                else -> error("Unexpected string ID: $id")
            }

            override fun getString(id: Int, vararg formatArgs: Any): String {
                assertEquals(R.string.transfer_direction, id)
                return formatArgs.joinToString(" → ")
            }
        }
        val rows = export.rows(resources, 2026)
        assertEquals(listOf(572L, 234L, 625L), rows.map { it.amountMinor })
        val csv = CsvExporter.encode(rows)
        assertTrue(csv.startsWith("\uFEFFdate,type,category,amount,note,account\r\n2026-01-01,"))
        assertEquals("2026-06-01,Transfer,,6.25,,Cash → Card", csv.lines()[2])
        assertTrue(csv.lines()[3].startsWith("2026-12-31,"))
        assertEquals(6, export.rows(resources, null).size)
    }

    private suspend fun seed() {
        db.accountDao().insertAll(listOf(AccountEntity(1, name = "Cash", icon = "wallet", sortOrder = 0), AccountEntity(2, name = "Card", icon = "card", sortOrder = 1)))
        db.categoryDao().insertAll((1L..7L).map { id ->
            CategoryEntity(id, if (id == 7L) INCOME else EXPENSE, name = "Category $id", icon = "dots", sortOrder = id.toInt())
        })
    }

    private fun transaction(date: String, type: TransactionType, amount: Long, category: Long = if (type == INCOME) 7 else 1) =
        TransactionEntity(type = type, amountMinor = amount, categoryId = category, date = LocalDate.parse(date), createdAt = 0, accountId = 1)
}
