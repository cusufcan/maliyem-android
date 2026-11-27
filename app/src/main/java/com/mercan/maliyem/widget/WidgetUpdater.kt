package com.mercan.maliyem.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mercan.maliyem.ui.settings.AppSettings
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Başarılı veritabanı yazımından sonra widget'ların yenilenmesini ister. */
fun interface WidgetUpdater {
    fun refresh()
}

@Singleton
internal class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
) : WidgetUpdater {
    // A Glance tree can be ready before the user changes the setting and published afterward.
    // Check it under the same mutex that protects the lock transition and immediate mask.
    private val publication = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
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

    suspend fun enableLock() = withContext(NonCancellable + Dispatchers.IO) {
        publication.withLock {
            try {
                publishMaskAndRefresh(enablingLock = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WidgetUpdater", "Widget maskesi gönderilemedi", e)
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "maliyem.widget.mask",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<WidgetMaskRetry>().build(),
                ).result.get()
            }
        }
    }

    suspend fun retryMask() = publication.withLock {
        if (settings.appLockEnabled.value) publishMaskAndRefresh()
    }

    private suspend fun publishMaskAndRefresh(enablingLock: Boolean = false) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val glance = GlanceAppWidgetManager(context)
            val provider = ComponentName(context, MonthWidgetReceiver::class.java)
            for (id in manager.getAppWidgetIds(provider)) {
                val options = manager.getAppWidgetOptions(id)
                val mask = MonthWidget(maskOnly = true).compose(context, glance.getGlanceIdBy(id), options)
                manager.updateAppWidget(id, mask)
                storeWidgetMonth(context, id, YearMonth.now())
            }
        } finally {
            if (enablingLock) settings.setAppLockEnabled(true)
            // updateAll schedules Glance work and refreshes active sessions. The direct update above
            // also replaces the RemoteViews currently cached by the launcher.
            MonthWidget().updateAll(context)
        }
    }

    suspend fun publishIfCurrent(locked: Boolean, publish: suspend () -> Boolean): Boolean =
        publication.withLock {
            locked == settings.appLockEnabled.value && publish()
        }

    fun refreshIfMonthChanged() {
        if (widgetsWithStaleMonth(context).isNotEmpty()) refresh()
    }

    override fun refresh() {
        requests.trySend(Unit)
    }
}

internal class WidgetMaskRetry(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication<MonthWidgetEntryPoint>(applicationContext).updater().retryMask()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("WidgetUpdater", "Widget maskesi yeniden gönderilemedi", e)
        Result.retry()
    }
}

internal fun widgetsWithStaleMonth(context: Context): IntArray {
    val month = YearMonth.now().toString()
    val prefs = monthPreferences(context)
    val provider = ComponentName(context, MonthWidgetReceiver::class.java)
    return AppWidgetManager.getInstance(context).getAppWidgetIds(provider)
        .filter { prefs.getString(it.toString(), null) != month }.toIntArray()
}

private fun monthPreferences(context: Context) = context.getSharedPreferences("widget_months", Context.MODE_PRIVATE)

internal fun storeWidgetMonth(context: Context, id: Int, month: YearMonth?) {
    monthPreferences(context).edit { putString(id.toString(), month?.toString()) }
}
