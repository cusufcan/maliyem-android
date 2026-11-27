package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

// The ledger is validated in ID order. Keep that order for SUM even if the query plan changes.
private const val SELECT_BALANCES = """
    SELECT g.*, COALESCE((
        SELECT SUM(amountMinor) FROM (
            SELECT amountMinor FROM goal_entries WHERE goalId = g.id ORDER BY id LIMIT -1
        )
    ), 0) AS savedMinor FROM savings_goals AS g
"""

@Dao
interface SavingsGoalDao {
    @Query(SELECT_BALANCES + " ORDER BY g.createdAt DESC, g.id DESC")
    fun observeBalances(): Flow<List<GoalBalance>>

    @Transaction
    @Query(SELECT_BALANCES + " WHERE g.id = :id")
    fun observeDetail(id: Long): Flow<GoalDetail?>

    @Query(SELECT_BALANCES + " WHERE g.id = :id")
    suspend fun getBalance(id: Long): GoalBalance?

    @Insert
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Insert
    suspend fun insert(entry: GoalEntryEntity): Long

    @Query("UPDATE savings_goals SET name = :name, targetMinor = :target, deadline = :deadline, icon = :icon WHERE id = :id")
    suspend fun edit(id: Long, name: String, target: Long, deadline: LocalDate?, icon: String): Int

    @Query("UPDATE savings_goals SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean): Int

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM savings_goals ORDER BY id")
    suspend fun getAll(): List<SavingsGoalEntity>

    @Query("SELECT * FROM goal_entries ORDER BY id")
    suspend fun getEntries(): List<GoalEntryEntity>

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(goals: List<SavingsGoalEntity>)

    @Insert
    suspend fun insertEntries(entries: List<GoalEntryEntity>)
}
