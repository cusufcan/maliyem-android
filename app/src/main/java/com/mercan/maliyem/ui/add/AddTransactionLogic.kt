package com.mercan.maliyem.ui.add

import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.util.parseMoney
import java.time.LocalDate
import java.util.Locale

/** Alt sayfadaki form; tutar kullanıcının yazdığı haliyle durur. */
data class AddForm(
    val type: TransactionType,
    val amountText: String,
    val categoryId: Long?,
    val date: LocalDate,
    val note: String,
)

enum class AddFormError { AMOUNT_EMPTY, AMOUNT_INVALID, AMOUNT_ZERO, CATEGORY_MISSING }

/**
 * Tutarı kuruşa çevirir. Klavye bazen yerel ondalık ayırıcı yerine öbür işareti verir
 * ("120.50" Türkçede): ondalık ayırıcı hiç yoksa ve tek grup ayırıcının ardında 1-2 rakam
 * varsa o işaret ondalık sayılır. "1.250" gibi üç rakamlı grup bin ayırıcısı olarak kalır.
 */
fun parseAmount(text: String, locale: Locale): Long? {
    val turkish = locale.language == "tr"
    val group = if (turkish) '.' else ','
    val decimal = if (turkish) ',' else '.'
    val trimmed = text.trim()
    val lenient = if (decimal !in trimmed && trimmed.count { it == group } == 1 &&
        trimmed.substringAfter(group).length in 1..2
    ) trimmed.replace(group, decimal) else trimmed
    return parseMoney(lenient, locale)
}

fun amountError(text: String, locale: Locale): AddFormError? {
    if (text.isBlank()) return AddFormError.AMOUNT_EMPTY
    val minor = parseAmount(text, locale) ?: return AddFormError.AMOUNT_INVALID
    return if (minor == 0L) AddFormError.AMOUNT_ZERO else null
}

/** Seçili kategori bu türün listesinde yoksa (tür değişti ya da ilk açılış) ilk kategori seçilir. */
fun pickCategory(selected: Long?, categories: List<CategoryEntity>): Long? =
    selected?.takeIf { id -> categories.any { it.id == id } } ?: categories.firstOrNull()?.id

fun cleanNote(note: String): String? = note.trim().ifEmpty { null }

/** Form geçerliyse kayıt, değilse ilk hata. [form]'un kategorisi [pickCategory] ile çözülmüş olmalı. */
fun buildEntity(form: AddForm, locale: Locale, createdAt: Long): Result {
    amountError(form.amountText, locale)?.let { return Result.Invalid(it) }
    val categoryId = form.categoryId ?: return Result.Invalid(AddFormError.CATEGORY_MISSING)
    return Result.Valid(
        TransactionEntity(
            type = form.type,
            amountMinor = parseAmount(form.amountText, locale)!!,
            categoryId = categoryId,
            date = form.date,
            note = cleanNote(form.note),
            createdAt = createdAt,
        ),
    )
}

sealed interface Result {
    data class Valid(val entity: TransactionEntity) : Result
    data class Invalid(val error: AddFormError) : Result
}

/** Düzenlemede tutar alanına yazılacak metin: 12050 → "120,50" (TR), 300000 → "3000". Binlik ayırıcı konmaz. */
fun amountInputText(minor: Long, locale: Locale): String {
    val separator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val whole = minor / 100
    val fraction = minor % 100
    return if (fraction == 0L) whole.toString() else "$whole$separator${fraction.toString().padStart(2, '0')}"
}
