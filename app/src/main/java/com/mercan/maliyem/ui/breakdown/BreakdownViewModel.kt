package com.mercan.maliyem.ui.breakdown

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.TransactionRepository
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

private const val KEY_TYPE = "type"

@HiltViewModel
class BreakdownViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    months: MonthSelection,
    repository: TransactionRepository,
) : ViewModel() {

    private val type = savedState.getStateFlow(KEY_TYPE, TransactionType.EXPENSE.name)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BreakdownUiState?> = months.month
        .flatMapLatest { m -> repository.observeMonth(m).map { m to it } }
        .combine(type) { (m, rows), t ->
            val selected = TransactionType.valueOf(t)
            BreakdownUiState(m, selected, buildSlices(rows, selected))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setType(value: TransactionType) { savedState[KEY_TYPE] = value.name }
}
