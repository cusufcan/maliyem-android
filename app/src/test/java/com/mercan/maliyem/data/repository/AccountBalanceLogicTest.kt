package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.AccountLedger
import com.mercan.maliyem.data.local.AccountMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBalanceLogicTest {
    private val cash = AccountEntity(1, "cash", icon = "wallet", sortOrder = 0, initialBalanceMinor = 1000)
    private val card = AccountEntity(2, name = "Card", icon = "card", sortOrder = 1, initialBalanceMinor = -200, archived = true)
    private val empty = AccountEntity(3, name = "Bank", icon = "building", sortOrder = 2, initialBalanceMinor = 90)

    @Test fun `opening balances plus income minus expenses and both sides of a transfer`() {
        val movements = listOf(
            AccountMovement(cash.id, 500),
            AccountMovement(cash.id, -100),
            AccountMovement(cash.id, -350),
            AccountMovement(card.id, 350),
        )
        val (cashBalance, cardBalance, unused) = calculateBalances(AccountLedger(listOf(cash, card, empty), movements))

        assertEquals(1050.toBigInteger(), cashBalance.balanceMinor)
        assertEquals(150.toBigInteger(), cardBalance.balanceMinor)
        assertEquals(90.toBigInteger(), unused.balanceMinor)
        assertTrue(cardBalance.hasActivity)
        assertFalse(unused.hasActivity)
        assertEquals(1200.toBigInteger(), cashBalance.balanceMinor + cardBalance.balanceMinor)
    }

    @Test fun `balances beyond Long and zero net activity are preserved`() {
        val max = Long.MAX_VALUE
        val ledger = AccountLedger(
            listOf(cash, card.copy(initialBalanceMinor = 0)),
            listOf(AccountMovement(cash.id, max), AccountMovement(card.id, -max), AccountMovement(card.id, max)),
        )
        val (cashBalance, cardBalance) = calculateBalances(ledger)

        assertEquals(max.toBigInteger() + 1000.toBigInteger(), cashBalance.balanceMinor)
        assertEquals(0.toBigInteger(), cardBalance.balanceMinor)
        assertTrue(cardBalance.hasActivity)
    }
}
