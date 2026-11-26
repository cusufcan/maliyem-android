package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Delete
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
    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TransactionWithCategory>>

    /** Kaydı olan günler; ay gezintisi boş ayları atlasın diye. */
    @Query("SELECT DISTINCT date FROM transactions")
    fun observeDates(): Flow<List<LocalDate>>

    /** Kayıt silinince null yayar; ayrıntı ekranı o an geri döner. */
    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionWithCategory?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT categoryId, COUNT(*) AS count FROM transactions GROUP BY categoryId")
    fun observeCounts(): Flow<List<CategoryCount>>

    /** Kimliği 0 olmayan kayıt aynı kimlikle geri yazılır ("Geri al"). */
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}
