package com.mercan.maliyem.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.ui.CalendarDays
import com.mercan.maliyem.ui.MonthSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val months: MonthSelection,
    private val repository: TransactionRepository,
    calendar: CalendarDays,
) : ViewModel() {

    private val currentMonth = calendar.days.map(YearMonth::from).distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SummaryUiState?> = combine(months.month, repository.observeMonthsWithData(), currentMonth) { month, withData, today ->
        val (previous, next) = adjacentMonths(month, withData, today)
        val previousWithData = withData.filter { it < month }.maxOrNull()
        Navigation(month, previous, next, previousWithData)
    }.distinctUntilChanged()
        .flatMapLatest { (month, previous, next, previousWithData) ->
            repository.observeMonth(month).flatMapLatest { rows ->
                val summary = buildSummary(rows, month).copy(previousMonth = previous, nextMonth = next)
                if (summary.isEmpty && previousWithData != null) {
                    repository.observeMonth(previousWithData).map { earlier ->
                        if (earlier.isEmpty()) summary else summary.copy(
                            previousNet = previousWithData to buildSummary(earlier, previousWithData).netMinor,
                        )
                    }
                } else flowOf(summary)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class Navigation(
        val month: YearMonth,
        val previous: YearMonth?,
        val next: YearMonth?,
        val previousWithData: YearMonth?,
    )

    fun selectMonth(month: YearMonth) = months.select(month)
    fun previousMonth() = uiState.value?.previousMonth?.let(months::select)
    fun nextMonth() = uiState.value?.nextMonth?.let(months::select)
}
