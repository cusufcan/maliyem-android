package com.mercan.maliyem.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Başarılı veritabanı yazımından sonra widget'ların yenilenmesini ister. */
fun interface WidgetUpdater {
    fun refresh()
}

@Singleton
internal class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext context: Context,
) : WidgetUpdater {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        // Yazımı yapan ekran kapansa da yenileme sürer; peş peşe istekler birleşir.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (request in requests) {
                try {
                    MonthWidget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("WidgetUpdater", "Widget yenilenemedi", e)
                }
            }
        }
    }

    override fun refresh() {
        requests.trySend(Unit)
    }
}
