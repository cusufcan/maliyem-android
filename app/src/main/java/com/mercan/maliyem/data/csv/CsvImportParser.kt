package com.mercan.maliyem.data.csv

import com.mercan.maliyem.data.backup.CsvExporter
import com.mercan.maliyem.data.local.TransactionType
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

internal const val MaxCsvBytes = 10_000_000
internal const val MaxCsvRows = 50_000

internal enum class CsvFileError { SIZE_LIMIT, ROW_LIMIT, EMPTY, UNREADABLE }
internal class CsvImportException(val reason: CsvFileError) : IOException()

internal enum class CsvField { DATE, AMOUNT, TYPE, CATEGORY, NOTE, ACCOUNT }

internal data class CsvMapping(
    val columns: Map<CsvField, Int>,
    val hasHeader: Boolean = true,
) {
    fun isValid(width: Int): Boolean =
        CsvField.DATE in columns && CsvField.AMOUNT in columns &&
            columns.values.all { it in 0 until width } && columns.values.toSet().size == columns.size

    companion object {
        val Maliyem = CsvMapping(
            mapOf(CsvField.DATE to 0, CsvField.TYPE to 1, CsvField.CATEGORY to 2,
                CsvField.AMOUNT to 3, CsvField.NOTE to 4, CsvField.ACCOUNT to 5),
        )
    }
}

internal data class CsvRecord(val line: Int, val cells: List<String>, val malformed: Boolean)

internal data class CsvDocument(
    val records: List<CsvRecord>,
    val isMaliyem: Boolean,
) {
    private val maxWidth by lazy { records.maxOf { it.cells.size } }

    fun width(hasHeader: Boolean) = if (hasHeader) records.first().cells.size else maxWidth

    fun columnTitle(index: Int, hasHeader: Boolean): String? =
        if (hasHeader) records.first().cells.getOrNull(index)?.takeIf(String::isNotBlank) else null

    fun suggestedMapping(): CsvMapping {
        if (isMaliyem) return CsvMapping.Maliyem
        return CsvMapping(buildMap {
            for ((index, cell) in records.first().cells.withIndex()) {
                val field = headerFields[cell.trim().csvNameKey()] ?: continue
                if (field !in this) put(field, index)
                if (size == CsvField.entries.size) break
            }
        })
    }
}

internal enum class CsvRowError { QUOTING, COLUMN_COUNT, DATE, AMOUNT, TYPE, TRANSFER_ACCOUNTS }
internal data class CsvIssue(val line: Int, val reason: CsvRowError)

internal data class CsvEntry(
    val line: Int,
    val date: LocalDate,
    val amountMinor: Long,
    val type: TransactionType?,
    val category: String?,
    val note: String?,
    val account: String?,
    val destination: String? = null,
)

internal data class MappedCsv(val entries: List<CsvEntry>, val issues: List<CsvIssue>)

private val headerAliases = mapOf(
    CsvField.DATE to setOf("date", "tarih", "transaction date", "işlem tarihi"),
    CsvField.AMOUNT to setOf("amount", "tutar"),
    CsvField.TYPE to setOf("type", "tür", "tip", "işlem türü"),
    CsvField.CATEGORY to setOf("category", "kategori"),
    CsvField.NOTE to setOf("note", "not", "description", "açıklama"),
    CsvField.ACCOUNT to setOf("account", "hesap"),
)

private val headerFields = headerAliases.flatMap { (field, names) -> names.map { it to field } }.toMap()

internal fun String.csvNameKey(): String {
    if (isEmpty()) return this
    return buildString(length) { for (char in this@csvNameKey) append(char.lowercaseChar()) }
}

internal object CsvImportParser {
    private val turkishDate = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    private val plainAmount = Regex("[+-]?[0-9]++(?:[.,][0-9]{1,2})?")
    private val groupedTurkish = Regex("[+-]?[0-9]{1,3}(?:\\.[0-9]{3})++(?:,[0-9]{1,2})?")
    private val groupedEnglish = Regex("[+-]?[0-9]{1,3}(?:,[0-9]{3})++\\.[0-9]{1,2}")
    private val isoDateShape = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val turkishDateShape = Regex("[0-9]{2}\\.[0-9]{2}\\.[0-9]{4}")

