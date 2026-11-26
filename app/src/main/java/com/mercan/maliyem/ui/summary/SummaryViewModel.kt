package com.mercan.maliyem.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.ui.MonthSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val months: MonthSelection,
    private val repository: TransactionRepository,
    clock: Clock,
) : ViewModel() {

    private val today = YearMonth.now(clock)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SummaryUiState?> = combine(months.month, repository.observeMonthsWithData(), ::Pair)
        .flatMapLatest { (m, withData) ->
            val (previous, next) = adjacentMonths(m, withData, today)
            // Boş ayda "geçen ay" kartı için kaydı olan bir önceki ayın neti gerekir.
            val previousRows = previous?.let(repository::observeMonth) ?: flowOf(emptyList())
            combine(repository.observeMonth(m), previousRows) { rows, prevRows ->
                buildSummary(rows, m).copy(
                    previousMonth = previous,
                    nextMonth = next,
                    previousNet = previous?.let { buildSummary(prevRows, it).netMinor },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = uiState.value?.previousMonth?.let(months::select)
    fun nextMonth() = uiState.value?.nextMonth?.let(months::select)
}
