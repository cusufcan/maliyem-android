package com.mercan.maliyem.data.csv

import com.mercan.maliyem.data.backup.CsvExporter
import com.mercan.maliyem.data.backup.CsvRow
import com.mercan.maliyem.data.local.TransactionType.EXPENSE
import com.mercan.maliyem.data.local.TransactionType.INCOME
import java.io.ByteArrayInputStream
import java.nio.charset.CharacterCodingException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportParserTest {
    private val date = LocalDate.of(2024, 2, 29)

    @Test fun `Maliyem export round trips both languages transfers and escaped text`() {
        val rows = listOf(
            CsvRow(date, "Gider", "Market, Kasap", 123_456, "=list,\"milk\"\r\nBread", "Nakit"),
            CsvRow(date, "Income", "Salary", Long.MAX_VALUE, "  keep spaces  ", "Cash"),
            CsvRow(date, "Transfer", "", 400, null, "-Savings → Wallet"),
        )
        val encoded = CsvExporter.encode(rows)
        val document = CsvImportParser.read(encoded.byteInputStream())
        assertTrue(document.isMaliyem)
        val result = CsvImportParser.map(document, document.suggestedMapping())
        assertTrue(result.issues.isEmpty())
        assertEquals(listOf(
            CsvEntry(2, date, 123_456, EXPENSE, "Market, Kasap", "=list,\"milk\"\r\nBread", "Nakit"),
            CsvEntry(4, date, Long.MAX_VALUE, INCOME, "Salary", "  keep spaces  ", "Cash"),
            CsvEntry(5, date, 400, null, null, null, "-Savings", "Wallet"),
        ), result.entries)
    }

    @Test fun `reordered semicolon columns map Turkish dates amounts signs and optional fields`() {
        val csv = """
            Hesap;Açıklama;Tutar;Kullanılmayan;Tarih;Kategori
            Kart;"İki; fiş";-1.234,56;x;29.02.2024;Gıda
            ;Maaş;+12,5;x;2024-02-29;
        """.trimIndent()
        val document = CsvImportParser.parse(csv)
        assertFalse(document.isMaliyem)
        val mapping = document.suggestedMapping()
        assertEquals(CsvMapping(mapOf(
            CsvField.DATE to 4, CsvField.AMOUNT to 2, CsvField.CATEGORY to 5,
            CsvField.NOTE to 1, CsvField.ACCOUNT to 0,
        )), mapping)
        val result = CsvImportParser.map(document, mapping)
        assertTrue(result.issues.isEmpty())
        assertEquals(listOf(
            CsvEntry(2, date, 123_456, EXPENSE, "Gıda", "İki; fiş", "Kart"),
            CsvEntry(3, date, 1_250, INCOME, null, "Maaş", null),
        ), result.entries)
    }

    @Test fun `generic comma file supports quoted comma decimals and manual type selection`() {
        val document = CsvImportParser.parse("a,b,c,d\nDebit,2024-02-29,\"1.234,56\",\"quoted ; note\"\nCredit,29.02.2024,\"1,234.56\",")
        assertFalse(document.suggestedMapping().isValid(document.width(true)))
        val mapping = CsvMapping(mapOf(CsvField.DATE to 1, CsvField.AMOUNT to 2, CsvField.TYPE to 0, CsvField.NOTE to 3))
        val result = CsvImportParser.map(document, mapping)
        assertEquals(listOf(
            CsvEntry(2, date, 123_456, EXPENSE, null, "quoted ; note", null),
            CsvEntry(3, date, 123_456, INCOME, null, null, null),
        ), result.entries)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun `headerless mapping includes first record and accepts unused trailing cells`() {
        val csv = CsvImportParser.parse("\n29.02.2024;-12,34\r2024-02-29;1.234;ignored\r\n")
        val mapping = CsvMapping(mapOf(CsvField.DATE to 0, CsvField.AMOUNT to 1), hasHeader = false)
        assertEquals(3, csv.width(false))
        val result = CsvImportParser.map(csv, mapping)
        assertTrue(result.issues.isEmpty())
        assertEquals(listOf(2, 3), result.entries.map { it.line })
        assertEquals(listOf(1_234L, 123_400L), result.entries.map { it.amountMinor })
    }

    @Test fun `mapping requires two different in-range columns and no reused optional column`() {
        val fields = mapOf(CsvField.DATE to 0, CsvField.AMOUNT to 1)
        assertTrue(CsvMapping(fields).isValid(2))
        assertFalse(CsvMapping(fields - CsvField.AMOUNT).isValid(2))
        for (invalid in listOf(0, 1, 2, -1)) {
            assertFalse(CsvMapping(fields + (CsvField.ACCOUNT to invalid)).isValid(2))
        }
        assertTrue(CsvMapping(fields + (CsvField.ACCOUNT to 2)).isValid(3))
    }

    @Test fun `bad records include line and reason and do not discard later valid records`() {
        val csv = CsvImportParser.parse("""
            date;amount;type;account
            2023-02-29;1;Gelir;
            31.04.2024;1;Gelir;
            2024-2-09;1;Gelir;
            2024-02-29;0;Gelir;
            2024-02-29;1,234;Gelir;
            2024-02-29;12.34,56;Gider;
            2024-02-29;92233720368547758.08;Gelir;
            2024-02-29;-92233720368547758.08;Gider;
            2024-02-29;1;Unknown;
            2024-02-29;1;Transfer;Cash
            2024-02-29;-1;Transfer;Cash → Savings
            2024-02-29;1;Gelir
            2024-02-29;1;Gelir;"Cash"x
            2024-02-29;-0.01;GİDER;
            2024-02-29;1;Gelir;"open
            quote
        """.trimIndent())
        val result = CsvImportParser.map(csv, csv.suggestedMapping())
        assertEquals(listOf(CsvEntry(15, date, 1, EXPENSE, null, null, null)), result.entries)
        assertEquals((2..14).toList() + 16, result.issues.map { it.line })
        assertEquals(listOf(
            CsvRowError.DATE, CsvRowError.DATE, CsvRowError.DATE, CsvRowError.AMOUNT,
            CsvRowError.AMOUNT, CsvRowError.AMOUNT, CsvRowError.AMOUNT, CsvRowError.AMOUNT,
            CsvRowError.TYPE, CsvRowError.TRANSFER_ACCOUNTS, CsvRowError.AMOUNT,
            CsvRowError.COLUMN_COUNT, CsvRowError.QUOTING, CsvRowError.QUOTING,
        ), result.issues.map { it.reason })
    }

    @Test fun `row limit counts logical records including erroneous ones and excludes headers`() {
        val row = "2024-02-29;1;\"a\nb\"\n"
        val header = "date;amount;note\n"
        val accepted = CsvImportParser.parse(header + row.repeat(MaxCsvRows))
        assertEquals(MaxCsvRows, CsvImportParser.map(accepted, accepted.suggestedMapping()).entries.size)
        assertEquals(CsvFileError.ROW_LIMIT, assertThrows(CsvImportException::class.java) {
            CsvImportParser.parse(header + row.repeat(MaxCsvRows + 1))
        }.reason)
        val headerless = CsvImportParser.parse(row.repeat(MaxCsvRows + 1))
        assertEquals(CsvFileError.ROW_LIMIT, assertThrows(CsvImportException::class.java) {
            CsvImportParser.map(headerless, CsvMapping(mapOf(CsvField.DATE to 0, CsvField.AMOUNT to 1), false))
        }.reason)
    }

    @Test fun `byte cap measures the UTF8 stream and accepts exactly 10 MB`() {
        val bytes = ByteArray(MaxCsvBytes) { '\n'.code.toByte() }
        val header = "date;amount;not ç\n".toByteArray()
        header.copyInto(bytes)
        assertEquals(listOf("date", "amount", "not ç"), CsvImportParser.read(bytes.inputStream()).records.single().cells)
        val tooLong = ByteArrayInputStream(bytes + 0.toByte())
        assertEquals(CsvFileError.SIZE_LIMIT, assertThrows(CsvImportException::class.java) {
            CsvImportParser.read(tooLong)
        }.reason)
        assertThrows(CharacterCodingException::class.java) {
            CsvImportParser.read(byteArrayOf(0xC3.toByte()).inputStream())
        }
        assertEquals(CsvFileError.EMPTY, assertThrows(CsvImportException::class.java) {
            CsvImportParser.parse("\uFEFF\r\n")
        }.reason)
    }
}
