package com.mercan.maliyem.ui.detail

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TransferEntity
import com.mercan.maliyem.data.repository.AccountRepository
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.data.repository.TransferRepository
import com.mercan.maliyem.ui.navigation.TransactionDetail
import com.mercan.maliyem.ui.navigation.UndoAction
import com.mercan.maliyem.util.roundedPercent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    data class Transfer(val item: TransferEntity) : DetailUiState

    data object Missing : DetailUiState
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: TransactionRepository,
    private val transfers: TransferRepository,
    accounts: AccountRepository,
    recurring: RecurringRepository,
) : ViewModel() {

    val route = savedState.toRoute<TransactionDetail>()
    private val id = route.id

    val accounts = accounts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null: yükleniyor. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val record = repository.observeDate(id)
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

    val uiState: StateFlow<DetailUiState?> = (
        if (route.transfer) transfers.observe(id).map { it?.let(DetailUiState::Transfer) ?: DetailUiState.Missing }
        else record
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rule = uiState.map { (it as? DetailUiState.Record)?.item?.transaction?.recurringRuleId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else recurring.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deleting = MutableStateFlow(false)
    val deleting = _deleting.asStateFlow()
    private val _deleted = MutableStateFlow<UndoAction?>(null)
    val deleted = _deleted.asStateFlow()
    private val _deleteFailed = MutableStateFlow(false)
    val deleteFailed = _deleteFailed.asStateFlow()

    fun delete() {
        if (_deleting.value || _deleted.value != null) return
        _deleting.value = true
        _deleteFailed.value = false
        viewModelScope.launch {
            try {
                val deleted = if (route.transfer) transfers.deleteAndGet(id)?.let(UndoAction::DeletedTransfer)
                    else repository.deleteAndGet(id)?.let(UndoAction::Deleted)
                _deleted.value = deleted
                if (deleted == null) _deleteFailed.value = true
            } catch (_: SQLiteException) {
                _deleteFailed.value = true
            } finally {
                _deleting.value = false
            }
        }
    }

    fun acknowledgeDeleted() { _deleted.value = null }
}
