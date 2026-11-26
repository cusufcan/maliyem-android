package com.mercan.maliyem.ui.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.di.ElapsedClock
import com.mercan.maliyem.ui.settings.AppSettings
import com.mercan.maliyem.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Kilit oturumu Activity yeniden kurulurken korunur, yeni süreçte doğrulama yeniden istenir. */
@HiltViewModel
internal class AppLockViewModel @Inject constructor(
    private val settings: AppSettings,
    private val clock: ElapsedClock,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    val enabled = settings.appLockEnabled
    val timeout = settings.appLockTimeout

    private var unlocked by mutableStateOf(false)
    val locked get() = enabled.value && !unlocked
    var authenticating by mutableStateOf(false)
        private set

    private var backgroundSince: Long? = null
    private var autoPrompt = true

    init {
        viewModelScope.launch {
            enabled.collect { if (!it) unlocked = false }
        }
    }

    fun onForeground() {
        backgroundSince?.let { since ->
            if (shouldLock(enabled.value, timeout.value, since, clock.now())) unlocked = false
        }
        backgroundSince = null
    }

    fun onBackground() {
        val now = clock.now()
        backgroundSince = now
        if (shouldLock(enabled.value, timeout.value, now, now)) unlocked = false
        // Eski Android sürümlerinde sistem PIN ekranı Activity'yi durdurabilir.
        autoPrompt = !authenticating
    }

    fun takeAutoPrompt(): Boolean {
        val shouldPrompt = autoPrompt && locked && !authenticating
        autoPrompt = false
        return shouldPrompt
    }

    fun beginAuthentication(): Boolean {
        if (authenticating) return false
        authenticating = true
        return true
    }

    fun finishAuthentication(success: Boolean) {
        if (!authenticating) return
        authenticating = false
        if (success) {
            if (!enabled.value) setEnabled(true)
            backgroundSince = null
            unlocked = true
        }
    }

    fun setTimeout(value: LockTimeout) = settings.setAppLockTimeout(value)

    fun disable() {
        setEnabled(false)
        unlocked = false
    }

    private fun setEnabled(value: Boolean) {
        settings.setAppLockEnabled(value)
        widgetUpdater.refresh()
    }
}
