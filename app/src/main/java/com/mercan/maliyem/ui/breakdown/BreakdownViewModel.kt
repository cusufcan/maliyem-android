package com.mercan.maliyem.ui.breakdown

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.BudgetRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.ui.MonthSelection
import com.mercan.maliyem.ui.trend.previousTrendMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

private const val KEY_TYPE = "type"

@HiltViewModel
class BreakdownViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    months: MonthSelection,
    repository: TransactionRepository,
    budgets: BudgetRepository,
) : ViewModel() {

    private val type = savedState.getStateFlow(KEY_TYPE, TransactionType.EXPENSE.name)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BreakdownUiState?> = months.month.flatMapLatest { month ->
        val previous = previousTrendMonth(month)
        val comparison = previous?.let { repository.observeTrendEntries(it, it) } ?: flowOf(emptyList())
        combine(repository.observeMonth(month), comparison, type, budgets.observeAll()) { rows, earlier, t, limits ->
            val selected = TransactionType.valueOf(t)
            val budgetLimits = if (selected == TransactionType.EXPENSE) limits.associate { it.categoryId to it.limitMinor } else emptyMap()
            BreakdownUiState(month, selected, buildSlices(rows, selected), budgetLimits, previousCategoryTotals(earlier, selected))
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setType(value: TransactionType) { savedState[KEY_TYPE] = value.name }
}
