package com.mercan.maliyem.ui.lock

import com.mercan.maliyem.ui.lock.LockTimeout.FIVE_MINUTES
import com.mercan.maliyem.ui.lock.LockTimeout.IMMEDIATE
import com.mercan.maliyem.ui.lock.LockTimeout.ONE_MINUTE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockLogicTest {
    @Test fun `disabled lock never requires authentication`() {
        assertFalse(shouldLock(false, IMMEDIATE, null, 100))
        assertFalse(shouldLock(false, IMMEDIATE, 0, 100))
    }

    @Test fun `cold start requires authentication with any timeout`() {
        for (timeout in LockTimeout.entries) {
            assertTrue(shouldLock(true, timeout, null, 100))
        }
    }

    @Test fun `immediate lock applies even in the same millisecond`() {
        assertTrue(shouldLock(true, IMMEDIATE, 100, 100))
    }

    @Test fun `one minute locks exactly at the deadline`() {
        assertFalse(shouldLock(true, ONE_MINUTE, 100, 60_099))
        assertTrue(shouldLock(true, ONE_MINUTE, 100, 60_100))
    }

    @Test fun `five minutes allows short absences and locks after the deadline`() {
        assertFalse(shouldLock(true, FIVE_MINUTES, 100, 60_100))
        assertFalse(shouldLock(true, FIVE_MINUTES, 100, 300_099))
        assertTrue(shouldLock(true, FIVE_MINUTES, 100, 300_100))
        assertTrue(shouldLock(true, FIVE_MINUTES, 100, 300_101))
    }

    @Test fun `an invalid elapsed timestamp requires authentication`() {
        assertTrue(shouldLock(true, FIVE_MINUTES, 101, 100))
    }
}
