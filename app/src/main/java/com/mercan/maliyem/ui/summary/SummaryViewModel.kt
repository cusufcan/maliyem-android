package com.mercan.maliyem.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_MONTH = "month"

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: TransactionRepository,
    clock: Clock,
) : ViewModel() {

    // Seçili ay "2026-11" olarak saklanır; süreç ölüp dönünce de aynı ay açılır (ADR-0007).
    private val today = YearMonth.now(clock)
    private val month = savedState.getStateFlow(KEY_MONTH, today.toString())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SummaryUiState?> = month
        .map(YearMonth::parse)
        .flatMapLatest { m ->
            combine(repository.observeMonth(m), repository.observeMonthsWithData()) { rows, withData ->
                val (previous, next) = adjacentMonths(m, withData, today)
                buildSummary(rows, m).copy(previousMonth = previous, nextMonth = next)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = go(uiState.value?.previousMonth)
    fun nextMonth() = go(uiState.value?.nextMonth)

    /** Yeni kayıt başka bir aya girildiyse Özet o aya geçer. */
    fun showMonth(date: LocalDate) = go(YearMonth.from(date))

    /** Snackbar'daki "Geri al": az önce eklenen kayıt silinir (ADR-0008). */
    fun undo(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    private fun go(target: YearMonth?) {
        if (target != null) savedState[KEY_MONTH] = target.toString()
    }
}
