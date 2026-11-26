package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.AccountDao
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.MaliyemDatabase
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AccountRepository @Inject constructor(
    private val db: MaliyemDatabase,
    private val dao: AccountDao,
) {
    fun observeAll() = dao.observeAll().distinctUntilChanged()

    fun observeBalances() = db.invalidationTracker.createFlow("accounts", "transactions", "transfers", "recurring_rules")
        .map { calculateBalances(dao.ledger()) }
        .distinctUntilChanged()

    suspend fun add(name: String, icon: String, initialBalanceMinor: Long) =
        dao.append(AccountEntity(name = name, icon = icon, sortOrder = 0, initialBalanceMinor = initialBalanceMinor))

    suspend fun edit(id: Long, name: String?, icon: String, initialBalanceMinor: Long) =
        dao.edit(id, name, icon, initialBalanceMinor) > 0

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived) > 0

    suspend fun delete(id: Long) = dao.deleteUnused(id, DefaultAccounts.CASH) > 0
}
