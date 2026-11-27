package com.mercan.maliyem.ui.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.di.ElapsedClock
import com.mercan.maliyem.ui.settings.AppSettings
import com.mercan.maliyem.widget.GlanceWidgetUpdater
import android.util.Log
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Kilit oturumu Activity yeniden kurulurken korunur, yeni süreçte doğrulama yeniden istenir. */
@HiltViewModel
internal class AppLockViewModel @Inject constructor(
    private val settings: AppSettings,
    private val clock: ElapsedClock,
    private val widgetUpdater: GlanceWidgetUpdater,
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
        if (!success || enabled.value) {
            authenticating = false
            if (success) unlock()
            return
        }
        unlock()
        viewModelScope.launch {
            try {
                widgetUpdater.enableLock()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AppLockViewModel", "Widget maskesi gönderilemedi", e)
                widgetUpdater.refresh()
            } finally {
                authenticating = false
            }
        }
    }

    fun setTimeout(value: LockTimeout) = settings.setAppLockTimeout(value)

    fun disable() {
        settings.setAppLockEnabled(false)
        widgetUpdater.refresh()
        unlocked = false
    }

    private fun unlock() {
        backgroundSince = null
        unlocked = true
    }
}
