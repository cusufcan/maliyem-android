package com.mercan.maliyem.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.ui.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal fun shouldShowOnboarding(completed: Boolean?, hasUserData: Boolean): Boolean =
    completed != true && !hasUserData

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val settings: AppSettings,
    private val database: MaliyemDatabase,
) : ViewModel() {
    private val _showIntro = MutableStateFlow<Boolean?>(null)
    val showIntro = _showIntro.asStateFlow()

    init {
        viewModelScope.launch {
            val completed = settings.onboardingCompleted
            _showIntro.value = try {
                val hasUserData = completed != true && (
                    settings.appLockEnabled.value || hasStoredUserData() ||
                        // An untouched DB from an older install or Android restore can match the seeds.
                        (completed == null && settings.hadDatabaseAtStartup)
                    )
                val show = shouldShowOnboarding(completed, hasUserData)
                val resolvedCompletion = !show
                if (completed != resolvedCompletion) settings.setOnboardingCompleted(resolvedCompletion)
                show
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("OnboardingViewModel", "Could not check first-run eligibility", e)
                false
            }
        }
    }

    fun finishIntro() {
        settings.setOnboardingCompleted(true)
        _showIntro.value = false
    }

    private suspend fun hasStoredUserData(): Boolean = database.withTransaction {
        database.transactionReportDao().hasUserRecords() ||
            database.accountDao().getAll() != listOf(DefaultAccounts.cash()) ||
            database.categoryDao().getAll().map { it.copy(id = 0) }.toSet() != DefaultCategories.entities().toSet()
    }
}
