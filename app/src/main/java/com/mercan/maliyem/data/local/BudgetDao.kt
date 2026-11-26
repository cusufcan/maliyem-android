package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

data class BudgetExpense(val categoryId: Long, val amountMinor: Long)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM overall_budget WHERE id = 1")
    fun observeOverall(): Flow<OverallBudgetEntity?>

    @Query("SELECT * FROM budgets ORDER BY id")
    suspend fun getAll(): List<BudgetEntity>

    @Query("SELECT * FROM overall_budget WHERE id = 1")
    suspend fun getOverall(): OverallBudgetEntity?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun getForCategory(categoryId: Long): BudgetEntity?

    @Query("SELECT categoryId, amountMinor FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :from AND :to")
    suspend fun expenses(from: LocalDate, to: LocalDate): List<BudgetExpense>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Query("SELECT type FROM categories WHERE id = :categoryId")
    suspend fun categoryType(categoryId: Long): TransactionType?

    @Upsert
    suspend fun save(budget: BudgetEntity)

    @Upsert
    suspend fun saveOverall(budget: OverallBudgetEntity)

    @Insert
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Query("DELETE FROM overall_budget")
    suspend fun deleteOverall()
}
