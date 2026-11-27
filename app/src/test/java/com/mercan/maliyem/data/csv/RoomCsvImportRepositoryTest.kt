package com.mercan.maliyem.data.csv

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RoomCsvImportRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, MaliyemDatabase::class.java).build()
    private var refreshes = 0
    private val repository = CsvImportRepository(db, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)) { refreshes++ }
    private val labels = CsvImportLabels(mapOf(
        R.string.account_cash to listOf("Cash", "Nakit"),
        R.string.category_groceries to listOf("Groceries", "Market"),
        R.string.category_rent to listOf("Rent", "Kira"),
    ))

    @Before fun seed() = runBlocking {
        db.accountDao().insertAll(listOf(DefaultAccounts.cash()))
        db.categoryDao().insertAll(DefaultCategories.entities())
    }

    @After fun close() = db.close()

    @Test fun `preview and import resolve both languages create referenced labels and skip invalid transfers`() = runBlocking {
        val csv = csv("""
            date,type,category,amount,note,account
            2024-01-01,Gider,,1.00,,Nakit
            2024-01-01,Expense,Workshop,2.00,tools,Card
            2024-01-02,Income,Workshop,3.00,,card
            2024-01-03,Transfer,,4.00,,Cash → Savings
            2024-01-03,Transfer,,5.00,,Cash → Nakit
            2024-02-30,Expense,Unused,1.00,,Unused
        """)
        val preview = repository.preview(csv, true, labels)
        assertEquals(6, preview.total)
        assertEquals(4, preview.ready)
        assertEquals(0, preview.duplicates)
        assertEquals(listOf(CsvIssue(6, CsvRowError.TRANSFER_ACCOUNTS), CsvIssue(7, CsvRowError.DATE)), preview.issues)
        assertEquals(listOf(CsvNewCategory("Workshop", EXPENSE), CsvNewCategory("Workshop", INCOME)), preview.newCategories)
        assertEquals(listOf("Card", "Savings"), preview.newAccounts)
        assertEquals(1, db.accountDao().getAll().size)

        assertEquals(CsvImportResult(4, 0, 2), repository.import(csv, true, labels))
        val accounts = db.accountDao().getAll().associateBy { it.name }
        val categories = db.categoryDao().getAll().associateBy { it.id }
        val transactions = db.transactionDao().getAll()
        assertEquals(listOf(100L, 200L, 300L), transactions.map { it.amountMinor })
        assertEquals(listOf(DefaultAccounts.CASH_ID, accounts.getValue("Card").id, accounts.getValue("Card").id),
            transactions.map { it.accountId })
        assertEquals(DefaultCategories.OTHER_EXPENSE, categories.getValue(transactions.first().categoryId).key)
        val transfer = db.transferDao().getAll().single()
        assertEquals(DefaultAccounts.CASH_ID, transfer.fromAccountId)
        assertEquals(accounts.getValue("Savings").id, transfer.toAccountId)
        assertEquals(1, refreshes)
        assertEquals(CsvImportResult(0, 4, 2), repository.import(csv, true, labels))
        assertEquals(1, refreshes)
    }

    @Test fun `confirm rechecks the database and duplicate comparison includes account category and note`() = runBlocking {
        val initial = csv("""
            date,type,category,amount,note,account
            2024-01-01,Expense,Groceries,1.00,a,Cash
        """)
        val repeated = csv("""
            date,type,category,amount,note,account
            2024-01-01,Gider,Market,1.00,a,Nakit
            2024-01-01,Expense,Groceries,1.00,a,Card
            2024-01-01,Expense,Groceries,1.00,b,Cash
            2024-01-01,Expense,Rent,1.00,a,Cash
            2024-01-01,Expense,Rent,1.00,a,Cash
        """)
        assertEquals(5, repository.preview(repeated, true, labels).ready)
        repository.import(initial, true, labels)
        val preview = repository.preview(repeated, true, labels)
        assertEquals(1, preview.duplicates)
        assertEquals(listOf("Card"), preview.newAccounts)
        assertEquals(CsvImportResult(4, 1, 0), repository.import(repeated, true, labels))
        val card = db.accountDao().getAll().single { it.name == "Card" }
        val transactions = db.transactionDao().getAll()
        assertEquals(5, transactions.size)
        assertEquals("a", transactions.single { it.accountId == card.id }.note)
        assertEquals(CsvImportResult(0, 5, 0), repository.import(repeated, true, labels))
        assertEquals(CsvImportResult(5, 0, 0), repository.import(repeated, false, labels))
        assertEquals(10, db.transactionDao().getAll().size)
    }

    @Test fun `failure during transfer insert rolls back earlier entity and transaction inserts`() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_transfer BEFORE INSERT ON transfers BEGIN SELECT RAISE(ABORT, 'failed'); END",
        )
        val csv = csv("""
            date,type,category,amount,note,account
            2024-01-01,Expense,New category,1.00,,New card
            2024-01-01,Transfer,,2.00,,Cash → New card
        """)
        val error = runCatching { repository.import(csv, true, labels) }.exceptionOrNull()
        assertTrue(error is SQLiteConstraintException)
        assertEquals(1, db.accountDao().getAll().size)
        assertEquals(DefaultCategories.all.size, db.categoryDao().getAll().size)
        assertTrue(db.transactionDao().getAll().isEmpty())
        assertTrue(db.transferDao().getAll().isEmpty())
        assertEquals(0, refreshes)
    }

    private fun csv(rows: String): MappedCsv {
        val document = CsvImportParser.parse(rows.trimIndent())
        return CsvImportParser.map(document, document.suggestedMapping())
    }
}