    fun read(input: InputStream): CsvDocument {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val remaining = MaxCsvBytes - output.size()
            val count = input.read(chunk, 0, minOf(chunk.size, remaining + 1))
            if (count < 0) break
            if (count > remaining) throw CsvImportException(CsvFileError.SIZE_LIMIT)
            output.write(chunk, 0, count)
        }
        return parse(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString())
    }

    fun parse(text: String): CsvDocument {
        val content = text.removePrefix("\uFEFF")
        val records = readRecords(content, detectDelimiter(content)).ifEmpty {
            throw CsvImportException(CsvFileError.EMPTY)
        }
        val header = records.first()
        val isMaliyem = !header.malformed && header.cells == CsvExporter.columns
        return CsvDocument(records, isMaliyem)
    }

    private val expenseTypes = setOf("expense", "gider", "debit")
    private val incomeTypes = setOf("income", "gelir", "credit")

    fun map(document: CsvDocument, mapping: CsvMapping): MappedCsv {
        val headerRows = if (mapping.hasHeader) 1 else 0
        if (document.records.size - headerRows > MaxCsvRows) throw CsvImportException(CsvFileError.ROW_LIMIT)
        val width = document.width(mapping.hasHeader)
        require(mapping.isValid(width))
        val entries = mutableListOf<CsvEntry>()
        val issues = mutableListOf<CsvIssue>()
        val usingExport = document.isMaliyem && mapping.hasHeader
        for (index in headerRows until document.records.size) {
            val record = document.records[index]
            fun cell(field: CsvField) = mapping.columns[field]?.let(record.cells::getOrNull).orEmpty()
            fun text(field: CsvField) = cell(field).let {
                if (usingExport) CsvExporter.unescapeText(it) else if (field == CsvField.NOTE) it else it.trim()
            }.ifEmpty { null }
            val date = date(cell(CsvField.DATE).trim())
            val amount = amount(cell(CsvField.AMOUNT).trim())
            val label = cell(CsvField.TYPE).trim().csvNameKey()
            val fromSign = CsvField.TYPE !in mapping.columns
            val type = when {
                fromSign -> if (amount != null && amount < 0) TransactionType.EXPENSE else TransactionType.INCOME
                label in expenseTypes -> TransactionType.EXPENSE
                label in incomeTypes -> TransactionType.INCOME
                else -> null
            }
            val isTransfer = !fromSign && label == "transfer"
            val account = text(CsvField.ACCOUNT)
            val transferAccounts = if (isTransfer) account?.split(" → ", limit = 3) else null

            val error = when {
                record.malformed -> CsvRowError.QUOTING
                (mapping.hasHeader && record.cells.size != width) ||
                    mapping.columns.values.any { it >= record.cells.size } -> CsvRowError.COLUMN_COUNT
                date == null -> CsvRowError.DATE
                amount == null || amount == 0L || amount == Long.MIN_VALUE || (isTransfer && amount < 0) -> CsvRowError.AMOUNT
                type == null && !isTransfer -> CsvRowError.TYPE
                isTransfer && (transferAccounts?.size != 2 || transferAccounts.any(String::isBlank) ||
                    transferAccounts[0] == transferAccounts[1]) -> CsvRowError.TRANSFER_ACCOUNTS
                else -> null
            }
            if (error != null) {
                issues.add(CsvIssue(record.line, error))
            } else {
                entries.add(CsvEntry(
                    record.line, checkNotNull(date), kotlin.math.abs(checkNotNull(amount)), type,
                    text(CsvField.CATEGORY), text(CsvField.NOTE),
                    transferAccounts?.firstOrNull() ?: account, transferAccounts?.get(1),
                ))
            }
        }
        return MappedCsv(entries, issues)
    }

    private fun date(value: String): LocalDate? = try {
        when {
            isoDateShape.matches(value) -> LocalDate.parse(value)
            turkishDateShape.matches(value) -> LocalDate.parse(value, turkishDate)
            else -> null
        }?.takeIf { it.year > 0 }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun amount(value: String): Long? {
        val decimal = when {
            plainAmount.matches(value) -> value.replace(',', '.')
            groupedTurkish.matches(value) -> value.replace(".", "").replace(',', '.')
            groupedEnglish.matches(value) -> value.replace(",", "")
            else -> return null
        }
        val separator = decimal.indexOf('.')
        val minor = if (separator < 0) decimal + "00" else {
            decimal.substring(0, separator) + decimal.substring(separator + 1).padEnd(2, '0')
        }
        return minor.toLongOrNull()
    }

    private fun detectDelimiter(text: String): Char {
        var quoted = false
        var commas = 0
        var semicolons = 0
        for (char in text) {
            when {
                char == '"' -> quoted = !quoted
                quoted -> Unit
                char == ',' -> commas++
                char == ';' -> semicolons++
                (char == '\r' || char == '\n') && commas + semicolons > 0 -> break
            }
        }
        return if (semicolons >= commas && semicolons > 0) ';' else ','
    }

    private enum class CellState { START, TEXT, QUOTED, AFTER_QUOTE }

    private fun readRecords(text: String, delimiter: Char): List<CsvRecord> {
        val records = mutableListOf<CsvRecord>()
        var cells = mutableListOf<String>()
        val field = StringBuilder()
        var state = CellState.START
        var malformed = false
        var line = 1
        var recordLine = 1
        var index = 0

        fun finishCell() {
            cells.add(if (field.isEmpty()) "" else field.toString())
            field.clear()
            state = CellState.START
        }

        fun finishRecord() {
            if (cells.isEmpty() && state == CellState.START && !malformed) return
            finishCell()
            records.add(CsvRecord(recordLine, cells, malformed))
            // Allow one header. Headerless mappings are checked again before preview.
            if (records.size > MaxCsvRows + 1) throw CsvImportException(CsvFileError.ROW_LIMIT)
            cells = mutableListOf()
            malformed = false
        }

        while (index < text.length) {
            val char = text[index++]
            when {
                char == '\n' || char == '\r' -> {
                    val crlf = char == '\r' && text.getOrNull(index) == '\n'
                    if (state == CellState.QUOTED) {
                        field.append(char)
                        if (crlf) field.append('\n')
                    } else {
                        finishRecord()
                        recordLine = line + 1
                    }
                    if (crlf) index++
                    line++
                }
                state == CellState.QUOTED -> if (char == '"') {
                    state = CellState.AFTER_QUOTE
                } else {
                    field.append(char)
                }
                char == delimiter -> finishCell()
                state == CellState.AFTER_QUOTE && char == '"' -> {
                    field.append('"')
                    state = CellState.QUOTED
                }
                state == CellState.START && char == '"' -> state = CellState.QUOTED
                else -> {
                    if (char == '"' || state == CellState.AFTER_QUOTE) malformed = true
                    state = CellState.TEXT
                    field.append(char)
                }
            }
        }
        if (state == CellState.QUOTED) malformed = true
        finishRecord()
        return records
    }
}
