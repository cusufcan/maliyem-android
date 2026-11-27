package com.mercan.maliyem.ui.add

import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransferEntity
import com.mercan.maliyem.data.receipt.ReceiptSuggestion
import com.mercan.maliyem.util.moneyDecimalSeparator
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
    val accountId: Long?,
)

enum class AddFormError { AMOUNT_EMPTY, AMOUNT_INVALID, AMOUNT_ZERO, CATEGORY_MISSING, ACCOUNT_MISSING }

/**
 * Tutarı kuruşa çevirir. Klavye bazen yerel ondalık ayırıcı yerine öbür işareti verir
 * ("120.50" Türkçede): ondalık ayırıcı hiç yoksa ve tek grup ayırıcının ardında 1-2 rakam
 * varsa o işaret ondalık sayılır. "1.250" gibi üç rakamlı grup bin ayırıcısı olarak kalır.
 */
fun parseAmount(text: String, locale: Locale): Long? =
    parseMoney(text, locale, alternateDecimal = true)

private fun amountError(text: String, minor: Long?): AddFormError? = when {
    text.isBlank() -> AddFormError.AMOUNT_EMPTY
    minor == null -> AddFormError.AMOUNT_INVALID
    minor == 0L -> AddFormError.AMOUNT_ZERO
    else -> null
}

fun amountError(text: String, locale: Locale): AddFormError? =
    amountError(text, parseAmount(text, locale))

/** Seçili kategori bu türün listesinde yoksa (tür değişti ya da ilk açılış) ilk kategori seçilir. */
fun pickCategory(selected: Long?, categories: List<CategoryEntity>): Long? =
    selected?.takeIf { id -> categories.any { it.id == id } } ?: categories.firstOrNull()?.id

/** Arşivlenmiş hesap yalnız kendi eski kaydında seçili kalabilir. */
fun pickAccount(selected: Long?, previous: Long?, accounts: List<AccountEntity>): Long? =
    selected?.takeIf { id -> accounts.any { it.id == id && (!it.archived || id == previous) } }
        ?: accounts.firstOrNull { !it.archived && it.key == DefaultAccounts.CASH }?.id
        ?: accounts.firstOrNull { !it.archived }?.id

fun cleanNote(note: String): String? = note.trim().ifEmpty { null }

/** Form geçerliyse kayıt, değilse ilk hata. [form]'un kategorisi [pickCategory] ile çözülmüş olmalı. */
fun buildEntity(form: AddForm, locale: Locale, createdAt: Long): Result {
    val minor = parseAmount(form.amountText, locale)
    amountError(form.amountText, minor)?.let { return Result.Invalid(it) }
    val categoryId = form.categoryId ?: return Result.Invalid(AddFormError.CATEGORY_MISSING)
    val accountId = form.accountId ?: return Result.Invalid(AddFormError.ACCOUNT_MISSING)
    return Result.Valid(
        TransactionEntity(
            type = form.type,
            amountMinor = checkNotNull(minor),
            categoryId = categoryId,
            date = form.date,
            note = cleanNote(form.note),
            createdAt = createdAt,
            accountId = accountId,
        ),
    )
}

/** Aynı hesap ve geçersiz tutar için transfer kaydı oluşturulmaz. */
fun buildTransfer(form: AddForm, toAccountId: Long?, amountMinor: Long?, createdAt: Long): TransferEntity? {
    val from = form.accountId ?: return null
    val to = toAccountId?.takeIf { it != from } ?: return null
    val minor = amountMinor?.takeIf { it > 0 } ?: return null
    return TransferEntity(
        fromAccountId = from, toAccountId = to, amountMinor = minor,
        date = form.date, note = cleanNote(form.note), createdAt = createdAt,
    )
}

sealed interface Result {
    data class Valid(val entity: TransactionEntity) : Result
    data class Invalid(val error: AddFormError) : Result
}

/** Düzenlemede tutar alanına yazılacak metin: 12050 → "120,50" (TR), 300000 → "3000". Binlik ayırıcı konmaz. */
fun amountInputText(minor: Long, locale: Locale): String {
    val separator = moneyDecimalSeparator(locale)
    val whole = minor / 100
    val fraction = minor % 100
    val sign = if (minor < 0 && whole == 0L) "-" else ""
    return if (fraction == 0L) whole.toString()
        else "$sign$whole$separator${kotlin.math.abs(fraction).toString().padStart(2, '0')}"
}


internal const val RECEIPT_AMOUNT = 1
internal const val RECEIPT_DATE = 2
internal const val RECEIPT_NOTE = 4

internal data class ReceiptFields(val amount: String, val date: String?, val note: String, val filled: Int = 0) {
    fun withSuggestion(suggestion: ReceiptSuggestion, touched: Int, today: LocalDate, locale: Locale): ReceiptFields {
        var nextFilled = 0
        fun <T> merge(field: Int, current: T, empty: T, suggested: T?): T {
            val wasFilled = filled and field != 0
            if (touched and field != 0 || (current != empty && !wasFilled)) return current
            if (suggested == null) return empty
            nextFilled = nextFilled or field
            return suggested
        }
        val amount = merge(RECEIPT_AMOUNT, amount, "", suggestion.amountMinor?.let { amountInputText(it, locale) })
        // A receipt must not give a new entry a future date.
        val date = merge(RECEIPT_DATE, date, null, suggestion.date?.takeIf { it < today }?.toString())
        val note = merge(RECEIPT_NOTE, note, "", suggestion.merchant)
        return ReceiptFields(amount, date, note, nextFilled)
    }

    fun withoutSuggestion(): ReceiptFields = ReceiptFields(
        amount = if (filled and RECEIPT_AMOUNT != 0) "" else amount,
        date = if (filled and RECEIPT_DATE != 0) null else date,
        note = if (filled and RECEIPT_NOTE != 0) "" else note,
    )
}
