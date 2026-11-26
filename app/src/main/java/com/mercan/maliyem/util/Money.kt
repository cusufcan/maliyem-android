package com.mercan.maliyem.util

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

/**
 * [minor] kuruşu ekrana yazar. Eksi tutarlar her zaman U+2212 ile başlar;
 * [signed] true ise pozitif tutara "+" eklenir (gelir satırı gibi).
 */
fun formatMoney(minor: Long, locale: Locale, signed: Boolean = false): String {
    val sign = when {
        minor < 0 -> MINUS.toString()
        signed && minor > 0 -> "+"
        else -> ""
    }
    // Long.MIN_VALUE'nun mutlak değeri Long'a sığmaz; ULong ile güvenli.
    val abs = if (minor < 0) (-(minor + 1)).toULong() + 1u else minor.toULong()
    val whole = (abs / 100u).toString()
    val fraction = (abs % 100u).toString().padStart(2, '0')
    val turkish = locale.isTurkish()
    val group = if (turkish) '.' else ','
    val decimal = if (turkish) ',' else '.'
    val grouped = whole.reversed().chunked(3).joinToString(group.toString()).reversed()
    val number = "$grouped$decimal$fraction"
    return if (turkish) "$sign$number$NBSP$LIRA" else "$sign$LIRA$number"
}

/**
 * Kullanıcının yazdığı tutarı kuruşa çevirir: Türkçede "1.234,56", diğer dillerde "1,234.56".
 * Boşluk ve ₺ yok sayılır. Geçersiz giriş, eksi tutar, ikiden çok kuruş basamağı ya da
 * Long'a sığmayan tutar için null döner. "0" geçerlidir; sıfırdan büyük olma kuralı formdadır.
 */
fun parseMoney(input: String, locale: Locale): Long? {
    val turkish = locale.isTurkish()
    val group = if (turkish) '.' else ','
    val decimal = if (turkish) ',' else '.'
    val cleaned = input.filterNot { it.isWhitespace() || it == LIRA }
    if (cleaned.isEmpty()) return null

    val parts = cleaned.split(decimal)
    if (parts.size > 2) return null
    val whole = parts[0].replace(group.toString(), "")
    val fraction = parts.getOrElse(1) { "" }
    if (whole.isEmpty() && fraction.isEmpty()) return null
    if (!whole.all { it in '0'..'9' } || !fraction.all { it in '0'..'9' }) return null
    if (fraction.length > 2) return null

    return try {
        val wholeMinor = Math.multiplyExact(whole.ifEmpty { "0" }.toLong(), 100L)
        Math.addExact(wholeMinor, fraction.padEnd(2, '0').toLong())
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}
