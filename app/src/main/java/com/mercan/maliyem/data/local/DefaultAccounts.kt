package com.mercan.maliyem.data.local

import androidx.annotation.StringRes
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mercan.maliyem.R

/** Hazır nakit hesabı; yeni kurulum, v1 geçişi ve v1 yedeği aynı kaydı kullanır. */
object DefaultAccounts {
    const val CASH = "cash"
    const val CASH_ID = 1L

    fun cash() = AccountEntity(id = CASH_ID, key = CASH, icon = "wallet", sortOrder = 0)

    @StringRes
    fun nameRes(key: String?): Int? = R.string.account_cash.takeIf { key == CASH }

    internal fun seed(db: SupportSQLiteDatabase) {
        val cash = cash()
        db.execSQL(
            "INSERT INTO accounts (id, `key`, icon, sortOrder, initialBalanceMinor, archived) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(cash.id, cash.key, cash.icon, cash.sortOrder, cash.initialBalanceMinor, 0),
        )
    }
}
