package com.mercan.maliyem.ui.add

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.CategoryRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.data.repository.forType
import com.mercan.maliyem.ui.CalendarDays
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_TYPE = "type"
private const val KEY_AMOUNT = "amount"
private const val KEY_AMOUNT_LOCALE = "amountLocale"
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
    val editId: Long?,
    val amountMinor: Long?,
)

/** Kaydedilen kaydın Özet'e dönüşte vurgulanması ve geri alınması için. */
data class SavedEntry(val id: Long, val type: TransactionType, val date: LocalDate, val edited: Boolean = false)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
    calendar: CalendarDays,
) : ViewModel() {

    // Yazılanlar SavedStateHandle'da; süreç ölüp dönünce form aynen açılır.
    private val type = savedState.getStateFlow(KEY_TYPE, TransactionType.EXPENSE.name)
    private val amount = savedState.getStateFlow(KEY_AMOUNT, "")
    private val category = savedState.getStateFlow<Long?>(KEY_CATEGORY, null)
    private val date = savedState.getStateFlow<String?>(KEY_DATE, null)
    private val note = savedState.getStateFlow(KEY_NOTE, "")
    private val tried = savedState.getStateFlow(KEY_TRIED, false)
    private val editId = savedState.getStateFlow<Long?>(KEY_EDIT, null)

    private val amountLocale = savedState.getStateFlow(KEY_AMOUNT_LOCALE, Locale.getDefault().toLanguageTag())
    private val categoriesByType = categories.observeAll()
        .map { all -> TransactionType.entries.associateWith(all::forType) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val formChanges = combine(type, amount, category, date, note) { _, _, _, _, _ -> Unit }
    private val validationChanges = combine(tried, editId, amountLocale) { _, _, _ -> Unit }

    val uiState: StateFlow<AddUiState?> =
        combine(formChanges, validationChanges, categoriesByType, calendar.days) { _, _, all, today ->
            all?.let {
                val (form, list) = resolveForm(it, today)
                val locale = Locale.forLanguageTag(amountLocale.value)
                val error = if (tried.value) (buildEntity(form, locale, 0) as? Result.Invalid)?.error else null
                AddUiState(form, list, today, error, editId.value, parseAmount(form.amountText, locale))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saved = MutableStateFlow<SavedEntry?>(null)
    val saved = _saved.asStateFlow()
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed = _writeFailed.asStateFlow()

    private fun resolveForm(
        all: Map<TransactionType, List<CategoryEntity>>,
        today: LocalDate,
    ): Pair<AddForm, List<CategoryEntity>> {
        val selectedType = TransactionType.valueOf(type.value)
        val list = all.getValue(selectedType)
        val form = AddForm(
            selectedType,
            amount.value,
            pickCategory(category.value, list),
            date.value?.let(LocalDate::parse) ?: today,
            note.value,
        )
        return form to list
    }

    fun setType(value: TransactionType) { savedState[KEY_TYPE] = value.name }
    fun setAmount(value: String, locale: Locale) {
        savedState[KEY_AMOUNT_LOCALE] = locale.toLanguageTag()
        savedState[KEY_AMOUNT] = value
    }
    fun setCategory(id: Long) { savedState[KEY_CATEGORY] = id }
    fun setDate(value: LocalDate) { savedState[KEY_DATE] = value.toString() }
    fun setNote(value: String) { savedState[KEY_NOTE] = value }

    /** "+ Yeni" çipinden: kategoriyi seçili türe ekler ve seçili getirir (ADR-0017). */
    fun addCategory(name: String, icon: String) {
        if (_saving.value) return
        val selectedType = TransactionType.valueOf(type.value)
        _saving.value = true
        _writeFailed.value = false
        viewModelScope.launch {
            try {
                val id = categories.add(selectedType, name, icon)
                categoriesByType.first { grouped -> grouped?.get(selectedType)?.any { it.id == id } == true }
                if (type.value == selectedType.name) setCategory(id)
            } catch (_: SQLiteException) {
                _writeFailed.value = true
            } finally {
                _saving.value = false
            }
        }
    }

    fun save() {
        if (_saving.value) return
        val all = categoriesByType.value ?: return
        val (form, _) = resolveForm(all, LocalDate.now(clock))
        val locale = Locale.forLanguageTag(amountLocale.value)
        val result = buildEntity(form, locale, clock.millis())
        if (result is Result.Invalid) {
            savedState[KEY_TRIED] = true
            return
        }
        val entity = (result as Result.Valid).entity
        val edit = editId.value
        val createdAt = savedState.get<Long>(KEY_CREATED) ?: entity.createdAt
        _saving.value = true
        _writeFailed.value = false
        viewModelScope.launch {
            try {
                val id = if (edit == null) {
                    transactions.add(entity)
                } else {
                    if (!transactions.update(entity.copy(id = edit, createdAt = createdAt))) {
                        _writeFailed.value = true
                        return@launch
                    }
                    edit
                }
                _saved.value = SavedEntry(id, entity.type, entity.date, edited = edit != null)
            } catch (_: SQLiteException) {
                _writeFailed.value = true
            } finally {
                if (_saved.value == null) _saving.value = false
            }
        }
    }

    /** Kaydı yükler. Ekran yeniden kurulursa mevcut taslağı korur. */
    suspend fun prepare(id: Long?, locale: Locale): Boolean {
        if (id != null && editId.value != id) {
            val entity = try {
                transactions.get(id)
            } catch (_: SQLiteException) {
                null
            } ?: return false
            savedState[KEY_TYPE] = entity.type.name
            setAmount(amountInputText(entity.amountMinor, locale), locale)
            savedState[KEY_CATEGORY] = entity.categoryId
            savedState[KEY_DATE] = entity.date.toString()
            savedState[KEY_NOTE] = entity.note.orEmpty()
            savedState[KEY_TRIED] = false
            savedState[KEY_CREATED] = entity.createdAt
            savedState[KEY_EDIT] = id
        } else {
            val sourceLocale = savedState.get<String>(KEY_AMOUNT_LOCALE)?.let(Locale::forLanguageTag)
            if (sourceLocale == null) {
                savedState[KEY_AMOUNT_LOCALE] = locale.toLanguageTag()
            } else if (sourceLocale != locale) {
                parseAmount(amount.value, sourceLocale)?.let { setAmount(amountInputText(it, locale), locale) }
            }
        }
        return true
    }

    /** Sayfa kapanınca taslak atılır; tür seçimi bir sonraki kayıt için kalır. */
    fun reset() {
        // remove() kullanılırsa getStateFlow akışları anahtardan kopar.
        savedState[KEY_AMOUNT] = ""
        savedState[KEY_CATEGORY] = null
        savedState[KEY_DATE] = null
        savedState[KEY_NOTE] = ""
        savedState[KEY_TRIED] = false
        savedState[KEY_EDIT] = null
        _saved.value = null
        _saving.value = false
        _writeFailed.value = false
    }
}
