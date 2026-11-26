package com.mercan.maliyem.quickentry

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.mercan.maliyem.MainActivity
import com.mercan.maliyem.data.local.TransactionType

/** Statik kısayolun görev temizleme bayrakları boş görevde kalır. */
class QuickEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            quickEntryType(intent.dataString)?.let { startActivity(mainActivityIntent(this, it)) }
        }
        finish()
    }
}

/** Ayrı bağlantılar widget düğmelerinin PendingIntent kimliklerini de ayırır. */
internal fun mainActivityIntent(context: Context, type: TransactionType? = null): Intent {
    val link = when (type) {
        TransactionType.EXPENSE -> NEW_EXPENSE_LINK
        TransactionType.INCOME -> NEW_INCOME_LINK
        null -> null
    }
    return Intent(context, MainActivity::class.java)
        .setAction(if (link == null) Intent.ACTION_MAIN else Intent.ACTION_VIEW)
        .setData(link?.let(Uri::parse))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
