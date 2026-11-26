package com.mercan.maliyem.data.backup

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
    fun encode(rows: List<CsvRow>): String = buildString {
        append("\uFEFFdate,type,category,amount,note,account\r\n")
        for (row in rows.sortedBy { it.date }) {
            val amount = "${row.amountMinor / 100}.${(row.amountMinor % 100).toString().padStart(2, '0')}"
            append(listOf(row.date.toString(), row.type, row.category, amount, row.note.orEmpty(), row.account).joinToString(",", transform = ::quote))
            append("\r\n")
        }
    }

    private fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
}
