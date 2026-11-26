package com.mercan.maliyem.util

import java.math.BigInteger
import java.util.Locale

/*
 * Tutar biçimleme ve ayrıştırma tek yerde (ADR-0002). Tutar kuruş cinsinden Long tutulur.
 * Biçim dile göre: Türkçe "12.450,00 ₺", diğer diller "₺12,450.00". Para birimi sabit TL.
 * JDK'nın para biçimi sürüme göre değiştiği için ayraçlar burada elle seçilir.
 */

private const val MINUS = '−'
private const val NBSP = ' '
private const val LIRA = '₺'

private fun Locale.isTurkish() = language == "tr"

internal fun moneyDecimalSeparator(locale: Locale) = if (locale.isTurkish()) ',' else '.'

private val minorPerLira = BigInteger.valueOf(100)

/** Pozitif tutarlarda kesir en yakın tam yüzdeye, yarımlar yukarı yuvarlanır. */
internal fun roundedPercent(part: BigInteger, total: BigInteger): BigInteger =
    (part * minorPerLira + total.shiftRight(1)) / total

/**
 * [minor] kuruşu ekrana yazar. Eksi tutarlar her zaman U+2212 ile başlar;
 * [signed] true ise pozitif tutara "+" eklenir (gelir satırı gibi).
 */
fun formatMoney(minor: Long, locale: Locale, signed: Boolean = false): String =
    formatMoney(minor.toBigInteger(), locale, signed)

fun formatMoney(minor: BigInteger, locale: Locale, signed: Boolean = false): String {
    val sign = when {
        minor.signum() < 0 -> MINUS.toString()
        signed && minor.signum() > 0 -> "+"
        else -> ""
    }
    val (wholeMinor, fractionMinor) = minor.abs().divideAndRemainder(minorPerLira)
    val whole = wholeMinor.toString()
    val fraction = fractionMinor.toString().padStart(2, '0')
    val turkish = locale.isTurkish()
    val group = if (turkish) '.' else ','
    val decimal = moneyDecimalSeparator(locale)
    val grouped = whole.reversed().chunked(3).joinToString(group.toString()).reversed()
    val number = "$grouped$decimal$fraction"
    return if (turkish) "$sign$number$NBSP$LIRA" else "$sign$LIRA$number"
}

/**
 * Kullanıcının yazdığı tutarı kuruşa çevirir: Türkçede "1.234,56", diğer dillerde "1,234.56".
 * Dış boşluklar ve baştaki veya sondaki ₺ yok sayılır. Geçersiz giriş, eksi tutar, ikiden çok kuruş basamağı ya da
 * Long'a sığmayan tutar için null döner. "0" geçerlidir; sıfırdan büyük olma kuralı formdadır.
 */
fun parseMoney(input: String, locale: Locale): Long? =
    parseMoney(input, locale, alternateDecimal = false)

internal fun parseMoney(input: String, locale: Locale, alternateDecimal: Boolean, allowNegative: Boolean = false): Long? {
    val decimal = moneyDecimalSeparator(locale)
    val group = if (decimal == ',') '.' else ','
    val raw = input.trim()
    val negative = allowNegative && (raw.startsWith('-') || raw.startsWith(MINUS))
    val trimmed = if (negative) raw.drop(1).trimStart() else raw
    val number = when {
        trimmed.startsWith(LIRA) -> trimmed.drop(1).trimStart()
        trimmed.endsWith(LIRA) -> trimmed.dropLast(1).trimEnd()
        else -> trimmed
    }
    val digits = buildString(number.length) {
        number.forEach { append(it.digitToIntOrNull()?.digitToChar() ?: it) }
    }
    val cleaned = if (alternateDecimal && decimal !in digits && digits.count { it == group } == 1 &&
        digits.substringAfter(group).length in 1..2
    ) digits.replace(group, decimal) else digits
    if (cleaned.isEmpty()) return null

    val parts = cleaned.split(decimal)
    if (parts.size > 2) return null
    val groups = parts[0].split(group)
    if (groups.size > 1 && (groups.first().length !in 1..3 || groups.drop(1).any { it.length != 3 })) return null
    val whole = groups.joinToString("")
    val fraction = parts.getOrElse(1) { "" }
    if (whole.isEmpty() && fraction.isEmpty()) return null
    if (!whole.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) return null
    if (fraction.length > 2) return null

    return try {
        val wholeMinor = whole.ifEmpty { "0" }.toLong()
        val cents = fraction.padEnd(2, '0').toLong()
        if (negative) Math.subtractExact(Math.multiplyExact(-wholeMinor, 100L), cents)
        else Math.addExact(Math.multiplyExact(wholeMinor, 100L), cents)
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

/**
 * [formatMoney] çıktısını büyük ve küçük yazılacak iki parçaya ayırır:
 * Türkçe "+9.623,35 ₺" → "+9.623" ve ",35 ₺"; İngilizce "+₺9,623.35" → "+₺9,623" ve ".35".
 */
fun splitFraction(formatted: String, locale: Locale): Pair<String, String> {
    val at = formatted.lastIndexOf(moneyDecimalSeparator(locale))
    return if (at < 0) formatted to "" else formatted.substring(0, at) to formatted.substring(at)
}
