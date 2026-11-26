package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.AccountLedger
import java.math.BigInteger

data class AccountBalance(val account: AccountEntity, val balanceMinor: BigInteger, val hasActivity: Boolean)

/** Gelecek tarih dahil tüm hareketler eklenir. Toplamlar Long sınırını aşabilir. */
fun calculateBalances(ledger: AccountLedger): List<AccountBalance> {
    val totals = mutableMapOf<Long, BigInteger>()
    for ((id, delta) in ledger.movements) {
        totals[id] = (totals[id] ?: BigInteger.ZERO) + delta.toBigInteger()
    }
    return ledger.accounts.map { account ->
        val movement = totals[account.id]
        AccountBalance(account, account.initialBalanceMinor.toBigInteger() + (movement ?: BigInteger.ZERO), movement != null)
    }
}
