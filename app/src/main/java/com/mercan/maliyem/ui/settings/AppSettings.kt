package com.mercan.maliyem.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.mercan.maliyem.ui.lock.LockTimeout
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tema seçimi; AppCompat gece kipine çevrilir. */
enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
}

/** Uygulama dili; seçimi Android (12 ve altında AppCompat) saklar. SYSTEM telefonun dilini izler. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    TURKISH("tr"),
    ;

    companion object {
        /** "tr", "en-US" ya da "" gibi etiket listesinden seçeneği bulur; tanınmazsa SYSTEM. */
        fun fromTags(tags: String): AppLanguage {
            val first = tags.substringBefore(',').substringBefore('-')
            return entries.firstOrNull { it.tag.isNotEmpty() && it.tag == first } ?: SYSTEM
        }
    }
}

/**
 * Tema ve uygulama kilidi SharedPreferences'ta, dil AppCompat'ta tutulur. İlk karede gereken
 * ayarlar eşzamanlı okunur (ADR-0013, ADR-0025).
 */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_APP_LOCK_ENABLED, false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _appLockTimeout = MutableStateFlow(
        LockTimeout.entries.firstOrNull { it.name == prefs.getString(KEY_APP_LOCK_TIMEOUT, null) } ?: LockTimeout.IMMEDIATE,
    )
    val appLockTimeout: StateFlow<LockTimeout> = _appLockTimeout.asStateFlow()

    internal fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, enabled) }
        _appLockEnabled.value = enabled
    }

    internal fun setAppLockTimeout(timeout: LockTimeout) {
        prefs.edit { putString(KEY_APP_LOCK_TIMEOUT, timeout.name) }
        _appLockTimeout.value = timeout
    }

    /** Uygulama açılırken kayıtlı temayı uygular. */
    fun applyTheme() = AppCompatDelegate.setDefaultNightMode(_theme.value.nightMode)

    fun setTheme(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _theme.value = mode
        applyTheme()
    }

    fun language(): AppLanguage = AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /** Dili uygular; Android Activity'yi yeni dille yeniden kurar. */
    fun setLanguage(language: AppLanguage) =
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))

    fun lastAccountId(): Long? = prefs.getLong(KEY_LAST_ACCOUNT, 0).takeIf { it > 0 }

    fun setLastAccountId(id: Long?) {
        prefs.edit { if (id == null) remove(KEY_LAST_ACCOUNT) else putLong(KEY_LAST_ACCOUNT, id) }
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_LAST_ACCOUNT = "lastAccount"
        const val KEY_APP_LOCK_ENABLED = "appLockEnabled"
        const val KEY_APP_LOCK_TIMEOUT = "appLockTimeout"
    }
}
