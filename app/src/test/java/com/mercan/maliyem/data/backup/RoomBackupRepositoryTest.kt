package com.mercan.maliyem.data.backup

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.RecurringFrequency.MONTHLY
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import com.mercan.maliyem.data.local.RecurringFrequency.WEEKLY
import com.mercan.maliyem.data.receipt.RECEIPTS_DIRECTORY
import com.mercan.maliyem.data.receipt.RECEIPT_DRAFTS_DIRECTORY
import com.mercan.maliyem.data.receipt.ReceiptStore
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.data.repository.RoomTransactionRepository
import com.mercan.maliyem.notifications.BudgetAlerts
import com.mercan.maliyem.notifications.BudgetAlertTracker
import com.mercan.maliyem.notifications.ExpenseWrite
import com.mercan.maliyem.notifications.LocalNotifications
import com.mercan.maliyem.ui.settings.AppSettings
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RoomBackupRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MaliyemDatabase::class.java)
        .allowMainThreadQueries().build()
    private val receipts = ReceiptStore(RuntimeEnvironment.getApplication(), db.transactionDao())
    private var refreshes = 0
    private val repository = RoomBackupRepository(
        db, db.categoryDao(), db.transactionDao(), db.accountDao(), db.transferDao(), db.recurringRuleDao(), db.budgetDao(),
        db.savingsGoalDao(), receipts,
    ) { refreshes++ }

    @After fun closeDatabase() = db.close()

    @Test fun `backup versions one through six restore into the current Room database`() = runBlocking {
        val photo = receiptFile("6d093eb6-b924-4aaf-a7fa-8fccbe72abe0.jpg")
        val original = backup().let {
            it.copy(transactions = listOf(it.transactions.single().copy(note = "İzmir'de öğle", receiptPath = photo.name)))
        }
        for (version in 1..6) {
            val expected = original.copy(
                accounts = if (version >= 2) original.accounts else original.accounts.take(1),
                transfers = if (version >= 2) original.transfers else emptyList(),
                recurringRules = if (version >= 3) original.recurringRules else emptyList(),
                budgets = if (version >= 4) original.budgets else emptyList(),
                overallBudgetMinor = original.overallBudgetMinor.takeIf { version >= 4 },
                savingsGoals = if (version >= 5) original.savingsGoals else emptyList(),
                goalEntries = if (version >= 5) original.goalEntries else emptyList(),
                transactions = original.transactions.map {
                    it.copy(
                        accountId = if (version >= 2) it.accountId else DefaultAccounts.CASH_ID,
                        recurringRuleId = it.recurringRuleId.takeIf { version >= 3 },
                        receiptPath = it.receiptPath.takeIf { version == 6 },
                    )
                },
            )
            if (version == 6) photo.writeText("device-local receipt")
            val payload = Json.parseToJsonElement(BackupCodec.encode(expected.copy(version = version))).jsonObject.toMutableMap()
            if (version < 2) payload.keys.removeAll(setOf("accounts", "transfers"))
            if (version < 3) payload.remove("recurringRules")
            if (version < 4) payload.keys.removeAll(setOf("budgets", "overallBudgetMinor"))
            if (version < 5) payload.keys.removeAll(setOf("savingsGoals", "goalEntries"))
            val row = payload.getValue("transactions").jsonArray.single().jsonObject.toMutableMap()
            if (version < 2) row.remove("accountId")
            if (version < 3) row.remove("recurringRuleId")
            if (version < 6) row.remove("receiptPath")
            payload["transactions"] = JsonArray(listOf(JsonObject(row)))

            val decoded = BackupCodec.decode(JsonObject(payload).toString()) as BackupDecodeResult.Valid
            repository.replaceAll(decoded)
            assertEquals("Balance and row data for backup v$version", expected, repository.snapshot().copy(exportedAt = 0))
        }
    }

    @Test fun `all tables round trip and automatic sequences follow restored ids`() = runBlocking {
        restore(backup())
        val before = repository.snapshot()
        assertEquals(backup(), before.copy(exportedAt = 0))

        val high = BackupCodec.MAX_ID
        db.openHelper.writableDatabase.execSQL("UPDATE sqlite_sequence SET seq = ?", arrayOf(high))
        restore(before)
        assertEquals(before.copy(exportedAt = 0), repository.snapshot().copy(exportedAt = 0))
        assertEquals(mapOf(
            "accounts" to 2L, "budgets" to 12L, "categories" to 3L,
            "recurring_rules" to 8L, "transactions" to 9L, "transfers" to 10L,
            "savings_goals" to 13L, "goal_entries" to 16L,
        ), sequences())

        val newId = db.transactionDao().insert(db.transactionDao().getAll().single().copy(id = 0))
        assertEquals(10L, newId)
        val goal = db.savingsGoalDao().getAll().first()
        assertEquals(650L, db.savingsGoalDao().getBalance(goal.id)?.savedMinor)
        restore(before.copy(version = 4, transfers = emptyList()))
        assertEquals(0L, sequences()["transfers"])
        assertEquals(0L, sequences()["savings_goals"])
        assertEquals(0L, sequences()["goal_entries"])
        assertTrue(db.savingsGoalDao().getAll().isEmpty())
        assertTrue(db.savingsGoalDao().getEntries().isEmpty())
        assertEquals(before.budgets, repository.snapshot().budgets)
        assertEquals(3, refreshes)
    }

    @Test fun `a late write failure rolls back every table and its sequence`() = runBlocking {
        val photo = receiptFile("6d093eb6-b924-4aaf-a7fa-8fccbe72abe0.jpg")
        val entry = backup().transactions.single().copy(receiptPath = photo.name)
        restore(backup().copy(transactions = listOf(entry)))
        val before = repository.snapshot().copy(exportedAt = 0)
        val oldSequences = sequences()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_restore BEFORE INSERT ON goal_entries BEGIN SELECT RAISE(ABORT, 'restore failed'); END",
        )
        val replacement = backup().copy(
            transactions = emptyList(), budgets = emptyList(), overallBudgetMinor = null,
            transfers = listOf(backup().transfers.single().copy(id = 99)),
        )
        val error = runCatching { restore(replacement) }.exceptionOrNull()
        assertTrue("Unexpected failure: $error", error is SQLiteConstraintException)
        assertEquals(before, repository.snapshot().copy(exportedAt = 0))
        assertEquals(oldSequences, sequences())
        assertTrue(photo.exists())
        assertEquals(1, refreshes)
    }

    @Test fun `due records complete in batches and resume skips paused dates`() = runBlocking {
        val today = LocalDate.of(2026, 11, 27)
        val start = today.minusWeeks(405)
        val weekly = backup().recurringRules.single().copy(
            frequency = WEEKLY, startDate = start.toString(), nextDate = start.toString(),
        )
        restore(backup().copy(transactions = emptyList(), recurringRules = listOf(weekly)))
        val context = RuntimeEnvironment.getApplication()
        val settings = AppSettings(context).also { it.setBudgetAlerts(false) }
        val alerts = BudgetAlerts(context, db, db.budgetDao(), LocalNotifications(context, settings))
        fun recurringAt(date: LocalDate) = RecurringRepository(
            db.recurringRuleDao(), db.transactionDao(),
            Clock.fixed(date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
            alerts, receipts,
        ) { }

        val recurring = recurringAt(today)
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("CREATE TRIGGER reject_due BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'retry'); END")
        val failure = runCatching { recurring.generateDue(throwOnFailure = true) }.exceptionOrNull()
        assertTrue(failure is SQLiteConstraintException)
        assertTrue(db.transactionDao().getAll().isEmpty())
        assertEquals(start, db.recurringRuleDao().getById(weekly.id)?.nextDate)
        sqlite.execSQL("DROP TRIGGER reject_due")

        assertEquals(406, recurring.generateDue(throwOnFailure = true))
        assertEquals(406, db.transactionDao().getAll().size)
        assertEquals(0, recurring.generateDue())
        assertEquals(406, db.transactionDao().getAll().size)

        recurring.setPaused(weekly.id, true)
        recurringAt(today.plusWeeks(3)).setPaused(weekly.id, false)
        val transactions = db.transactionDao().getAll()
        assertEquals(407, transactions.size)
        assertEquals(today.plusWeeks(3), transactions.last().date)
    }

    @Test fun `JSON restore keeps local receipts and drops missing or unsafe paths`() = runBlocking {
        val local = receiptFile("6d093eb6-b924-4aaf-a7fa-8fccbe72abe0.jpg")
        val removed = receiptFile("6d093eb6-b924-4aaf-a7fa-8fccbe72abe1.jpg")
        val missing = "6d093eb6-b924-4aaf-a7fa-8fccbe72abe2.jpg"
        val unsafe = "../${local.name}"
        val entry = backup().transactions.single()
        restore(backup().copy(transactions = listOf(entry.copy(receiptPath = removed.name))))

        // Recreate a device-local photo whose path is present in the incoming backup.
        local.writeText("local")
        restore(backup().copy(transactions = listOf(
            entry.copy(receiptPath = local.name),
            entry.copy(id = entry.id + 1, receiptPath = missing),
            entry.copy(id = entry.id + 2, receiptPath = unsafe),
        )))
        assertEquals(listOf(local.name, null, null), repository.snapshot().transactions.map { it.receiptPath })
        assertTrue(local.exists())
        assertFalse(removed.exists())
        assertFalse(BackupCodec.encode(repository.snapshot()).contains("\"local\""))
    }

    @Test fun `receipt deletion waits for undo and reconciliation preserves shared files`() = runBlocking {
        restore(backup())
        val photo = receiptFile("6d093eb6-b924-4aaf-a7fa-8fccbe72abe0.jpg")
        val orphan = receiptFile("6d093eb6-b924-4aaf-a7fa-8fccbe72abe1.jpg")
        val dao = db.transactionDao()
        val entry = dao.getAll().single().copy(receiptPath = photo.name)
        dao.update(entry)
        val transactions = transactionRepository()
        val removed = checkNotNull(transactions.deleteAndGet(entry.id))

        receipts.reconcile()
        assertFalse(orphan.exists())
        assertTrue(photo.exists())
        assertTrue(transactions.restore(removed))
        receipts.discardUndo(removed)?.join()
        assertEquals(photo.name, dao.getById(entry.id)?.receiptPath)
        assertTrue(photo.exists())

        val shared = dao.insert(entry.copy(id = 0))
        val expiring = checkNotNull(transactions.deleteAndGet(entry.id))
        val last = checkNotNull(transactions.deleteAndGet(shared))
        receipts.discardUndo(expiring)?.join()
        assertTrue(photo.exists())
        receipts.discardUndo(last)?.join()
        assertFalse(photo.exists())
    }

    @Test fun `replacing removing and permanently deleting receipts clean their files`() = runBlocking {
        restore(backup())
        val dao = db.transactionDao()
        val entry = dao.getAll().single()
        val transactions = transactionRepository()
        val old = receiptFile("6d093eb6-b924-4aaf-a7fa-8fccbe72abe0.jpg")
        dao.update(entry.copy(receiptPath = old.name))
        val context = RuntimeEnvironment.getApplication()
        val draftDirectory = File(context.filesDir, RECEIPT_DRAFTS_DIRECTORY).apply { mkdirs() }
        val draft = File(draftDirectory, "6d093eb6-b924-4aaf-a7fa-8fccbe72abe1.jpg").apply { writeText("new") }
        val saved = File(old.parentFile, draft.name)

        assertFalse(transactions.update(entry.copy(id = 99, receiptPath = draft.name)))
        assertFalse(saved.exists())
        assertTrue(draft.exists())
        assertTrue(old.exists())

        assertTrue(transactions.update(entry.copy(receiptPath = draft.name)))
        assertFalse(old.exists())
        assertEquals("new", saved.readText())
        assertTrue(transactions.update(entry))
        assertFalse(saved.exists())
        assertNull(dao.getById(entry.id)?.receiptPath)

        val added = transactions.add(entry.copy(id = 0, receiptPath = draft.name))
        transactions.delete(added)
        assertFalse(saved.exists())

        dao.update(entry.copy(receiptPath = draft.name))
        receipts.reconcile()
        assertNull(dao.getById(entry.id)?.receiptPath)
    }

    private fun transactionRepository() = RoomTransactionRepository(
        db.transactionDao(),
        object : BudgetAlertTracker {
            override suspend fun <T> track(write: suspend () -> ExpenseWrite<T>): T = write().result
        },
        receipts,
    ) { }

    private fun receiptFile(name: String): File {
        val directory = File(RuntimeEnvironment.getApplication().filesDir, RECEIPTS_DIRECTORY).apply { mkdirs() }
        return File(directory, name).apply { writeText(name) }
    }

    private suspend fun restore(backup: BackupFile) {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup)) as BackupDecodeResult.Valid
        repository.replaceAll(decoded)
    }

    private fun sequences(): Map<String, Long> = buildMap {
        db.openHelper.writableDatabase.query("SELECT name, seq FROM sqlite_sequence").use { rows ->
            while (rows.moveToNext()) put(rows.getString(0), rows.getLong(1))
        }
    }

    private fun backup() = BackupFile(
        format = BackupFile.FORMAT, version = BackupFile.VERSION, exportedAt = 0,
        categories = listOf(
            BackupCategory(1, EXPENSE, "groceries", icon = "cart", sortOrder = 0),
            BackupCategory(2, EXPENSE, DefaultCategories.OTHER_EXPENSE, icon = "dots", sortOrder = 1),
            BackupCategory(3, INCOME, DefaultCategories.OTHER_INCOME, icon = "dots", sortOrder = 0),
        ),
        accounts = listOf(
            BackupAccount(1, "cash", icon = "wallet", sortOrder = 0),
            BackupAccount(2, name = "Card", icon = "card", sortOrder = 1),
        ),
        recurringRules = listOf(BackupRecurringRule(
            8, EXPENSE, 199, 1, 2, frequency = MONTHLY,
            startDate = "2026-01-31", nextDate = "2026-02-28", createdAt = 100,
        )),
        transactions = listOf(BackupTransaction(
            9, EXPENSE, 199, 1, "2026-01-31", createdAt = 100, accountId = 2, recurringRuleId = 8,
        )),
        transfers = listOf(BackupTransfer(10, 1, 2, 200, "2026-01-31", createdAt = 100)),
        budgets = listOf(BackupBudget(12, 1, 990, 100)),
        overallBudgetMinor = 1990,
        savingsGoals = listOf(
            BackupSavingsGoal(12, "Trip", 2000, "2026-02-28", "plane", 100),
            BackupSavingsGoal(13, "Books", 500, icon = "book", createdAt = 101, archived = true),
        ),
        goalEntries = listOf(
            BackupGoalEntry(14, 12, 800, "2026-01-31", "First deposit"),
            BackupGoalEntry(15, 12, -150, "2026-02-01"),
            BackupGoalEntry(16, 13, 500, "2026-02-02"),
        ),
    )
}
