package com.mercan.maliyem.data.receipt

import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

data class ReceiptLine(val text: String, val top: Int? = null)

data class ReceiptSuggestion(val amountMinor: Long?, val date: LocalDate?, val merchant: String?)

class ReceiptParser(private val clock: Clock) {
    fun parse(lines: List<ReceiptLine>): ReceiptSuggestion {
        val ordered = if (lines.all { it.top != null }) lines.sortedBy { it.top } else lines
        val text = ordered.map { it.text.trim() }.filter { it.isNotEmpty() }
        val normalized = text.map(::normalize)
        val values = normalized.map { line -> money.findAll(line).mapNotNull(::toMinor).toList() }
        val total = normalized.indices.mapNotNull { index ->
            val line = normalized[index]
            val label = totalLabel.find(line) ?: return@mapNotNull null
            if (nonFinal.containsMatchIn(line)) return@mapNotNull null
            val suffix = line.substring(label.range.last + 1)
            val amount = money.findAll(suffix).mapNotNull(::toMinor).lastOrNull()
                ?: values[index].lastOrNull()
                ?: normalized.getOrNull(index + 1)?.takeIf { amountOnly.matches(it) }
                    ?.let { values[index + 1].singleOrNull() }
                ?: return@mapNotNull null
            val priority = when {
                explicitFinal.containsMatchIn(line) -> 3
                label.value != "TUTAR" -> 2
                else -> 1
            }
            Triple(priority, index, amount)
        }.maxWithOrNull(compareBy({ it.first }, { it.second }))?.third

        val today = LocalDate.now(clock)
        val earliest = today.minusYears(5)
        val latest = today.plusDays(1)
        val dates = normalized.map { line ->
            datePattern.findAll(line).mapNotNull(::toDate).firstOrNull { it in earliest..latest }
        }
        val date = normalized.indices.firstNotNullOfOrNull { index ->
            dates[index]?.takeIf { dateLabel.containsMatchIn(normalized[index]) }
                ?: dates.getOrNull(index + 1)?.takeIf {
                    dateLabelOnly.matches(normalized[index]) && datePattern.containsMatchIn(normalized[index + 1])
                }
        } ?: dates.firstOrNull { it != null }

        val merchant = text.indices.take(10)
            .takeWhile { !totalLabel.containsMatchIn(normalized[it]) && values[it].isEmpty() }
            .firstOrNull { index ->
                val line = normalized[index]
                !headerNoise.containsMatchIn(line) && !datePattern.containsMatchIn(line) &&
                    !phone.containsMatchIn(line) && line.count(Char::isLetter) >= 2
            }?.let { titleCase(text[it]) }

        return ReceiptSuggestion(total ?: values.flatten().maxOrNull(), date, merchant)
    }

    private fun toMinor(match: MatchResult): Long? {
        val (_, prefix, digits, fraction, currency) = match.groupValues
        if (prefix.isEmpty() && fraction.isEmpty() && currency.isEmpty()) return null
        val whole = digits.filter(Char::isDigit).toLongOrNull() ?: return null
        if (whole > MAX_MINOR / 100) return null
        val minor = whole * 100 + fraction.ifEmpty { "00" }.toLong()
        return minor.takeIf { it in 1..MAX_MINOR }
    }

    private fun toDate(match: MatchResult): LocalDate? {
        val groups = match.groupValues
        val isoYear = groups[1]
        val year = groups[7]
        return try {
            if (isoYear.isNotEmpty()) {
                LocalDate.of(isoYear.toInt(), groups[2].toInt(), groups[3].toInt())
            } else {
                val fullYear = year.toInt() + if (year.length == 2) 2000 else 0
                LocalDate.of(fullYear, groups[6].toInt(), groups[4].toInt())
            }
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun titleCase(value: String): String {
        val clean = value.trim { !it.isLetterOrDigit() }.replace(spaces, " ")
        // OCR often returns an all-ASCII header. Don't turn every capital I into dotless ı.
        val locale = if (clean.any { it in "İıŞşÇçĞğÜüÖö" }) turkish else Locale.ROOT
        return word.replace(clean) { match ->
            match.value.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
        }.take(40).trimEnd()
    }

    private companion object {
        const val MAX_MINOR = 1_000_000_000L
        val turkish = Locale.forLanguageTag("tr")
        val spaces = Regex("""\s+""")
        val separatorGap = Regex("""(?<=\d[.,])\s+(?=\d)""")
        val word = Regex("""\p{L}+""")
        val totalLabel = Regex("""\b(?:T[O0]PLA[MN]|TUTAR|ODENECEK|TOTAL)\b""")
        val explicitFinal = Regex("""\b(?:GENEL\s+T[O0]PLA[MN]|ODENECEK|GRAND\s+TOTAL)\b""")
        val nonFinal = Regex("""\b(?:ARA|ALT|SUB)\s*(?:T[O0]PLA[MN]|TOTAL)\b|\b(?:KDV|TOP\s*KDV|VERGI|VAT|PARA\s+USTU|INDIRIM|ISKONTO)\b""")
        const val WHOLE = """(?:\d{1,3}(?:[. ,]\d{3})+|\d+)"""
        // A decimal separator is required unless the amount is marked with a currency or POS '*'.
        val money = Regex("""(?<![\d.,/:%₺*\w-])(?<!-\s)([₺*]\s*|(?:TL|TRY)\s+)?($WHOLE)(?:[,.](\d{2}))?(\s*(?:TL\b|TRY\b|₺))?(?![\w.,/:%-]|\s*%)""")
        val amountOnly = Regex("""(?:[₺*]\s*|(?:TL|TRY)\s+)?$WHOLE(?:[,.]\d{2})?\s*(?:TL|TRY|₺)?""")
        val datePattern = Regex("""(?<!\d)(?:(\d{4})-(\d{2})-(\d{2})|(\d{2})([./-])(\d{2})\5(\d{4}|\d{2}))(?!\d)""")
        val dateLabel = Regex("""\b(?:TARIH|DATE)\b""")
        val dateLabelOnly = Regex("""(?:TARIH|DATE)\s*[:=-]?""")
        val phone = Regex("""(?:\+?90\s*)?\(?\d{3,4}\)?[\s.-]+\d{3}[\s.-]*\d{2}[\s.-]*\d{2}""")
        val headerNoise = Regex(
            """\b(?:VKN|TCKN|VERGI|TARIH|SAAT|TEL|TELEFON|FAX|ADRES|MAH(?:ALLESI)?|CAD(?:DESI)?|SOK(?:AK)?|BULVARI|NO|WWW|MERSIS|SUBE\s*KODU|YAZARKASA|MUSTERI\s+NUSHASI|BILGI\s+FISI|SATIS\s+FISI|PERAKENDE\s+SATIS|POS\s+SLIP|HOS\s+GELDINIZ|Z\s*SAYACI)\b""",
        )

        fun normalize(text: String): String = text.replace('ı', 'i').uppercase(Locale.ROOT)
            .replace('İ', 'I').replace('Ç', 'C').replace('Ş', 'S')
            .replace('Ğ', 'G').replace('Ö', 'O').replace('Ü', 'U')
            .replace('\u00a0', ' ').replace('\u202f', ' ').replace('−', '-')
            // OCR often splits "551,15" into "551, 15" and "25.11.2026" into "25.11. 2026".
            .replace(separatorGap, "")
    }
}
