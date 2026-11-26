package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Kategori başına kayıt sayısı (Kategoriler ekranı). */
data class CategoryCount(val categoryId: Long, val count: Int)

@Dao
interface TransactionDao {
    /** [from] ve [to] dahil; ay görünümü için ayın ilk ve son günü verilir. */
    @Transaction
    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC, id DESC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TransactionWithCategory>>

    /** Kaydı olan günler; ay gezintisi boş ayları atlasın diye. */
    @Query("SELECT DISTINCT date FROM transactions")
    fun observeDates(): Flow<List<LocalDate>>

    /** Ayrıntı ekranının izleyeceği ayı belirler; kayıt silinince null yayar. */
    @Query("SELECT date FROM transactions WHERE id = :id")
    fun observeDate(id: Long): Flow<LocalDate?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT categoryId, COUNT(*) AS count FROM transactions GROUP BY categoryId")
    fun observeCounts(): Flow<List<CategoryCount>>

    /** Kimliği 0 olmayan kayıt aynı kimlikle geri yazılır ("Geri al"). */
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Update
    suspend fun update(transaction: TransactionEntity): Int

    @Transaction
    suspend fun deleteAndGet(id: Long): TransactionEntity? {
        val transaction = getById(id) ?: return null
        deleteById(id)
        return transaction
    }

    @Query("SELECT id FROM categories WHERE type = :type AND (id = :id OR `key` = :fallbackKey) ORDER BY (id = :id) DESC LIMIT 1")
    suspend fun categoryForRestore(id: Long, type: TransactionType, fallbackKey: String): Long?

    @Transaction
    suspend fun restore(transaction: TransactionEntity, fallbackKey: String): Boolean {
        val categoryId = categoryForRestore(transaction.categoryId, transaction.type, fallbackKey) ?: return false
        insert(transaction.copy(categoryId = categoryId))
        return true
    }
}
