package com.mercan.maliyem.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val en = Locale.US

    @Test fun `turkish format`() {
        assertEquals("12.450,00 ₺", formatMoney(1_245_000, tr))
        assertEquals("1.234.567,89 ₺", formatMoney(123_456_789, tr))
        assertEquals("0,05 ₺", formatMoney(5, tr))
        assertEquals("0,00 ₺", formatMoney(0, tr))
    }

    @Test fun `english format`() {
        assertEquals("₺12,450.00", formatMoney(1_245_000, en))
        assertEquals("₺1,234,567.89", formatMoney(123_456_789, en))
        assertEquals("₺999.99", formatMoney(99_999, en))
    }

    @Test fun `signs use plus and unicode minus`() {
        assertEquals("+9.623,35 ₺", formatMoney(962_335, tr, signed = true))
        assertEquals("−250,00 ₺", formatMoney(-25_000, tr))
        assertEquals("−₺250.00", formatMoney(-25_000, en, signed = true))
        assertEquals("0,00 ₺", formatMoney(0, tr, signed = true))
    }

    @Test fun `extreme values do not overflow`() {
        assertEquals("−₺92,233,720,368,547,758.08", formatMoney(Long.MIN_VALUE, en))
        assertEquals("₺92,233,720,368,547,758.07", formatMoney(Long.MAX_VALUE, en))
    }

    @Test fun `parse turkish`() {
        assertEquals(1250L, parseMoney("12,5", tr))
        assertEquals(123_456L, parseMoney("1.234,56", tr))
        assertEquals(1_245_000L, parseMoney("12.450,00 ₺", tr))
        assertEquals(1200L, parseMoney("12", tr))
        assertEquals(50L, parseMoney(",5", tr))
        assertEquals(0L, parseMoney("0", tr))
    }

    @Test fun `parse english`() {
        assertEquals(123_456L, parseMoney("1,234.56", en))
        assertEquals(1250L, parseMoney("₺12.5", en))
        assertEquals(1200L, parseMoney("12.", en))
    }

    @Test fun `parse rejects invalid input`() {
        listOf("", "  ", "₺", "abc", "12a", "-5", "12,345", "1,2,3", ",").forEach {
            assertNull("\"$it\"", parseMoney(it, tr))
        }
        assertNull(parseMoney("1.234.567", en))
        assertNull(parseMoney("99999999999999999999", tr))
        assertNull(parseMoney("92233720368547758,08", tr))
    }

    @Test fun `format then parse round trips`() {
        listOf(0L, 5L, 99L, 100L, 1_245_000L, 123_456_789L).forEach { minor ->
            assertEquals(minor, parseMoney(formatMoney(minor, tr), tr))
            assertEquals(minor, parseMoney(formatMoney(minor, en), en))
        }
    }
}
