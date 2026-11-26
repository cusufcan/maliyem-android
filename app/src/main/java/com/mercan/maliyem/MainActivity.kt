package com.mercan.maliyem

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.mercan.maliyem.widget.GlanceWidgetUpdater
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.quickentry.quickEntryType
import com.mercan.maliyem.ui.lock.AppLockAuthentication
import com.mercan.maliyem.ui.lock.AppLockViewModel
import com.mercan.maliyem.ui.lock.SeyirLockedScreen
import com.mercan.maliyem.ui.lock.SeyirSecurityCard
import com.mercan.maliyem.ui.navigation.MaliyemNavHost
import com.mercan.maliyem.ui.theme.MaliyemTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Tek Activity; tüm ekranlar Compose ile çizilir. AppCompat, dil ve tema seçimini uygular. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var recurring: RecurringRepository
    @Inject lateinit var calendar: CalendarDays
    @Inject internal lateinit var widgets: GlanceWidgetUpdater

    private val appLock: AppLockViewModel by viewModels()
    private lateinit var authentication: AppLockAuthentication
    private var authenticationAvailable by mutableStateOf(false)
    private var pendingQuickEntry by mutableStateOf<TransactionType?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun updateSecureWindow() {
        if (appLock.enabled.value) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private const val KEY_QUICK_ENTRY = "pendingQuickEntry"
