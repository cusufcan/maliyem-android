package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers WHERE date BETWEEN :from AND :to")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TransferEntity>>

    /** Seçili hesap transferin göndereni veya alıcısı olabilir. */
    @Query("""
        SELECT * FROM transfers
        WHERE date BETWEEN :from AND :to
          AND (:allAccounts OR fromAccountId IN (:accountIds) OR toAccountId IN (:accountIds))
    """)
    fun observeFiltered(from: LocalDate, to: LocalDate, accountIds: List<Long>, allAccounts: Boolean): Flow<List<TransferEntity>>

    @Query("SELECT DISTINCT date FROM transfers")
    fun observeDates(): Flow<List<LocalDate>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    fun observeById(id: Long): Flow<TransferEntity?>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getById(id: Long): TransferEntity?

    @Query("SELECT * FROM transfers ORDER BY date, createdAt, id")
    suspend fun getAll(): List<TransferEntity>

    @Insert
    suspend fun insert(transfer: TransferEntity): Long

    @Insert
    suspend fun insertAll(transfers: List<TransferEntity>)

    @Update
    suspend fun update(transfer: TransferEntity): Int

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfers")
    suspend fun deleteAll()

    @Transaction
    suspend fun deleteAndGet(id: Long): TransferEntity? {
        val transfer = getById(id) ?: return null
        deleteById(id)
        return transfer
    }
}
