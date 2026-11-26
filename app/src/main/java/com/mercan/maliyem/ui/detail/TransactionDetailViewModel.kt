package com.mercan.maliyem.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.ui.navigation.TransactionDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [categoryMonthMinor]: aynı kategorinin kaydın ayındaki toplamı (pano: "Market bu ay X ₺"). */
data class DetailUiState(val item: TransactionWithCategory, val categoryMonthMinor: Long) {
    val sharePercent: Int get() = if (categoryMonthMinor == 0L) 100 else Math.round(item.transaction.amountMinor * 100.0 / categoryMonthMinor).toInt()
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: TransactionRepository,
) : ViewModel() {

    val id = savedState.toRoute<TransactionDetail>().id

    /** null: yükleniyor ya da kayıt silindi. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DetailUiState?> = repository.observe(id)
        .flatMapLatest { item ->
            if (item == null) flowOf(null)
            else repository.observeMonth(YearMonth.from(item.transaction.date)).map { rows ->
                DetailUiState(item, rows.filter { it.transaction.categoryId == item.transaction.categoryId }.sumOf { it.transaction.amountMinor })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Silinen kaydı döndürür; "Geri al" onu aynı kimlikle geri yazar. */
    suspend fun delete(): TransactionEntity? {
        val entity = repository.get(id) ?: return null
        repository.delete(id)
        return entity
    }
}
