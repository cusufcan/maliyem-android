package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.data.local.RecurringFrequency.MONTHLY
import com.mercan.maliyem.data.local.RecurringFrequency.WEEKLY
import com.mercan.maliyem.data.local.RecurringFrequency.YEARLY
import com.mercan.maliyem.data.local.RecurringRuleEntity
import com.mercan.maliyem.data.local.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringLogicTest {
    @Test fun `weekly includes today and starts at the saved cursor`() {
        val rule = rule(WEEKLY, "2026-11-05").copy(nextDate = date("2026-11-12"))
        assertEquals(dates("2026-11-12", "2026-11-19", "2026-11-26"), occurrences(rule, date("2026-11-26")))
    }

    @Test fun `monthly restores the anchor day after a short month`() {
        val common = rule(MONTHLY, "2026-01-31")
        assertEquals(
            dates("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            occurrences(common, date("2026-05-01")),
        )
        val leap = rule(MONTHLY, "2028-01-31").copy(nextDate = date("2028-02-29"))
        assertEquals(dates("2028-02-29", "2028-03-31"), occurrences(leap, date("2028-03-31")))
    }

    @Test fun `yearly returns to leap day when it exists again`() {
        val rule = rule(YEARLY, "2024-02-29")
        assertEquals(
            dates("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"),
            occurrences(rule, date("2028-02-29")),
        )
    }

    @Test fun `end date is inclusive and ends between two occurrences`() {
        val rule = rule(WEEKLY, "2026-11-05")
        val today = date("2026-11-26")
        val expected = dates("2026-11-05", "2026-11-12")
        assertEquals(expected, occurrences(rule.copy(endDate = date("2026-11-12")), today))
        assertEquals(expected, occurrences(rule.copy(endDate = date("2026-11-13")), today))
        assertTrue(occurrences(rule.copy(nextDate = today, endDate = date("2026-11-13")), today).isEmpty())
    }

    @Test fun `paused rules and future starts are not due`() {
        val rule = rule(WEEKLY, "2026-11-05")
        assertTrue(occurrences(rule.copy(paused = true), date("2026-12-01")).isEmpty())
        assertTrue(occurrences(rule, date("2026-11-04")).isEmpty())
    }

    @Test fun `catch up caps each run and continues at the first ungenerated date`() {
        val rule = rule(WEEKLY, "2000-01-01")
        val today = rule.startDate.plusWeeks(MAX_OCCURRENCES.toLong())
        val first = occurrences(rule, today)
        assertEquals(MAX_OCCURRENCES, first.size)
        assertEquals(rule.startDate.plusWeeks(MAX_OCCURRENCES - 1L), first.last())
        val next = nextOccurrence(rule, first.last())
        assertEquals(listOf(today), occurrences(rule.copy(nextDate = next), today))
        assertTrue(occurrences(rule.copy(nextDate = nextOccurrence(rule, next)), today).isEmpty())
    }

    @Test fun `changing frequency aligns forward without rewinding the cursor`() {
        val rule = rule(MONTHLY, "2026-01-31")
        assertEquals(date("2026-02-28"), occurrenceOnOrAfter(rule.startDate, rule.frequency, date("2026-02-07")))
        assertEquals(date("2026-03-31"), occurrenceOnOrAfter(rule.startDate, rule.frequency, date("2026-03-01")))
        assertEquals(date("2026-01-31"), occurrenceOnOrAfter(rule.startDate, rule.frequency, date("2026-01-01")))
        assertEquals(date("2026-02-28"), occurrenceOnOrAfter(rule.startDate, WEEKLY, date("2026-02-25")))
        val yearly = rule(YEARLY, "2024-02-29")
        assertEquals(date("2027-02-28"), occurrenceOnOrAfter(yearly.startDate, yearly.frequency, date("2026-03-01")))
    }

    @Test fun `the calendar limit does not overflow or repeat the last occurrence`() {
        val last = LocalDate.MAX.minusDays(1)
        val rule = rule(WEEKLY, last.toString())
        assertEquals(listOf(last), occurrences(rule, LocalDate.MAX))
        assertEquals(LocalDate.MAX, nextOccurrence(rule, last))
        assertTrue(occurrences(rule.copy(nextDate = LocalDate.MAX), LocalDate.MAX).isEmpty())
    }

    private fun date(value: String) = LocalDate.parse(value)
    private fun dates(vararg values: String) = values.map(::date)

    private fun rule(frequency: RecurringFrequency, start: String) = RecurringRuleEntity(
        type = TransactionType.EXPENSE, amountMinor = 100, categoryId = 1, accountId = 1,
        frequency = frequency, startDate = date(start), nextDate = date(start), createdAt = 1,
    )
}
