package com.mercan.maliyem.data.backup

import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.RecurringFrequency.MONTHLY
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val expense = BackupCategory(1, EXPENSE, key = "groceries", icon = "cart", sortOrder = 0)
    private val income = BackupCategory(5, INCOME, name = "Çeviri işi", icon = "laptop", sortOrder = 0)
    private val otherExpense = BackupCategory(2, EXPENSE, DefaultCategories.OTHER_EXPENSE, icon = "dots", sortOrder = 1)
    private val otherIncome = BackupCategory(3, INCOME, DefaultCategories.OTHER_INCOME, icon = "dots", sortOrder = 1)
    private val transaction = BackupTransaction(27, EXPENSE, 12_550, 1, "2026-11-26", "Pazar, sebze", 1_795_651_200_000, accountId = 1)
    private val cash = BackupAccount(1, key = "cash", icon = "wallet", sortOrder = 0)
    private val card = BackupAccount(2, name = "Card", icon = "card", sortOrder = 1, initialBalanceMinor = -100, archived = true)
    private val transfer = BackupTransfer(27, 1, 2, 850, "2026-11-27", "Top up", 123)
    private val backup = BackupFile(
        BackupFile.FORMAT, BackupFile.VERSION, 1_795_651_200_000,
        listOf(expense, income, otherExpense, otherIncome),
        listOf(transaction),
        listOf(cash, card),
        listOf(transfer),
    )

    @Test fun `round trip keeps all fields and ids`() {
        val encoded = BackupCodec.encode(backup)
        val result = BackupCodec.decode(encoded) as BackupDecodeResult.Valid

        assertTrue(encoded.contains("\"type\":\"EXPENSE\""))
        assertTrue(encoded.contains("\"name\":null"))
        assertEquals(backup, result.backup)
        assertEquals(4, result.categoryCount)
    }

    @Test fun `rejects bad format and unsupported versions`() {
        assertInvalid(backup.copy(format = "other-backup"))
        assertInvalid(backup.copy(version = 0))
        assertInvalid(backup.copy(version = BackupFile.VERSION + 1))
    }

    @Test fun `rejects missing category and category type mismatch`() {
        assertInvalid(backup.copy(transactions = listOf(transaction.copy(categoryId = 4))))
        assertInvalid(backup.copy(transactions = listOf(transaction.copy(categoryId = income.id))))
    }

    @Test fun `rejects non positive amounts and invalid dates`() {
        assertInvalid(backup.copy(transactions = listOf(transaction.copy(amountMinor = 0))))
        assertInvalid(backup.copy(transactions = listOf(transaction.copy(amountMinor = -100))))
        assertInvalid(backup.copy(transactions = listOf(transaction.copy(date = "2026-02-30"))))
    }

    @Test fun `rejects duplicate ids and keys that Room cannot insert`() {
        assertInvalid(backup.copy(categories = backup.categories + expense.copy(key = "dining")))
        assertInvalid(backup.copy(categories = backup.categories + expense.copy(id = 15)))
        assertInvalid(backup.copy(transactions = listOf(transaction, transaction)))
        assertInvalid(backup.copy(transactions = listOf(transaction.copy(id = 0))))
    }

    @Test fun `adds missing fallbacks without changing file counts or ids`() {
        val result = BackupCodec.decode(BackupCodec.encode(backup.copy(categories = listOf(expense, income))))
            as BackupDecodeResult.Valid

        assertEquals(2, result.categoryCount)
        assertEquals(listOf(expense, income), result.backup.categories.take(2))
        assertEquals(listOf(otherExpense.copy(sortOrder = 0), otherIncome.copy(sortOrder = 0)), result.backup.categories.drop(2))
        assertEquals(listOf(transaction), result.backup.transactions)
    }

    @Test fun `rejects fallback with the wrong type`() {
        val categories = listOf(expense, income, otherExpense.copy(type = INCOME), otherIncome)
        assertInvalid(backup.copy(categories = categories))
    }

    @Test fun `v1 is upgraded with one default account and preserved transactions`() {
        val content = """
            {
              "format":"maliyem-backup","version":1,"exportedAt":123,
              "categories":[{"id":5,"type":"INCOME","name":"Çeviri işi","icon":"laptop","sortOrder":0,"color":"purple"}],
              "transactions":[{"id":9,"type":"INCOME","amountMinor":12550,"categoryId":5,"date":"2026-11-26","createdAt":456}],
              "budgets":[{"amountMinor":100000}]
            }
        """.trimIndent()
        val result = BackupCodec.decode(content) as BackupDecodeResult.Valid

        assertEquals(BackupFile.VERSION, result.backup.version)
        assertTrue(result.backup.recurringRules.isEmpty())
        assertTrue(result.backup.budgets.isEmpty())
        assertNull(result.backup.overallBudgetMinor)
        assertEquals(listOf(cash), result.backup.accounts)
        assertTrue(result.backup.transfers.isEmpty())
        assertEquals(1, result.categoryCount)
        assertEquals(income, result.backup.categories.first())
        assertEquals(BackupTransaction(9, INCOME, 12_550, 5, "2026-11-26", null, 456, accountId = 1), result.backup.transactions.single())
    }

    @Test fun `rejects missing account links and invalid transfers`() {
        assertInvalid(backup.copy(transactions = listOf(transaction.copy(accountId = 0))))
        assertInvalid(backup.copy(accounts = listOf(cash)))
        assertInvalid(backup.copy(accounts = listOf(cash.copy(archived = true), card)))
        assertInvalid(backup.copy(transfers = listOf(transfer.copy(toAccountId = transfer.fromAccountId))))
        assertInvalid(backup.copy(transfers = listOf(transfer.copy(amountMinor = 0))))
        assertInvalid(backup.copy(transfers = listOf(transfer.copy(amountMinor = -1))))
        assertInvalid(backup.copy(transfers = listOf(transfer.copy(date = "2026-02-30"))))
        assertInvalid(backup.copy(transfers = listOf(transfer, transfer)))
    }

    @Test fun `v2 without recurrence fields keeps accounts transfers and transactions`() {
        val content = """
            {
              "format":"maliyem-backup","version":2,"exportedAt":123,
              "categories":[{"id":1,"type":"EXPENSE","name":"Market","icon":"cart","sortOrder":0}],
              "transactions":[{"id":8,"type":"EXPENSE","amountMinor":100,"categoryId":1,"date":"2026-11-26","createdAt":456,"accountId":2}],
              "accounts":[{"id":1,"key":"cash","icon":"wallet","sortOrder":0},{"id":2,"name":"Card","icon":"card","sortOrder":1}],
              "transfers":[{"id":27,"fromAccountId":1,"toAccountId":2,"amountMinor":850,"date":"2026-11-27","note":"Top up","createdAt":123}]
            }
        """.trimIndent()
        val result = BackupCodec.decode(content) as BackupDecodeResult.Valid
        assertEquals(BackupFile.VERSION, result.backup.version)
        assertEquals(listOf(cash, card.copy(initialBalanceMinor = 0, archived = false)), result.backup.accounts)
        assertEquals(listOf(transfer), result.backup.transfers)
        assertEquals(
            listOf(BackupTransaction(8, EXPENSE, 100, 1, "2026-11-26", null, 456, accountId = 2)),
            result.backup.transactions,
        )
        assertTrue(result.backup.recurringRules.isEmpty())
        assertTrue(result.backup.budgets.isEmpty())
        assertNull(result.backup.overallBudgetMinor)
    }

    private val rule = BackupRecurringRule(
        4, EXPENSE, 100, expense.id, card.id, "Kira", MONTHLY,
        "2026-01-31", "2026-02-28", "2026-02-15", paused = true, createdAt = 123,
    )

    @Test fun `v3 round trip keeps paused rules cursor and generated transaction links`() {
        val linked = backup.copy(
            version = 3,
            recurringRules = listOf(rule),
            transactions = listOf(transaction.copy(recurringRuleId = rule.id)),
        )
        val v3 = BackupCodec.encode(linked)
            .replace(",\"budgets\":[]", "")
            .replace(",\"overallBudgetMinor\":null", "")
        val restored = BackupCodec.decode(v3) as BackupDecodeResult.Valid
        assertEquals(linked.copy(version = BackupFile.VERSION), restored.backup)
    }

    @Test fun `v4 keeps budgets and starts without goals`() {
        val withBudgets = backup.copy(
            budgets = listOf(BackupBudget(9, expense.id, 40_000, 125)),
            overallBudgetMinor = 100_000,
        )
        val legacy = BackupCodec.encode(withBudgets.copy(version = 4))
            .replace("\"savingsGoals\":[]", "\"savingsGoals\":true")
            .replace(",\"goalEntries\":[]", "")
        val restored = BackupCodec.decode(legacy) as BackupDecodeResult.Valid
        assertEquals(withBudgets, restored.backup)
    }

    private val goal = BackupSavingsGoal(11, "Tatil", 25_000, "2027-02-15", "plane", 124, archived = true)
    private val deposit = BackupGoalEntry(20, goal.id, 18_500, "2026-11-26", "Başlangıç")
    private val withdrawal = BackupGoalEntry(21, goal.id, -3_500, "2026-11-27")

    @Test fun `v5 round trip keeps goals and signed entries regardless of list order`() {
        val file = backup.copy(savingsGoals = listOf(goal), goalEntries = listOf(withdrawal, deposit))
        val restored = BackupCodec.decode(BackupCodec.encode(file)) as BackupDecodeResult.Valid
        assertEquals(file, restored.backup)
    }

    @Test fun `rejects invalid goals and entries before restore`() {
        for (invalid in listOf(
            goal.copy(name = " "), goal.copy(id = 0), goal.copy(targetMinor = 0),
            goal.copy(deadline = "2026-02-30"), goal.copy(icon = ""),
        )) assertInvalid(backup.copy(savingsGoals = listOf(invalid)))
        assertInvalid(backup.copy(savingsGoals = listOf(goal, goal)))

        val file = backup.copy(savingsGoals = listOf(goal))
        for (invalid in listOf(
            withdrawal.copy(amountMinor = -18_501), withdrawal.copy(amountMinor = Long.MAX_VALUE),
            withdrawal.copy(goalId = 99), withdrawal.copy(amountMinor = 0),
            withdrawal.copy(id = deposit.id), withdrawal.copy(date = "2026-02-30"),
        )) assertInvalid(file.copy(goalEntries = listOf(deposit, invalid)))
        assertInvalid(file.copy(goalEntries = listOf(withdrawal)))
    }

    @Test fun `rejects invalid budget limits ids and categories`() {
        val budget = BackupBudget(9, expense.id, 40_000, 125)
        assertInvalid(backup.copy(overallBudgetMinor = 0))
        assertInvalid(backup.copy(overallBudgetMinor = -1))
        assertInvalid(backup.copy(budgets = listOf(budget, budget.copy(categoryId = otherExpense.id))))
        assertInvalid(backup.copy(budgets = listOf(budget, budget.copy(id = 10))))
        for (invalid in listOf(
            budget.copy(id = 0),
            budget.copy(categoryId = 99),
            budget.copy(categoryId = income.id),
            budget.copy(limitMinor = 0),
            budget.copy(limitMinor = -1),
        )) {
            assertInvalid(backup.copy(budgets = listOf(invalid)))
        }
    }

    @Test fun `rejects invalid rules and dangling transaction links`() {
        assertInvalid(backup.copy(transactions = listOf(transaction.copy(recurringRuleId = rule.id))))
        assertInvalid(backup.copy(recurringRules = listOf(rule, rule)))
        for (invalid in listOf(
            rule.copy(id = 0),
            rule.copy(amountMinor = 0),
            rule.copy(categoryId = 99),
            rule.copy(categoryId = income.id),
            rule.copy(accountId = 99),
            rule.copy(startDate = "2026-02-30"),
            rule.copy(nextDate = "2026-02-30"),
            rule.copy(endDate = "2026-02-30"),
            rule.copy(nextDate = "2026-01-30"),
            rule.copy(nextDate = "2026-03-30"),
            rule.copy(endDate = "2026-01-30"),
        )) {
            assertInvalid(backup.copy(recurringRules = listOf(invalid)))
        }
        val unknownFrequency = BackupCodec.encode(backup.copy(recurringRules = listOf(rule)))
            .replace("\"MONTHLY\"", "\"DAILY\"")
        assertEquals(BackupDecodeResult.Invalid, BackupCodec.decode(unknownFrequency))
    }

    @Test fun `rejects malformed JSON and absent tables`() {
        assertEquals(BackupDecodeResult.Invalid, BackupCodec.decode("{"))
        assertEquals(
            BackupDecodeResult.Invalid,
            BackupCodec.decode("""{"format":"maliyem-backup","version":1,"exportedAt":123,"categories":[]}"""),
        )
    }

    @Test fun `accepts 2 to the power of 53 and rejects higher ids in every table and link`() {
        val max = BackupCodec.MAX_ID
        val over = max + 1
        val budget = BackupBudget(9, expense.id, 40_000, 125)
        val atLimit = backup.copy(
            categories = listOf(expense.copy(id = max), income, otherExpense, otherIncome),
            transactions = listOf(transaction.copy(id = max, categoryId = max, recurringRuleId = max)),
            transfers = listOf(transfer.copy(id = max, toAccountId = max)),
            accounts = listOf(cash, card.copy(id = max)),
            recurringRules = listOf(rule.copy(id = max, categoryId = max, accountId = max)),
            budgets = listOf(budget.copy(id = max, categoryId = max)),
            savingsGoals = listOf(goal.copy(id = max)),
            goalEntries = listOf(deposit.copy(id = max, goalId = max)),
        )
        assertEquals(atLimit, (BackupCodec.decode(BackupCodec.encode(atLimit)) as BackupDecodeResult.Valid).backup)
        for (outOfRange in listOf(
            backup.copy(categories = listOf(expense.copy(id = over))),
            backup.copy(transactions = listOf(transaction.copy(id = over))),
            backup.copy(accounts = listOf(cash.copy(id = over))),
            backup.copy(transfers = listOf(transfer.copy(id = over))),
            backup.copy(recurringRules = listOf(rule.copy(id = over))),
            backup.copy(budgets = listOf(budget.copy(id = over))),
            backup.copy(savingsGoals = listOf(goal.copy(id = over))),
            backup.copy(goalEntries = listOf(deposit.copy(id = over))),
            backup.copy(goalEntries = listOf(deposit.copy(goalId = over))),
            backup.copy(transactions = listOf(transaction.copy(recurringRuleId = Long.MAX_VALUE))),
            backup.copy(transfers = listOf(transfer.copy(toAccountId = over))),
        )) {
            assertEquals(BackupDecodeResult.IdOutOfRange, BackupCodec.decode(BackupCodec.encode(outOfRange)))
        }
    }

    private fun assertInvalid(file: BackupFile) {
        assertEquals(BackupDecodeResult.Invalid, BackupCodec.decode(BackupCodec.encode(file)))
    }
}
