package com.mercan.maliyem.ui.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.BudgetRepository
import com.mercan.maliyem.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_EDITING = "editing"
internal const val OVERALL_BUDGET = 0L

internal data class BudgetSettings(
    val categories: List<CategoryEntity>,
    val limits: Map<Long, Long>,
    val overallLimit: Long?,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
) : ViewModel() {
    internal val settings = combine(
        categories.observeByType(TransactionType.EXPENSE), budgets.observeAll(), budgets.observeOverall(),
    ) { choices, limits, overall ->
        BudgetSettings(choices, limits.associate { it.categoryId to it.limitMinor }, overall?.limitMinor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    internal val editing = savedState.getStateFlow<Long?>(KEY_EDITING, null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed = _writeFailed.asStateFlow()

    fun edit(id: Long?) {
        if (_busy.value) return
        _writeFailed.value = false
        savedState[KEY_EDITING] = id
    }

    fun save(limitMinor: Long?) {
        val id = editing.value ?: return
        if (_busy.value) return
        _busy.value = true
        _writeFailed.value = false
        viewModelScope.launch {
            try {
                budgets.setLimit(id.takeUnless { it == OVERALL_BUDGET }, limitMinor)
                savedState[KEY_EDITING] = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _writeFailed.value = true
            } finally {
                _busy.value = false
            }
        }
    }
}
