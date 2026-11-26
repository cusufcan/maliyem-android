package com.mercan.maliyem.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
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
 * Tema SharedPreferences'ta, dil AppCompat'ta tutulur. Tema ilk karede gerekli olduğu için
 * eşzamanlı okunur (ADR-0013).
 */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

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

    private companion object { const val KEY_THEME = "theme" }
}
