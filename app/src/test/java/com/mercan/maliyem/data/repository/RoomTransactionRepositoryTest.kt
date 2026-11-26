package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.CategoryCount
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomTransactionRepositoryTest {
    private class RangeDao : TransactionDao {
        var range: Pair<LocalDate, LocalDate>? = null
        override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TransactionWithCategory>> {
            range = from to to
            return emptyFlow()
        }
        override fun observeFiltered(
            from: LocalDate,
            to: LocalDate,
            type: TransactionType?,
            categoryIds: List<Long>,
            accountIds: List<Long>,
            allCategories: Boolean,
            allAccounts: Boolean,
        ): Flow<List<TransactionWithCategory>> = emptyFlow()

        var dateEmissions: List<List<LocalDate>> = emptyList()
        override fun observeDates() = flowOf(*dateEmissions.toTypedArray())
        var inserted: TransactionEntity? = null
        var deleted: Long? = null
        override suspend fun insert(transaction: TransactionEntity): Long { inserted = transaction; return 7L }
        override suspend fun insertAll(transactions: List<TransactionEntity>) = Unit
        override suspend fun deleteById(id: Long) { deleted = id }
        override suspend fun deleteAll() = Unit
        var updated: TransactionEntity? = null
        override suspend fun update(transaction: TransactionEntity): Int {
            updated = transaction
            return 1
        }
        var existing: TransactionEntity? = null
        var ruleId: Long? = null
        override suspend fun existingRecurringRuleId(id: Long) = id.takeIf { it == ruleId }
        var restoredCategory: Long? = 9L
        var requestedFallback: String? = null
        override suspend fun categoryForRestore(id: Long, type: TransactionType, fallbackKey: String): Long? {
            requestedFallback = fallbackKey
            return restoredCategory
        }
        override fun observeDate(id: Long): Flow<LocalDate?> = emptyFlow()
        override suspend fun getById(id: Long): TransactionEntity? = existing?.takeIf { it.id == id }
        override suspend fun getAll(): List<TransactionEntity> = listOfNotNull(existing)
        override fun observeCounts(): Flow<List<CategoryCount>> = flowOf(emptyList())
    }

    private fun rangeOf(month: YearMonth) = RangeDao().also { RoomTransactionRepository(it).observeMonth(month) }.range

    @Test fun `month bounds include first and last day`() {
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30), rangeOf(YearMonth.of(2026, 11)))
        assertEquals(LocalDate.of(2026, 12, 1) to LocalDate.of(2026, 12, 31), rangeOf(YearMonth.of(2026, 12)))
    }

    @Test fun `february follows leap years`() {
        assertEquals(LocalDate.of(2027, 2, 28), rangeOf(YearMonth.of(2027, 2))!!.second)
        assertEquals(LocalDate.of(2028, 2, 29), rangeOf(YearMonth.of(2028, 2))!!.second)
    }

    @Test fun `dates collapse into distinct months`() = runBlocking {
        val dao = RangeDao().apply {
            dateEmissions = listOf(listOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 28), LocalDate.of(2026, 9, 1)))
        }
        assertEquals(setOf(YearMonth.of(2026, 11), YearMonth.of(2026, 9)), RoomTransactionRepository(dao).observeMonthsWithData().first())
    }

    @Test fun `add and delete reach the dao`() = runBlocking {
        val dao = RangeDao()
        val repo = RoomTransactionRepository(dao)
        val entity = TransactionEntity(type = TransactionType.EXPENSE, amountMinor = 100, categoryId = 1, date = LocalDate.of(2026, 11, 26), createdAt = 0, accountId = 1)
        assertEquals(7L, repo.add(entity))
        assertEquals(entity, dao.inserted)
        repo.delete(7)
        assertEquals(7L, dao.deleted)
    }

    @Test fun `new dates in an existing month do not emit another month set`() = runBlocking {
        val first = LocalDate.of(2026, 12, 31)
        val second = LocalDate.of(2027, 1, 1)
        val dao = RangeDao().apply {
            dateEmissions = listOf(listOf(first), listOf(first, first.minusDays(1)), listOf(first, second))
        }
        val result = RoomTransactionRepository(dao).observeMonthsWithData().toList()
        assertEquals(listOf(setOf(YearMonth.from(first)), setOf(YearMonth.from(first), YearMonth.from(second))), result)
    }

    @Test fun `delete returns the snapshot and undo can restore into the fallback category`() = runBlocking {
        val entry = TransactionEntity(5, TransactionType.EXPENSE, 101, 2, LocalDate.of(2026, 11, 1), "note", 4, accountId = 1)
        val dao = RangeDao().apply { existing = entry }
        val repo = RoomTransactionRepository(dao)
        assertNull(repo.deleteAndGet(99))
        assertNull(dao.deleted)
        assertEquals(entry, repo.deleteAndGet(entry.id))
        assertEquals(entry.id, dao.deleted)
        assertTrue(repo.restore(entry))
        assertEquals(DefaultCategories.OTHER_EXPENSE, dao.requestedFallback)
        assertEquals(entry.copy(categoryId = 9), dao.inserted)
        dao.existing = null
        assertFalse(repo.update(entry))
    }

    @Test fun `editing a generated transaction keeps its current rule link`() = runBlocking {
        val entry = TransactionEntity(5, TransactionType.EXPENSE, 101, 2, LocalDate.of(2026, 11, 1), null, 4, accountId = 1, recurringRuleId = 8)
        val dao = RangeDao().apply { existing = entry }
        val repo = RoomTransactionRepository(dao)
        val draft = entry.copy(amountMinor = 500, recurringRuleId = null)

        assertTrue(repo.update(draft))
        assertEquals(draft.copy(recurringRuleId = 8), dao.updated)

        dao.existing = entry.copy(recurringRuleId = null)
        assertTrue(repo.update(draft.copy(recurringRuleId = 8)))
        assertEquals(draft, dao.updated)
    }

    @Test fun `undo preserves an existing rule link and drops a deleted one`() = runBlocking {
        val entry = TransactionEntity(5, TransactionType.EXPENSE, 101, 2, LocalDate.of(2026, 11, 1), null, 4, accountId = 1, recurringRuleId = 8)
        val dao = RangeDao().apply { ruleId = 8 }
        val repo = RoomTransactionRepository(dao)
        assertTrue(repo.restore(entry))
        assertEquals(entry.copy(categoryId = 9), dao.inserted)
        dao.ruleId = null
        assertTrue(repo.restore(entry))
        assertEquals(entry.copy(categoryId = 9, recurringRuleId = null), dao.inserted)
    }

    @Test fun `undo does not insert when neither category exists`() = runBlocking {
        val entry = TransactionEntity(5, TransactionType.INCOME, 101, 2, LocalDate.of(2026, 11, 1), null, 4, accountId = 1)
        val dao = RangeDao().apply { restoredCategory = null }
        assertFalse(RoomTransactionRepository(dao).restore(entry))
        assertEquals(DefaultCategories.OTHER_INCOME, dao.requestedFallback)
        assertNull(dao.inserted)
    }
}
