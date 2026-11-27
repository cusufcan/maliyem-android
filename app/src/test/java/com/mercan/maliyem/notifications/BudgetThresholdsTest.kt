package com.mercan.maliyem.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetThresholdsTest {
    @Test fun `each exact boundary counts as reached`() {
        assertEquals(listOf(80), crossings(799, 800))
        assertEquals(listOf(100), crossings(999, 1000))
    }

    @Test fun `one expense can cross both thresholds`() {
        assertEquals(listOf(80, 100), crossings(500, 1100))
    }

    @Test fun `edits at or above a threshold and decreases do not cross it again`() {
        assertEquals(emptyList<Int>(), crossings(800, 900))
        assertEquals(emptyList<Int>(), crossings(1000, 1200))
        assertEquals(emptyList<Int>(), crossings(1000, 600))
        assertEquals(emptyList<Int>(), crossings(800, 800))
    }

    @Test fun `a fractional boundary is rounded up to the next whole kurus`() {
        assertEquals(emptyList<Int>(), crossings(80, 81, 102))
        assertEquals(listOf(80), crossings(81, 82, 102))
    }

    @Test fun `spending and scaling can exceed Long max`() {
        val limit = Long.MAX_VALUE
        val after = limit.toBigInteger() + 1.toBigInteger()
        assertEquals(listOf(80, 100), crossedBudgetThresholds(0.toBigInteger(), after, limit))
    }

    private fun crossings(before: Long, after: Long, limit: Long = 1000L) =
        crossedBudgetThresholds(before.toBigInteger(), after.toBigInteger(), limit)
}
