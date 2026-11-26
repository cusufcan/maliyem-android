package com.mercan.maliyem.data.backup

import java.math.BigInteger
import java.time.LocalDate

data class CsvRow(
    val date: LocalDate,
    val type: String,
    val category: String,
    val amountMinor: Long,
    val note: String?,
    val account: String,
)

/** Excel için UTF-8 BOM, alanlar ve satır sonları için RFC 4180. Tutar kuruştan tam sayıyla çevrilir. */
object CsvExporter {
    private val formulaPrefixes = setOf('=', '+', '-', '@', '\t', '\r')
    internal val columns = listOf("date", "type", "category", "amount", "note", "account")

    internal fun unescapeText(value: String): String =
        if (value.startsWith("'") && value.getOrNull(1) in formulaPrefixes) value.substring(1) else value

    fun encode(rows: List<CsvRow>): String = buildString {
        append('\uFEFF').append(columns.joinToString(",")).append("\r\n")
        for (row in rows.sortedBy { it.date }) {
            val minor = row.amountMinor.toBigInteger()
            val (lira, cents) = minor.abs().divideAndRemainder(BigInteger.valueOf(100))
            val amount = (if (minor.signum() < 0) "-" else "") + "$lira.${cents.toString().padStart(2, '0')}"
            val cells = listOf(
                textCell(row.date.toString()), textCell(row.type), textCell(row.category),
                amount, textCell(row.note.orEmpty()), textCell(row.account),
            )
            append(cells.joinToString(","))
            append("\r\n")
        }
    }

    private fun textCell(value: String): String {
        val field = if (value.firstOrNull() in formulaPrefixes) "'$value" else value
        return if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
