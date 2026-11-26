package com.mercan.maliyem.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val KEY_MONTH = "month"

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    repository: TransactionRepository,
    clock: Clock,
) : ViewModel() {

    // Seçili ay "2026-11" olarak saklanır; süreç ölüp dönünce de aynı ay açılır (ADR-0007).
    private val month = savedState.getStateFlow(KEY_MONTH, YearMonth.now(clock).toString())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SummaryUiState?> = month
        .map(YearMonth::parse)
        .flatMapLatest { m -> repository.observeMonth(m).map { buildSummary(it, m) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = shift(-1)
    fun nextMonth() = shift(1)

    private fun shift(by: Long) {
        savedState[KEY_MONTH] = YearMonth.parse(month.value).plusMonths(by).toString()
    }
}
