package com.mercan.maliyem.data.repository

import androidx.room.withTransaction
import com.mercan.maliyem.data.local.GoalEntryEntity
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.SavingsGoalDao
import com.mercan.maliyem.data.local.SavingsGoalEntity
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class GoalWriteResult { SAVED, INVALID, UNAVAILABLE, OVERDRAWN, LIMIT_EXCEEDED }

class SavingsGoalRepository @Inject constructor(
    private val db: MaliyemDatabase,
    private val goals: SavingsGoalDao,
    private val clock: Clock,
) {
    fun observeBalances() = goals.observeBalances()
    fun observeDetail(id: Long) = goals.observeDetail(id)

    suspend fun save(id: Long?, name: String, target: Long, deadline: LocalDate?, icon: String): GoalWriteResult {
        if (name.isBlank() || target <= 0 || icon.isBlank()) return GoalWriteResult.INVALID
        if (id == null) {
            goals.insert(SavingsGoalEntity(
                name = name.trim(), targetMinor = target, deadline = deadline, icon = icon, createdAt = clock.millis(),
            ))
            return GoalWriteResult.SAVED
        }
        return resultOf(goals.edit(id, name.trim(), target, deadline, icon))
    }

    suspend fun setArchived(id: Long, archived: Boolean) = resultOf(goals.setArchived(id, archived))
    suspend fun delete(id: Long) = resultOf(goals.delete(id))

    suspend fun addEntry(id: Long, amount: Long, date: LocalDate, note: String): GoalWriteResult = db.withTransaction {
        if (amount == 0L || date > LocalDate.now(clock)) return@withTransaction GoalWriteResult.INVALID
        val balance = goals.getBalance(id)?.takeUnless { it.goal.archived }
            ?: return@withTransaction GoalWriteResult.UNAVAILABLE
        if (amount < -balance.savedMinor) return@withTransaction GoalWriteResult.OVERDRAWN
        if (amount > Long.MAX_VALUE - balance.savedMinor) return@withTransaction GoalWriteResult.LIMIT_EXCEEDED
        goals.insert(GoalEntryEntity(goalId = id, amountMinor = amount, date = date, note = note.trim().ifEmpty { null }))
        GoalWriteResult.SAVED
    }

    private fun resultOf(updated: Int) = if (updated == 1) GoalWriteResult.SAVED else GoalWriteResult.UNAVAILABLE
}
