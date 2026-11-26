package com.mercan.maliyem.data.backup

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.RecurringFrequency.MONTHLY
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import com.mercan.maliyem.data.local.RecurringFrequency.WEEKLY
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.notifications.BudgetAlerts
import com.mercan.maliyem.notifications.LocalNotifications
import com.mercan.maliyem.ui.settings.AppSettings
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    private var refreshes = 0
    private val repository = RoomBackupRepository(
        db, db.categoryDao(), db.transactionDao(), db.accountDao(), db.transferDao(), db.recurringRuleDao(), db.budgetDao(),
        db.savingsGoalDao(),
    ) { refreshes++ }

    @After fun closeDatabase() = db.close()

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
        restore(backup())
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
            alerts,
        ) { }

        val recurring = recurringAt(today)
        assertEquals(406, recurring.generateDue())
        assertEquals(406, db.transactionDao().getAll().size)
        assertEquals(0, recurring.generateDue())
        assertEquals(406, db.transactionDao().getAll().size)

        recurring.setPaused(weekly.id, true)
        recurringAt(today.plusWeeks(3)).setPaused(weekly.id, false)
        val transactions = db.transactionDao().getAll()
        assertEquals(407, transactions.size)
        assertEquals(today.plusWeeks(3), transactions.last().date)
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
