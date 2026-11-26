package com.mercan.maliyem

import android.app.Application
import com.mercan.maliyem.ui.settings.AppSettings
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt'in bağımlılık grafiğini uygulama açılırken kurmasını sağlayan giriş noktası. */
@HiltAndroidApp
class MaliyemApp : Application() {
    @Inject lateinit var settings: AppSettings

    override fun onCreate() {
        super.onCreate()
        // Kayıtlı tema ilk Activity açılmadan uygulanır; açılışta renk sıçraması olmaz.
        settings.applyTheme()
    }
}
