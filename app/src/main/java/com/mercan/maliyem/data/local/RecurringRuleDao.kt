package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY nextDate, id")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    fun observe(id: Long): Flow<RecurringRuleEntity?>

    @Query("SELECT COUNT(*) FROM recurring_rules")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rules ORDER BY id")
    suspend fun getAll(): List<RecurringRuleEntity>

    @Query("""
        SELECT id FROM recurring_rules
        WHERE paused = 0 AND nextDate <= :today AND (endDate IS NULL OR nextDate <= endDate)
        ORDER BY id
    """)
    suspend fun dueIds(today: LocalDate): List<Long>

    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Insert
    suspend fun insertAll(rules: List<RecurringRuleEntity>)

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET nextDate = :nextDate WHERE id = :id")
    suspend fun advance(id: Long, nextDate: LocalDate)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}
