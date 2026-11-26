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

/** Eğilim toplamları için gerekli işlem alanları. */
data class TrendEntry(val date: LocalDate, val type: TransactionType, val categoryId: Long, val amountMinor: Long)

@Dao
interface TransactionDao {
    /** [from] ve [to] dahil; ay görünümü için ayın ilk ve son günü verilir. */
    @Transaction
    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC, id DESC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TransactionWithCategory>>

    /** Tarih sınırları dahil. Transferler ayrı tabloda olduğu için bu okumaya girmez. */
    @Query("SELECT date, type, categoryId, amountMinor FROM transactions WHERE date BETWEEN :from AND :to")
    fun observeTrendEntries(from: LocalDate, to: LocalDate): Flow<List<TrendEntry>>

    /** Tarih sınırları dahil; boş kimlik listeleri o alanın tüm değerlerini kapsar. */
    @Transaction
    @Query("""
        SELECT * FROM transactions
        WHERE date BETWEEN :from AND :to
          AND (:type IS NULL OR type = :type)
          AND (:allCategories OR categoryId IN (:categoryIds))
          AND (:allAccounts OR accountId IN (:accountIds))
        ORDER BY date DESC, createdAt DESC, id DESC
    """)
    fun observeFiltered(
        from: LocalDate,
        to: LocalDate,
        type: TransactionType?,
        categoryIds: List<Long>,
        accountIds: List<Long>,
        allCategories: Boolean,
        allAccounts: Boolean,
    ): Flow<List<TransactionWithCategory>>

    /** Kaydı olan günler; ay gezintisi boş ayları atlasın diye. */
    @Query("SELECT DISTINCT date FROM transactions")
    fun observeDates(): Flow<List<LocalDate>>

    /** Ayrıntı ekranının izleyeceği ayı belirler; kayıt silinince null yayar. */
    @Query("SELECT date FROM transactions WHERE id = :id")
    fun observeDate(id: Long): Flow<LocalDate?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date, createdAt, id")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT categoryId, COUNT(*) AS count FROM transactions GROUP BY categoryId")
    fun observeCounts(): Flow<List<CategoryCount>>

    /** Kimliği 0 olmayan kayıt aynı kimlikle geri yazılır ("Geri al"). */
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Update
    suspend fun update(transaction: TransactionEntity): Int

    @Transaction
    suspend fun deleteAndGet(id: Long): TransactionEntity? {
        val transaction = getById(id) ?: return null
        deleteById(id)
        return transaction
    }

    /** Düzenleme, kural silinmişse bağlantıyı geri getirmemeli. */
    @Transaction
    suspend fun updateKeepingRule(transaction: TransactionEntity): Int {
        val current = getById(transaction.id) ?: return 0
        return update(transaction.copy(recurringRuleId = current.recurringRuleId))
    }

    @Query("SELECT id FROM recurring_rules WHERE id = :id")
    suspend fun existingRecurringRuleId(id: Long): Long?

    @Query("SELECT id FROM categories WHERE type = :type AND (id = :id OR `key` = :fallbackKey) ORDER BY (id = :id) DESC LIMIT 1")
    suspend fun categoryForRestore(id: Long, type: TransactionType, fallbackKey: String): Long?

    @Transaction
    suspend fun restore(transaction: TransactionEntity, fallbackKey: String): Boolean {
        val categoryId = categoryForRestore(transaction.categoryId, transaction.type, fallbackKey) ?: return false
        val ruleId = transaction.recurringRuleId?.let { existingRecurringRuleId(it) }
        insert(transaction.copy(categoryId = categoryId, recurringRuleId = ruleId))
        return true
    }
}
