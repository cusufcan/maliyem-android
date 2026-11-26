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
private const val KEY_EDIT = "edit"
private const val KEY_CREATED = "created"

data class AddUiState(
    val form: AddForm,
    val categories: List<CategoryEntity>,
    val today: LocalDate,
    /** İlk Kaydet denemesinden önce hata gösterilmez. */
    val error: AddFormError?,
    /** Düzenleme kipinde başlık "Kaydı düzenle" olur. */
    val editing: Boolean = false,
)

/** Kaydedilen kaydın Özet'e dönüşte vurgulanması ve geri alınması için. */
data class SavedEntry(val id: Long, val type: TransactionType, val date: LocalDate, val edited: Boolean = false)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    // Yazılanlar SavedStateHandle'da; süreç ölüp dönünce form aynen açılır.
    private val type = savedState.getStateFlow(KEY_TYPE, TransactionType.EXPENSE.name)
    private val amount = savedState.getStateFlow(KEY_AMOUNT, "")
    private val category = savedState.getStateFlow<Long?>(KEY_CATEGORY, null)
    private val date = savedState.getStateFlow<String?>(KEY_DATE, null)
    private val note = savedState.getStateFlow(KEY_NOTE, "")
    private val tried = savedState.getStateFlow(KEY_TRIED, false)
    private val editId = savedState.getStateFlow<Long?>(KEY_EDIT, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val typeCategories = type.flatMapLatest { categories.observeByType(TransactionType.valueOf(it)) }

    private val form: Flow<AddForm> = combine(type, amount, category, date, note) { t, a, c, d, n ->
        AddForm(TransactionType.valueOf(t), a, c, d?.let(LocalDate::parse) ?: LocalDate.now(clock), n)
    }

    private var locale: Locale = Locale.getDefault()

    val uiState: StateFlow<AddUiState?> = combine(form, typeCategories, tried, editId) { f, list, t, edit ->
        val resolved = f.copy(categoryId = pickCategory(f.categoryId, list))
        AddUiState(resolved, list, LocalDate.now(clock), if (t) firstError(resolved) else null, edit != null)
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

    /** "+ Yeni" çipinden: kategoriyi seçili türe ekler ve seçili getirir (ADR-0017). */
    fun addCategory(name: String, icon: String) {
        val t = TransactionType.valueOf(type.value)
        viewModelScope.launch { setCategory(categories.add(t, name, icon)) }
    }

    fun save() {
        val form = uiState.value?.form ?: return
        when (val result = buildEntity(form, locale, clock.millis())) {
            is Result.Invalid -> savedState[KEY_TRIED] = true
            is Result.Valid -> viewModelScope.launch {
                val edit = editId.value
                val id = if (edit != null) {
                    // Düzenlemede kimlik ve ilk oluşturulma anı korunur; sıralama bozulmaz.
                    transactions.update(result.entity.copy(id = edit, createdAt = savedState[KEY_CREATED] ?: result.entity.createdAt))
                    edit
                } else {
                    transactions.add(result.entity)
                }
                reset()
                savedEvents.send(SavedEntry(id, result.entity.type, result.entity.date, edited = edit != null))
            }
        }
    }

    /** Kaydı forma yükler; aynı kayıt zaten açıksa (ekran dönünce) yazılanlar korunur. */
    fun startEdit(id: Long) {
        if (editId.value == id) return
        viewModelScope.launch {
            val entity = transactions.get(id) ?: return@launch
            savedState[KEY_TYPE] = entity.type.name
            savedState[KEY_AMOUNT] = amountInputText(entity.amountMinor, locale)
            savedState[KEY_CATEGORY] = entity.categoryId
            savedState[KEY_DATE] = entity.date.toString()
            savedState[KEY_NOTE] = entity.note.orEmpty()
            savedState[KEY_TRIED] = false
            savedState[KEY_CREATED] = entity.createdAt
            savedState[KEY_EDIT] = id
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
        savedState[KEY_EDIT] = null
    }
}
