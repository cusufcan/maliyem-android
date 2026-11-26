package com.mercan.maliyem

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mercan.maliyem.ui.CalendarDays
import com.mercan.maliyem.ui.add.AddTransactionViewModel
import com.mercan.maliyem.widget.GlanceWidgetUpdater
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.notifications.LocalNotifications
import com.mercan.maliyem.notifications.RecurringWork
import com.mercan.maliyem.quickentry.quickEntryType
import com.mercan.maliyem.ui.lock.AppLockAuthentication
import com.mercan.maliyem.ui.lock.AppLockViewModel
import com.mercan.maliyem.ui.lock.SeyirLockedScreen
import com.mercan.maliyem.ui.lock.SeyirSecurityCard
import com.mercan.maliyem.ui.navigation.MaliyemNavHost
import com.mercan.maliyem.ui.settings.AppSettings
import com.mercan.maliyem.ui.settings.NotificationsCard
import com.mercan.maliyem.ui.theme.MaliyemTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Tek Activity; tüm ekranlar Compose ile çizilir. AppCompat, dil ve tema seçimini uygular. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var recurring: RecurringRepository
    @Inject lateinit var calendar: CalendarDays
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var notifications: LocalNotifications
    @Inject lateinit var recurringWork: RecurringWork
    @Inject internal lateinit var widgets: GlanceWidgetUpdater

    private val addTransaction: AddTransactionViewModel by viewModels()
    private val appLock: AppLockViewModel by viewModels()
    private lateinit var authentication: AppLockAuthentication
    private var authenticationAvailable by mutableStateOf(false)
    private var pendingQuickEntry by mutableStateOf<TransactionType?>(null)

    private var notificationsAllowed by mutableStateOf(false)
    private var notificationPermissionRequested by mutableStateOf(false)
    private val canRequestNotificationPermission
        get() = Build.VERSION.SDK_INT >= 33 && !notificationPermissionRequested

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = notifications.hasPermission()
    }

    private val takeReceipt = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        addTransaction.finishReceiptCapture(taken)
    }

    private val pickReceipt = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { addTransaction.importReceipt(it) }
    }

    private fun launchReceiptSource(launch: () -> Unit) {
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            addTransaction.receiptCaptureFailed()
        } catch (_: SecurityException) {
            addTransaction.receiptCaptureFailed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notifications.createChannels()
        notificationsAllowed = notifications.hasPermission()
        notificationPermissionRequested = settings.notificationPermissionRequested
        updateSecureWindow()
        authentication = AppLockAuthentication(this) { success ->
            appLock.finishAuthentication(success)
            updateSecureWindow()
            authenticationAvailable = authentication.isAvailable()
        }
        lifecycleScope.launch { appLock.enabled.collect { updateSecureWindow() } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                calendar.days.collect {
                    widgets.refreshIfMonthChanged()
                    recurring.generateDue()
                }
            }
        }
        // Yeniden kurulan alt sayfa kendi taslağını saklar; açılış eylemi tekrarlanmaz.
        pendingQuickEntry = if (savedInstanceState == null) quickEntryType(intent.dataString)
            else savedInstanceState.getString(KEY_QUICK_ENTRY)?.let(TransactionType::valueOf)
        // İçerik durum ve gezinme çubuklarının altına uzanır; boşluklar Compose'da verilir.
        enableEdgeToEdge()
        setContent {
            val screenState = rememberSaveableStateHolder()
            val lockEnabled by appLock.enabled.collectAsState()
            MaliyemTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (lockEnabled && appLock.locked) {
                        SeyirLockedScreen(authenticationAvailable, appLock.authenticating, ::authenticate)
                    } else {
                        screenState.SaveableStateProvider("app") {
                            MaliyemNavHost(
                                quickEntry = pendingQuickEntry,
                                onQuickEntryConsumed = { pendingQuickEntry = null },
                                securityCard = { SecurityCard(lockEnabled) },
                                notificationsCard = { NotificationSettings() },
                                onBudgetCreated = ::requestNotificationPermission,
                                add = addTransaction,
                                onReceiptCamera = { launchReceiptSource { takeReceipt.launch(addTransaction.startReceiptCapture()) } },
                                onReceiptGallery = {
                                    launchReceiptSource {
                                        pickReceipt.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        quickEntryType(intent.dataString)?.let { pendingQuickEntry = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_QUICK_ENTRY, pendingQuickEntry?.name)
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
    }

    override fun onPostResume() {
        super.onPostResume()
        notificationsAllowed = notifications.hasPermission()
        authenticationAvailable = authentication.isAvailable()
        if (appLock.takeAutoPrompt()) authenticate()
    }

    override fun onStop() {
        if (!isChangingConfigurations) appLock.onBackground()
        super.onStop()
    }

    private fun authenticate() {
        authenticationAvailable = authentication.isAvailable()
        if (authenticationAvailable && appLock.beginAuthentication()) authentication.authenticate()
    }

    @Composable
    private fun SecurityCard(enabled: Boolean) {
        val timeout by appLock.timeout.collectAsStateWithLifecycle()
        SeyirSecurityCard(
            enabled, authenticationAvailable, appLock.authenticating, timeout,
            onEnabledChange = { turnOn ->
                if (turnOn) {
                    authenticate()
                } else {
                    appLock.disable()
                    updateSecureWindow()
                }
            },
            onTimeoutChange = appLock::setTimeout,
        )
    }

    private fun requestNotificationPermission() {
        if (notifications.hasPermission() || !canRequestNotificationPermission) return
        notificationPermissionRequested = true
        settings.notificationPermissionRequested = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Composable
    private fun NotificationSettings() {
        val budgetAlerts by settings.budgetAlerts.collectAsStateWithLifecycle()
        val recurringReminders by settings.recurringReminders.collectAsStateWithLifecycle()
        NotificationsCard(
            budgetAlerts, recurringReminders, notificationsAllowed, canRequestNotificationPermission,
            onBudgetAlertsChange = { enabled ->
                settings.setBudgetAlerts(enabled)
                if (enabled) requestNotificationPermission()
            },
            onRecurringRemindersChange = { enabled ->
                settings.setRecurringReminders(enabled)
                recurringWork.refreshSchedule()
                if (enabled) requestNotificationPermission()
            },
            onOpenSystemSettings = {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            },
        )
    }

    private fun updateSecureWindow() {
        if (appLock.enabled.value) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private const val KEY_QUICK_ENTRY = "pendingQuickEntry"
