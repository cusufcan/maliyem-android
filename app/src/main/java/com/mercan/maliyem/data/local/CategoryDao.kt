package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories WHERE type = :type")
    suspend fun nextSortOrder(type: TransactionType): Int

    @Transaction
    suspend fun append(category: CategoryEntity): Long =
        insert(category.copy(sortOrder = nextSortOrder(category.type)))

    @Query("SELECT id FROM categories WHERE `key` = :key")
    suspend fun idOfKey(key: String): Long?

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveTransactions(from: Long, to: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Kayıtlar önce [fallbackKey] kategorisine taşınır, sonra kategori silinir; ikisi tek işlemde (ADR-0011). */
    @Transaction
    suspend fun deleteMovingTransactions(id: Long, fallbackKey: String) {
        val fallback = idOfKey(fallbackKey) ?: return
        if (fallback == id) return
        moveTransactions(id, fallback)
        deleteById(id)
    }
}
