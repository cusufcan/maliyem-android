package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.data.local.RecurringRuleEntity
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal const val MAX_OCCURRENCES = 400

/** Tek çalıştırmada üretilecek tarihler; bugün ve bitiş günü dahildir. */
fun occurrences(rule: RecurringRuleEntity, today: LocalDate): List<LocalDate> {
    if (rule.paused) return emptyList()
    val last = minOf(today, rule.endDate ?: today)
    return generateSequence(rule.nextDate) { nextOccurrence(rule, it) }
        .takeWhile { it <= last && it < LocalDate.MAX }
        .take(MAX_OCCURRENCES)
        .toList()
}

/** Takvim aralığı tükenirse MAX bir daha kayıt üretilmeyeceğini gösterir. */
internal fun nextOccurrence(rule: RecurringRuleEntity, date: LocalDate): LocalDate =
    if (date == LocalDate.MAX) date else occurrenceOnOrAfter(rule.startDate, rule.frequency, date.plusDays(1))

/** Tarih sıklık değişiminde ileriye hizalanır; ayın günü her zaman başlangıçtan gelir. */
internal fun occurrenceOnOrAfter(anchor: LocalDate, frequency: RecurringFrequency, earliest: LocalDate): LocalDate {
    val from = maxOf(earliest, anchor)
    return try {
        when (frequency) {
            RecurringFrequency.WEEKLY -> {
                val days = ChronoUnit.DAYS.between(anchor, from)
                anchor.plusWeeks((days + 6) / 7)
            }
            RecurringFrequency.MONTHLY -> {
                val month = YearMonth.from(from)
                val candidate = month.anchoredDay(anchor.dayOfMonth)
                if (candidate >= from) candidate else month.plusMonths(1).anchoredDay(anchor.dayOfMonth)
            }
            RecurringFrequency.YEARLY -> {
                val month = YearMonth.of(from.year, anchor.month)
                val candidate = month.anchoredDay(anchor.dayOfMonth)
                if (candidate >= from) candidate else month.plusYears(1).anchoredDay(anchor.dayOfMonth)
            }
        }
    } catch (_: DateTimeException) {
        LocalDate.MAX
    }
}

private fun YearMonth.anchoredDay(day: Int) = atDay(minOf(day, lengthOfMonth()))
