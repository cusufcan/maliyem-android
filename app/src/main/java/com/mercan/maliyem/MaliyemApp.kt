package com.mercan.maliyem

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt'in bağımlılık grafiğini uygulama açılırken kurmasını sağlayan giriş noktası. */
@HiltAndroidApp
class MaliyemApp : Application()
