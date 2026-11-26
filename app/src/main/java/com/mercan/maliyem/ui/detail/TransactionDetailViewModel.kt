package com.mercan.maliyem.ui.detail

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.ui.navigation.TransactionDetail
import com.mercan.maliyem.util.roundedPercent
import java.math.BigInteger
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [categoryMonthMinor]: aynı kategorinin kaydın ayındaki toplamı (pano: "Market bu ay X ₺"). */
sealed interface DetailUiState {
    data class Record(val item: TransactionWithCategory, val categoryMonthMinor: BigInteger) : DetailUiState {
        val sharePercent: Int = if (categoryMonthMinor.signum() == 0) 100
            else roundedPercent(item.transaction.amountMinor.toBigInteger(), categoryMonthMinor).toInt()
    }

    data object Missing : DetailUiState
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: TransactionRepository,
) : ViewModel() {

    private val id = savedState.toRoute<TransactionDetail>().id

    /** null: yükleniyor. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DetailUiState?> = repository.observeDate(id)
        .map { it?.let(YearMonth::from) }
        .distinctUntilChanged()
        .flatMapLatest { month ->
            if (month == null) flowOf(DetailUiState.Missing)
            else repository.observeMonth(month).mapNotNull { rows ->
                val item = rows.firstOrNull { it.transaction.id == id } ?: return@mapNotNull null
                val total = rows.asSequence()
                    .filter { it.transaction.categoryId == item.transaction.categoryId }
                    .sumOf { it.transaction.amountMinor.toBigInteger() }
                DetailUiState.Record(item, total)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deleting = MutableStateFlow(false)
    val deleting = _deleting.asStateFlow()
    private val _deleted = MutableStateFlow<TransactionEntity?>(null)
    val deleted = _deleted.asStateFlow()
    private val _deleteFailed = MutableStateFlow(false)
    val deleteFailed = _deleteFailed.asStateFlow()

    fun delete() {
        if (_deleting.value || _deleted.value != null) return
        _deleting.value = true
        _deleteFailed.value = false
        viewModelScope.launch {
            try {
                _deleted.value = repository.deleteAndGet(id)
            } catch (_: SQLiteException) {
                _deleteFailed.value = true
            } finally {
                _deleting.value = false
            }
        }
    }

    fun acknowledgeDeleted() { _deleted.value = null }
}
