package com.mercan.maliyem.quickentry

import com.mercan.maliyem.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickEntryLogicTest {
    @Test fun `quick entry links preset the matching type`() {
        assertEquals(TransactionType.EXPENSE, quickEntryType("maliyem://quick-entry/expense"))
        assertEquals(TransactionType.INCOME, quickEntryType("maliyem://quick-entry/income"))
    }

    @Test fun `ordinary launches do not open the form`() {
        assertNull(quickEntryType(null))
        assertNull(quickEntryType("android.intent.action.MAIN"))
        assertNull(quickEntryType("maliyem://quick-entry/unknown"))
    }
}
