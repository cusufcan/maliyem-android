package com.mercan.maliyem.data.repository

import androidx.room.Room
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.repository.GoalWriteResult.INVALID
import com.mercan.maliyem.data.repository.GoalWriteResult.LIMIT_EXCEEDED
import com.mercan.maliyem.data.repository.GoalWriteResult.OVERDRAWN
import com.mercan.maliyem.data.repository.GoalWriteResult.SAVED
import com.mercan.maliyem.data.repository.GoalWriteResult.UNAVAILABLE
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class SavingsGoalRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MaliyemDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = db.savingsGoalDao()
    private val today = LocalDate.of(2026, 11, 27)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repository = SavingsGoalRepository(db, dao, clock)

    @After fun closeDatabase() = db.close()

    @Test fun `goal lifecycle maintains a separate history and cascades on delete`() = runBlocking {
        db.accountDao().insertAll(listOf(DefaultAccounts.cash().copy(initialBalanceMinor = 500)))
        val accounts = AccountRepository(db, db.accountDao())
        val balances = accounts.observeBalances().first()

        assertEquals(INVALID, repository.save(null, "Trip", 0, null, "plane"))
        assertEquals(SAVED, repository.save(null, " Trip ", 1_000, null, "plane"))
        val goal = dao.getAll().single()
        assertEquals("Trip", goal.name)
        assertEquals(clock.millis(), goal.createdAt)
        assertEquals(SAVED, repository.save(goal.id, "Holiday", 1_100, today, "gift"))
        assertEquals(goal.copy(name = "Holiday", targetMinor = 1_100, deadline = today, icon = "gift"), dao.getAll().single())

        assertEquals(INVALID, repository.addEntry(goal.id, 0, today, ""))
        assertEquals(INVALID, repository.addEntry(goal.id, 500, today.plusDays(1), ""))
        assertEquals(SAVED, repository.addEntry(goal.id, 800, today, " First "))
        assertEquals(SAVED, repository.addEntry(goal.id, -150, today.minusDays(1), ""))
        assertEquals(OVERDRAWN, repository.addEntry(goal.id, -651, today, ""))
        assertEquals(SAVED, repository.setArchived(goal.id, true))
        assertEquals(UNAVAILABLE, repository.addEntry(goal.id, 100, today, ""))
        val archived = checkNotNull(repository.observeDetail(goal.id).first())
        assertTrue(archived.balance.goal.archived)
        assertEquals(650L, archived.balance.savedMinor)
        assertEquals(listOf("First", null), archived.entries.sortedBy { it.id }.map { it.note })
        assertEquals(SAVED, repository.setArchived(goal.id, false))
        assertEquals(SAVED, repository.addEntry(goal.id, 100, today, ""))
        assertEquals(750L, repository.observeBalances().first().single().savedMinor)
        assertEquals(balances, accounts.observeBalances().first())

        assertEquals(SAVED, repository.delete(goal.id))
        assertNull(repository.observeDetail(goal.id).first())
        assertTrue(dao.getEntries().isEmpty())
        assertEquals(UNAVAILABLE, repository.save(goal.id, "Missing", 1, null, "plane"))
    }

    @Test fun `concurrent withdrawals serialize and the ordered sum stays within Long`() = runBlocking {
        repository.save(null, "Trip", Long.MAX_VALUE, null, "plane")
        val id = dao.getAll().single().id
        assertEquals(SAVED, repository.addEntry(id, Long.MAX_VALUE, today, ""))
        assertEquals(LIMIT_EXCEEDED, repository.addEntry(id, 1, today, ""))
        val results = List(2) {
            async { repository.addEntry(id, -Long.MAX_VALUE, today, "") }
        }.awaitAll()
        assertEquals(setOf(SAVED, OVERDRAWN), results.toSet())
        assertEquals(SAVED, repository.addEntry(id, Long.MAX_VALUE, today, ""))
        assertEquals(Long.MAX_VALUE, dao.getBalance(id)?.savedMinor)
    }
}
