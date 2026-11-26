package com.mercan.maliyem.quickentry

import com.mercan.maliyem.data.local.TransactionType

internal const val NEW_EXPENSE_LINK = "maliyem://quick-entry/expense"
internal const val NEW_INCOME_LINK = "maliyem://quick-entry/income"

/** Yalnız hızlı kayıt bağlantıları, formun başlangıç türünü belirler. */
internal fun quickEntryType(link: String?): TransactionType? = when (link) {
    NEW_EXPENSE_LINK -> TransactionType.EXPENSE
    NEW_INCOME_LINK -> TransactionType.INCOME
    else -> null
}
