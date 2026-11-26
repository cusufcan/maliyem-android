package com.mercan.maliyem.data.backup

import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {
    private val date = LocalDate.of(2026, 11, 26)

    @Test fun `writes UTF-8 BOM and header for an empty export`() {
        val exported = CsvExporter.encode(emptyList())

        assertEquals("\uFEFFdate,type,category,amount,note,account\r\n", exported)
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            exported.toByteArray(Charsets.UTF_8).copyOf(3),
        )
    }

    @Test fun `quotes commas quotes CR and LF and preserves Turkish characters`() {
        val rows = listOf(
            CsvRow(date, "Gider", "Yeme, içme", 12_550, "Çay \"açık\"\r\nyemek", "Nakit"),
            CsvRow(date, "Gelir", "Çeviri\rişi", 5, "iki\nsatır", "Maaş, kart"),
        )

        assertEquals(
            "\uFEFFdate,type,category,amount,note,account\r\n" +
                "2026-11-26,Gider,\"Yeme, içme\",125.50,\"Çay \"\"açık\"\"\r\nyemek\",Nakit\r\n" +
                "2026-11-26,Gelir,\"Çeviri\rişi\",0.05,\"iki\nsatır\",\"Maaş, kart\"\r\n",
            CsvExporter.encode(rows),
        )
    }

    @Test fun `sorts ascending and formats exact positive amounts without a currency sign`() {
        val rows = listOf(
            CsvRow(date, "Expense", "Groceries", 200, null, "Cash"),
            CsvRow(date.minusDays(1), "Income", "Salary", Long.MAX_VALUE, null, "Card"),
        )

        assertEquals(
            "\uFEFFdate,type,category,amount,note,account\r\n" +
                "2026-11-25,Income,Salary,92233720368547758.07,,Card\r\n" +
                "2026-11-26,Expense,Groceries,2.00,,Cash\r\n",
            CsvExporter.encode(rows),
        )
    }

    @Test fun `exports a transfer with an empty category and a directional account`() {
        val row = CsvRow(date, "Transfer", "", 535, null, "Cash → Card")

        assertEquals(
            "\uFEFFdate,type,category,amount,note,account\r\n2026-11-26,Transfer,,5.35,,Cash → Card\r\n",
            CsvExporter.encode(listOf(row)),
        )
    }
}
