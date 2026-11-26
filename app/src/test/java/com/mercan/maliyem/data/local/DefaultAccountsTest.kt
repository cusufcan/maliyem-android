package com.mercan.maliyem.data.local

import com.mercan.maliyem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultAccountsTest {
    @Test fun `cash starts active with a localized name and zero balance`() {
        val cash = DefaultAccounts.cash()

        assertEquals(DefaultAccounts.CASH_ID, cash.id)
        assertEquals("cash", cash.key)
        assertNull(cash.name)
        assertEquals(R.string.account_cash, DefaultAccounts.nameRes(cash.key))
        assertNull(DefaultAccounts.nameRes(null))
        assertFalse(cash.archived)
        assertEquals(0L, cash.initialBalanceMinor)
        assertEquals(0, cash.sortOrder)
    }
}
