package com.mercan.maliyem.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.ui.MonthSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_MONTH = "month"

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

    /** Hiç kayıt yoksa ilk açılış ekranı gösterilir; null = henüz bilinmiyor. */
    val hasData: StateFlow<Boolean?> = repository.observeMonthsWithData()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Özet'te Ekle düğmesi yalnız seçili ay doluysa görünür; boş ayın kendi "Ekle" düğmesi var. */
    val monthHasData: StateFlow<Boolean> = combine(months.month, repository.observeMonthsWithData()) { m, withData -> m in withData }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun showMonth(date: LocalDate) = months.select(YearMonth.from(date))

    /** Yeni eklenen kaydı geri alır. */
    fun removeAdded(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Silinen kaydı aynı kimlikle geri yazar. */
    fun restore(entity: TransactionEntity) {
        viewModelScope.launch { repository.add(entity) }
    }
}
