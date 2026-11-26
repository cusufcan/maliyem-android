package com.mercan.maliyem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Hesap bakiyesini değiştiren tek hareket; işaret hareket yönünü gösterir. */
data class AccountMovement(val accountId: Long, val delta: Long)

/** Hesap ve hareketlerinin aynı veritabanı anındaki görünümü. */
data class AccountLedger(
    val accounts: List<AccountEntity>,
    val movements: List<AccountMovement>,
    val recurringAccountIds: Set<Long> = emptySet(),
)

@Dao
abstract class AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    abstract fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    abstract suspend fun getAll(): List<AccountEntity>

    @Insert
    abstract suspend fun insertAll(accounts: List<AccountEntity>)

    @Insert
    protected abstract suspend fun insert(account: AccountEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM accounts")
    protected abstract suspend fun nextSortOrder(): Int

    @Transaction
    open suspend fun append(account: AccountEntity) = insert(account.copy(sortOrder = nextSortOrder()))

    @Query("UPDATE accounts SET name = :name, icon = :icon, initialBalanceMinor = :initialBalanceMinor WHERE id = :id")
    abstract suspend fun edit(id: Long, name: String?, icon: String, initialBalanceMinor: Long): Int

    @Query("""
        UPDATE accounts SET archived = :archived WHERE id = :id
            AND (:archived = 0 OR EXISTS (SELECT 1 FROM accounts WHERE archived = 0 AND id != :id))
    """)
    abstract suspend fun setArchived(id: Long, archived: Boolean): Int

    /** Kullanım ve son etkin hesap kontrolü silmeyle aynı SQL deyiminde yapılır. */
    @Query("""
        DELETE FROM accounts WHERE id = :id AND (`key` IS NULL OR `key` != :defaultKey)
            AND (archived = 1 OR EXISTS (SELECT 1 FROM accounts WHERE archived = 0 AND id != :id))
            AND NOT EXISTS (SELECT 1 FROM transactions WHERE accountId = :id)
            AND NOT EXISTS (SELECT 1 FROM recurring_rules WHERE accountId = :id)
            AND NOT EXISTS (SELECT 1 FROM transfers WHERE fromAccountId = :id OR toAccountId = :id)
    """)
    abstract suspend fun deleteUnused(id: Long, defaultKey: String): Int

    @Query("DELETE FROM accounts")
    abstract suspend fun deleteAll()

    @Query("""
        SELECT accountId, CASE type WHEN 'INCOME' THEN amountMinor ELSE -amountMinor END AS delta FROM transactions
        UNION ALL SELECT fromAccountId AS accountId, -amountMinor AS delta FROM transfers
        UNION ALL SELECT toAccountId AS accountId, amountMinor AS delta FROM transfers
    """)
    protected abstract suspend fun getMovements(): List<AccountMovement>

    @Query("SELECT DISTINCT accountId FROM recurring_rules")
    protected abstract suspend fun getRecurringAccountIds(): List<Long>

    @Transaction
    open suspend fun ledger() = AccountLedger(getAll(), getMovements(), getRecurringAccountIds().toSet())
}
