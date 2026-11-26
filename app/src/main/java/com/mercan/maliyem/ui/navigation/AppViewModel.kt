package com.mercan.maliyem.ui.navigation

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.ui.MonthSelection
import com.mercan.maliyem.ui.add.SavedEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val KEY_MONTH = "month"

sealed interface UndoAction {
    data class Added(val entry: SavedEntry) : UndoAction
    data class Deleted(val entity: TransactionEntity) : UndoAction
}

 /** Sekmelerin ortak işleri: Ekle sonrası ay geçişi ve snackbar'daki "Geri al" (ADR-0010). */
@HiltViewModel
class AppViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val months: MonthSelection,
    private val repository: TransactionRepository,
) : ViewModel() {

    init {
        savedState.get<String>(KEY_MONTH)?.let { months.select(YearMonth.parse(it)) }
        viewModelScope.launch { months.month.collect { savedState[KEY_MONTH] = it.toString() } }
    }

    private val monthsWithData = repository.observeMonthsWithData()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Hiç kayıt yoksa ilk açılış ekranı gösterilir; null = henüz bilinmiyor. */
    val hasData: StateFlow<Boolean?> = monthsWithData
        .map { it?.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Özet'te Ekle düğmesi yalnız seçili ay doluysa görünür; boş ayın kendi "Ekle" düğmesi var. */
    val monthHasData: StateFlow<Boolean> = combine(months.month, monthsWithData) { m, withData -> withData?.contains(m) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _undoActions = MutableStateFlow<List<UndoAction>>(emptyList())
    val undoActions = _undoActions.asStateFlow()
    private val undoWrites = Mutex()
    private val _undoFailed = MutableStateFlow(false)
    val undoFailed = _undoFailed.asStateFlow()

    fun offerUndo(action: UndoAction) {
        if (action is UndoAction.Added) months.select(YearMonth.from(action.entry.date))
        _undoActions.update { it + action }
    }

    fun finishUndo(action: UndoAction, perform: Boolean) {
        if (_undoActions.value.firstOrNull() != action) return
        _undoActions.update { it.drop(1) }
        if (!perform) return
        viewModelScope.launch {
            try {
                undoWrites.withLock {
                    when (action) {
                        is UndoAction.Added -> repository.delete(action.entry.id)
                        is UndoAction.Deleted -> if (!repository.restore(action.entity)) _undoFailed.value = true
                    }
                }
            } catch (_: SQLiteException) {
                _undoFailed.value = true
            }
        }
    }

    /** Yedekte aynı kimlikler başka kayıtlara ait olabilir; eski "Geri al" işlemlerini kapatır. */
    suspend fun clearUndo() {
        _undoActions.value = emptyList()
        undoWrites.withLock { _undoFailed.value = false }
    }

    fun acknowledgeUndoFailure() { _undoFailed.value = false }
}
