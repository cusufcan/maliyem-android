package com.mercan.maliyem.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.CategoryRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_TYPE = "type"
private const val KEY_AMOUNT = "amount"
private const val KEY_CATEGORY = "category"
private const val KEY_DATE = "date"
private const val KEY_NOTE = "note"
private const val KEY_TRIED = "tried"

data class AddUiState(
    val form: AddForm,
    val categories: List<CategoryEntity>,
    val today: LocalDate,
    /** İlk Kaydet denemesinden önce hata gösterilmez. */
    val error: AddFormError?,
)

/** Kaydedilen kaydın Özet'e dönüşte vurgulanması ve geri alınması için. */
data class SavedEntry(val id: Long, val type: TransactionType, val date: LocalDate)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    // Yazılanlar SavedStateHandle'da; süreç ölüp dönünce form aynen açılır.
    private val type = savedState.getStateFlow(KEY_TYPE, TransactionType.EXPENSE.name)
    private val amount = savedState.getStateFlow(KEY_AMOUNT, "")
    private val category = savedState.getStateFlow<Long?>(KEY_CATEGORY, null)
    private val date = savedState.getStateFlow<String?>(KEY_DATE, null)
    private val note = savedState.getStateFlow(KEY_NOTE, "")
    private val tried = savedState.getStateFlow(KEY_TRIED, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val typeCategories = type.flatMapLatest { categories.observeByType(TransactionType.valueOf(it)) }

    private val form: Flow<AddForm> = combine(type, amount, category, date, note) { t, a, c, d, n ->
        AddForm(TransactionType.valueOf(t), a, c, d?.let(LocalDate::parse) ?: LocalDate.now(clock), n)
    }

    private var locale: Locale = Locale.getDefault()

    val uiState: StateFlow<AddUiState?> = combine(form, typeCategories, tried) { f, list, t ->
        val resolved = f.copy(categoryId = pickCategory(f.categoryId, list))
        AddUiState(resolved, list, LocalDate.now(clock), if (t) firstError(resolved) else null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val savedEvents = Channel<SavedEntry>(Channel.BUFFERED)
    val saved: Flow<SavedEntry> = savedEvents.receiveAsFlow()

    private fun firstError(form: AddForm) = (buildEntity(form, locale, 0) as? Result.Invalid)?.error

    fun setLocale(value: Locale) { locale = value }
    fun setType(value: TransactionType) { savedState[KEY_TYPE] = value.name }
    fun setAmount(value: String) { savedState[KEY_AMOUNT] = value }
    fun setCategory(id: Long) { savedState[KEY_CATEGORY] = id }
    fun setDate(value: LocalDate) { savedState[KEY_DATE] = value.toString() }
    fun setNote(value: String) { savedState[KEY_NOTE] = value }

    fun save() {
        val form = uiState.value?.form ?: return
        when (val result = buildEntity(form, locale, clock.millis())) {
            is Result.Invalid -> savedState[KEY_TRIED] = true
            is Result.Valid -> viewModelScope.launch {
                val id = transactions.add(result.entity)
                reset()
                savedEvents.send(SavedEntry(id, result.entity.type, result.entity.date))
            }
        }
    }

    /** Sayfa kapanınca taslak atılır; tür seçimi bir sonraki kayıt için kalır. */
    fun reset() {
        // remove() değil: getStateFlow akışları anahtar silinince kopar, varsayılana yazmak gerekir.
        savedState[KEY_AMOUNT] = ""
        savedState[KEY_CATEGORY] = null
        savedState[KEY_DATE] = null
        savedState[KEY_NOTE] = ""
        savedState[KEY_TRIED] = false
    }
}
