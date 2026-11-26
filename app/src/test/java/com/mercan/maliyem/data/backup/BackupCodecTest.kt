package com.mercan.maliyem.data.backup

import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val expense = BackupCategory(1, EXPENSE, key = "groceries", icon = "cart", sortOrder = 0)
    private val income = BackupCategory(5, INCOME, name = "Çeviri işi", icon = "laptop", sortOrder = 0)
    private val otherExpense = BackupCategory(2, EXPENSE, DefaultCategories.OTHER_EXPENSE, icon = "dots", sortOrder = 1)
    private val otherIncome = BackupCategory(3, INCOME, DefaultCategories.OTHER_INCOME, icon = "dots", sortOrder = 1)
    private val transaction = BackupTransaction(27, EXPENSE, 12_550, 1, "2026-11-26", "Pazar, sebze", 1_795_651_200_000)
    private val backup = BackupFile(
        BackupFile.FORMAT, 1, 1_795_651_200_000,
        listOf(expense, income, otherExpense, otherIncome),
        listOf(transaction),
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

    @Test fun `v1 ignores extra fields and accepts absent nullable fields`() {
        val content = """
            {
              "format":"maliyem-backup","version":1,"exportedAt":123,
              "categories":[{"id":5,"type":"INCOME","name":"Çeviri işi","icon":"laptop","sortOrder":0,"color":"purple"}],
              "transactions":[{"id":9,"type":"INCOME","amountMinor":12550,"categoryId":5,"date":"2026-11-26","createdAt":456}],
              "budgets":[{"amountMinor":100000}]
            }
        """.trimIndent()
        val result = BackupCodec.decode(content) as BackupDecodeResult.Valid

        assertEquals(1, result.categoryCount)
        assertEquals(income, result.backup.categories.first())
        assertEquals(BackupTransaction(9, INCOME, 12_550, 5, "2026-11-26", null, 456), result.backup.transactions.single())
    }

    @Test fun `rejects malformed JSON and absent tables`() {
        assertEquals(BackupDecodeResult.Invalid, BackupCodec.decode("{"))
        assertEquals(
            BackupDecodeResult.Invalid,
            BackupCodec.decode("""{"format":"maliyem-backup","version":1,"exportedAt":123,"categories":[]}"""),
        )
    }

    private fun assertInvalid(file: BackupFile) {
        assertEquals(BackupDecodeResult.Invalid, BackupCodec.decode(BackupCodec.encode(file)))
    }
}
