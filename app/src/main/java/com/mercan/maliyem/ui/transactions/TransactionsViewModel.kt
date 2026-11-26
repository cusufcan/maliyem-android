package com.mercan.maliyem.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.ui.CalendarDays
import com.mercan.maliyem.ui.MonthSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val KEY_FILTER = "filter"

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    months: MonthSelection,
    repository: TransactionRepository,
    calendar: CalendarDays,
) : ViewModel() {

    val today = calendar.days.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val filter = savedState.getStateFlow(KEY_FILTER, TransactionFilter.ALL.name)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionsUiState?> = months.month
        .flatMapLatest { m -> repository.observeMonth(m).map { m to it } }
        .combine(filter) { (m, rows), f ->
            val selected = TransactionFilter.valueOf(f)
            TransactionsUiState(m, selected, groupByDay(rows, selected))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(value: TransactionFilter) { savedState[KEY_FILTER] = value.name }
}
