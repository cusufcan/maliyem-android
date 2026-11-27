package com.mercan.maliyem.notifications

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mercan.maliyem.R
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.ui.settings.AppSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RECURRING_WORK = "maliyem.recurring.daily"

class RecurringWork @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val settings: AppSettings,
) {
    fun refreshSchedule() {
        if (settings.recurringReminders.value) schedule()
        else WorkManager.getInstance(context).cancelAllWorkByTag(RECURRING_WORK)
    }

    // Recalculate 09:00 from the local calendar each day, including across DST changes.
    internal fun schedule(after: LocalDate? = null): Operation {
        val now = ZonedDateTime.now(clock)
        var next = now.toLocalDate().atTime(9, 0).atZone(now.zone)
        if (!next.isAfter(now) || next.toLocalDate() == after) {
            next = next.toLocalDate().plusDays(1).atTime(9, 0).atZone(now.zone)
        }
        val request = OneTimeWorkRequestBuilder<RecurringReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(RECURRING_WORK)
            .build()
        return WorkManager.getInstance(context).enqueueUniqueWork(
            "$RECURRING_WORK:${next.toLocalDate()}", ExistingWorkPolicy.KEEP, request,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RecurringWorkerEntryPoint {
    fun appSettings(): AppSettings
    fun clock(): Clock
    fun recurringRepository(): RecurringRepository
    fun localNotifications(): LocalNotifications
    fun recurringWork(): RecurringWork
}

internal class RecurringReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication<RecurringWorkerEntryPoint>(applicationContext)
        if (!entry.appSettings().recurringReminders.value) return Result.success()
        val today = LocalDate.now(entry.clock())
        return try {
            // Persist tomorrow's request even if today's database operation needs a retry.
            withContext(Dispatchers.IO) { entry.recurringWork().schedule(after = today).result.get() }
            if (!entry.appSettings().recurringReminders.value) return Result.success()
            val recurring = entry.recurringRepository()
            val notifications = entry.localNotifications()
            val count = recurring.generateDue(today, throwOnFailure = true)
            notifications.recurringSummary("added", today, count, R.plurals.notification_recurring_added)
            val tomorrow = recurring.countDueOn(today.plusDays(1))
            notifications.recurringSummary("due", today, tomorrow, R.plurals.notification_recurring_due)
            Result.success()
        } catch (e: SQLiteException) {
            Log.w("RecurringReminderWorker", "Could not read recurring reminders", e)
            Result.retry()
        } catch (e: ExecutionException) {
            Log.w("RecurringReminderWorker", "Could not schedule recurring reminders", e)
            Result.retry()
        }
    }
}
