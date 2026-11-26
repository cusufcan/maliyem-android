package com.mercan.maliyem.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    categories: CategoryRepository,
) : ViewModel() {
    val categoryCount: StateFlow<Int?> = categories.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val theme: StateFlow<ThemeMode> = settings.theme

    // Dil değişince Activity yeniden kurulur ama ViewModel yaşar; değer bu yüzden durumda tutulur.
    private val _language = MutableStateFlow(settings.language())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setTheme(mode: ThemeMode) = settings.setTheme(mode)

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        settings.setLanguage(language)
    }
}
