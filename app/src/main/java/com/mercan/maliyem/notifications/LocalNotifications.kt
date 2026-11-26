package com.mercan.maliyem.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mercan.maliyem.MainActivity
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.ui.settings.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val recurringSummaries = context.getSharedPreferences("recurring_summaries", Context.MODE_PRIVATE)

    private val localized get() = ContextCompat.getContextForLanguage(context)

    fun createChannels() {
        val strings = localized
        manager.createNotificationChannels(
            listOf(
                NotificationChannel("budgets", strings.getString(R.string.notification_channel_budgets), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel("recurring", strings.getString(R.string.notification_channel_recurring), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun hasPermission(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    internal fun canPostBudgetAlerts() = settings.budgetAlerts.value && canPost("budgets")

    internal fun budgetAlert(key: String, alert: BudgetAlert): Boolean {
        if (!settings.budgetAlerts.value) return false
        val strings = localized
        val title = alert.category?.let { category ->
            category.name ?: DefaultCategories.nameRes(category.key)?.let(strings::getString)
        } ?: strings.getString(R.string.budget_overall)
        val locale = strings.resources.configuration.locales[0]
        val month = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(alert.month)
            .replaceFirstChar { it.titlecase(locale) }
        val body = strings.getString(
            if (alert.threshold == 80) R.string.notification_budget_warning else R.string.notification_budget_limit,
            month,
        )
        return post("budgets", key, title, body)
    }

    @Synchronized
    internal fun recurringSummary(
        kind: String,
        date: LocalDate,
        count: Int,
        @PluralsRes message: Int,
    ) {
        if (count <= 0 || !settings.recurringReminders.value) return
        val day = date.toString()
        if (recurringSummaries.getString(kind, null) == day) return
        val strings = localized
        val sent = post(
            "recurring", "$kind:$day", strings.getString(R.string.notification_channel_recurring),
            strings.resources.getQuantityString(message, count, count),
        )
        if (sent) recurringSummaries.edit { putString(kind, day) }
    }

    private fun canPost(channel: String) = hasPermission() &&
        (manager.getNotificationChannel(channel)?.importance ?: NotificationManager.IMPORTANCE_NONE) != NotificationManager.IMPORTANCE_NONE

    private fun post(channel: String, tag: String, title: String, body: String): Boolean {
        if (!canPost(channel)) return false
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Amount-free text also protects notifications posted before the app lock was enabled.
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_seyir_nav_summary)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        return try {
            manager.notify(tag, 0, notification)
            true
        } catch (_: SecurityException) {
            // Permission may have been revoked since the check above.
            false
        }
    }
}
