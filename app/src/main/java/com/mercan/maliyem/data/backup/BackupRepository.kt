package com.mercan.maliyem.data.backup

import androidx.room.withTransaction
import com.mercan.maliyem.data.local.AccountDao
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.BudgetDao
import com.mercan.maliyem.data.local.BudgetEntity
import com.mercan.maliyem.data.local.CategoryDao
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.GoalEntryEntity
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.OverallBudgetEntity
import com.mercan.maliyem.data.local.RecurringRuleDao
import com.mercan.maliyem.data.local.RecurringRuleEntity
import com.mercan.maliyem.data.local.SavingsGoalDao
import com.mercan.maliyem.data.local.SavingsGoalEntity
import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransferDao
import com.mercan.maliyem.data.local.TransferEntity
import com.mercan.maliyem.widget.WidgetUpdater
import java.time.LocalDate
import javax.inject.Inject

interface BackupRepository {
    suspend fun snapshot(): BackupFile

    suspend fun replaceAll(validated: BackupDecodeResult.Valid)
}

class RoomBackupRepository @Inject constructor(
    private val db: MaliyemDatabase,
    private val categories: CategoryDao,
    private val transactions: TransactionDao,
    private val accounts: AccountDao,
    private val transfers: TransferDao,
    private val recurringRules: RecurringRuleDao,
    private val budgets: BudgetDao,
    private val goals: SavingsGoalDao,
    private val widgetUpdater: WidgetUpdater,
) : BackupRepository {
    /** Tablolar aynı anlık görüntüden okunur; dışa aktarımda bağlantılar korunur. */
    override suspend fun snapshot(): BackupFile = db.withTransaction {
        BackupFile(
            format = BackupFile.FORMAT,
            version = BackupFile.VERSION,
            exportedAt = System.currentTimeMillis(),
            categories = categories.getAll().map { BackupCategory(it.id, it.type, it.key, it.name, it.icon, it.sortOrder) },
            transactions = transactions.getAll().map {
                BackupTransaction(
                    it.id, it.type, it.amountMinor, it.categoryId, it.date.toString(), it.note,
                    it.createdAt, it.accountId, it.recurringRuleId,
                )
            },
            accounts = accounts.getAll().map {
                BackupAccount(it.id, it.key, it.name, it.icon, it.sortOrder, it.initialBalanceMinor, it.archived)
            },
            recurringRules = recurringRules.getAll().map {
                BackupRecurringRule(
                    it.id, it.type, it.amountMinor, it.categoryId, it.accountId, it.note, it.frequency,
                    it.startDate.toString(), it.nextDate.toString(), it.endDate?.toString(), it.paused, it.createdAt,
                )
            },
            budgets = budgets.getAll().map { BackupBudget(it.id, it.categoryId, it.limitMinor, it.createdAt) },
            overallBudgetMinor = budgets.getOverall()?.limitMinor,
            savingsGoals = goals.getAll().map {
                BackupSavingsGoal(it.id, it.name, it.targetMinor, it.deadline?.toString(), it.icon, it.createdAt, it.archived)
            },
            goalEntries = goals.getEntries().map {
                BackupGoalEntry(it.id, it.goalId, it.amountMinor, it.date.toString(), it.note)
            },
            transfers = transfers.getAll().map {
                BackupTransfer(it.id, it.fromAccountId, it.toAccountId, it.amountMinor, it.date.toString(), it.note, it.createdAt)
            },
        )
    }

    /** Dış anahtar sırasıyla silip ekler; yazımlardan biri başarısız olursa tamamı geri alınır. */
    override suspend fun replaceAll(validated: BackupDecodeResult.Valid) {
        val backup = validated.backup
        val categoryRows = backup.categories.map { CategoryEntity(it.id, it.type, it.key, it.name, it.icon, it.sortOrder) }
        val transactionRows = backup.transactions.map {
            TransactionEntity(
                it.id, it.type, it.amountMinor, it.categoryId, LocalDate.parse(it.date), it.note,
                it.createdAt, it.accountId, it.recurringRuleId,
            )
        }
        val accountRows = backup.accounts.map {
            AccountEntity(it.id, it.key, it.name, it.icon, it.sortOrder, it.initialBalanceMinor, it.archived)
        }
        val transferRows = backup.transfers.map {
            TransferEntity(it.id, it.fromAccountId, it.toAccountId, it.amountMinor, LocalDate.parse(it.date), it.note, it.createdAt)
        }
        val ruleRows = backup.recurringRules.map {
            RecurringRuleEntity(
                it.id, it.type, it.amountMinor, it.categoryId, it.accountId, it.note, it.frequency,
                LocalDate.parse(it.startDate), LocalDate.parse(it.nextDate), it.endDate?.let(LocalDate::parse),
                it.paused, it.createdAt,
            )
        }
        val budgetRows = backup.budgets.map { BudgetEntity(it.id, it.categoryId, it.limitMinor, it.createdAt) }
        val goalRows = backup.savingsGoals.map {
            SavingsGoalEntity(it.id, it.name, it.targetMinor, it.deadline?.let(LocalDate::parse), it.icon, it.createdAt, it.archived)
        }
        val goalEntryRows = backup.goalEntries.map {
            GoalEntryEntity(it.id, it.goalId, it.amountMinor, LocalDate.parse(it.date), it.note)
        }
        db.withTransaction {
            goals.deleteAll()
            budgets.deleteAll()
            budgets.deleteOverall()
            transfers.deleteAll()
            transactions.deleteAll()
            recurringRules.deleteAll()
            accounts.deleteAll()
            categories.deleteAll()
            categories.insertAll(categoryRows)
            budgets.insertAll(budgetRows)
            backup.overallBudgetMinor?.let { budgets.saveOverall(OverallBudgetEntity(limitMinor = it)) }
            accounts.insertAll(accountRows)
            recurringRules.insertAll(ruleRows)
            transactions.insertAll(transactionRows)
            transfers.insertAll(transferRows)
            goals.insertAll(goalRows)
            goals.insertEntries(goalEntryRows)
            val sqlite = db.openHelper.writableDatabase
            // AUTOINCREMENT keeps previous high-water marks even after deleting all rows.
            val automaticTables = listOf(
                "categories", "transactions", "accounts", "transfers", "recurring_rules", "budgets", "savings_goals", "goal_entries",
            )
            for (table in automaticTables) {
                sqlite.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf(table))
                sqlite.execSQL(
                    "INSERT INTO sqlite_sequence (name, seq) SELECT ?, COALESCE(MAX(id), 0) FROM `$table`",
                    arrayOf(table),
                )
            }
        }
        widgetUpdater.refresh()
    }
}
