package com.mercan.maliyem

import android.app.Application
import com.mercan.maliyem.data.receipt.ReceiptStore
import com.mercan.maliyem.notifications.LocalNotifications
import com.mercan.maliyem.notifications.RecurringWork
import com.mercan.maliyem.ui.settings.AppSettings
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt'in bağımlılık grafiğini uygulama açılırken kurmasını sağlayan giriş noktası. */
@HiltAndroidApp
class MaliyemApp : Application() {
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var notifications: LocalNotifications
    @Inject lateinit var recurringWork: RecurringWork

    @Inject lateinit var receipts: ReceiptStore

    override fun onCreate() {
        super.onCreate()
        // Kayıtlı tema ilk Activity açılmadan uygulanır; açılışta renk sıçraması olmaz.
        settings.applyTheme()
        notifications.createChannels()
        recurringWork.refreshSchedule()
        receipts.cleanOnStart()
    }
}
