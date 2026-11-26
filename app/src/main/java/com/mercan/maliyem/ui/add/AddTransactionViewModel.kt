package com.mercan.maliyem.ui.add

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.receipt.ReceiptDrafts
import com.mercan.maliyem.data.repository.AccountRepository
import com.mercan.maliyem.data.repository.CategoryRepository
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.data.repository.TransferRepository
import com.mercan.maliyem.data.repository.forType
import com.mercan.maliyem.ui.CalendarDays
import com.mercan.maliyem.ui.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_TYPE = "type"
private const val KEY_TYPE_CHANGED = "typeChanged"
private const val KEY_AMOUNT = "amount"
private const val KEY_AMOUNT_LOCALE = "amountLocale"
private const val KEY_CATEGORY = "category"
private const val KEY_DATE = "date"
private const val KEY_NOTE = "note"
private const val KEY_TRIED = "tried"
private const val KEY_EDIT = "edit"
private const val KEY_TRANSFER = "transfer"
private const val KEY_ACCOUNT = "account"
private const val KEY_TO = "toAccount"
private const val KEY_PREVIOUS_ACCOUNT = "previousAccount"
private const val KEY_PREVIOUS_TO = "previousTo"
private const val KEY_CREATED = "created"
private const val KEY_FREQUENCY = "frequency"
private const val KEY_RECEIPT = "receipt"
private const val KEY_CAMERA_CAPTURE = "receiptCapture"

data class AddUiState(
    val form: AddForm,
    val categories: List<CategoryEntity>,
    val today: LocalDate,
    /** İlk Kaydet denemesinden önce hata gösterilmez. */
    val error: AddFormError?,
    /** Düzenleme kipinde başlık "Kaydı düzenle" olur. */
    val editId: Long?,
    val amountMinor: Long?,
    val accounts: List<AccountEntity>,
    val transfer: Boolean,
    val toAccountId: Long?,
    val frequency: RecurringFrequency?,
) {
    val activeAccounts = accounts.filterNot { it.archived }
    val canTransfer = form.accountId != null && toAccountId != null
}

private data class AccountSelection(
    val transfer: Boolean,
    val accountId: Long?,
    val toAccountId: Long?,
)

/** Kaydedilen kaydın Özet'e dönüşte vurgulanması ve geri alınması için. */
data class SavedEntry(
    val id: Long,
    val type: TransactionType?,
    val date: LocalDate,
    val edited: Boolean = false,
    val recurring: Boolean = false,
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    accounts: AccountRepository,
    private val transfers: TransferRepository,
    private val recurring: RecurringRepository,
    private val settings: AppSettings,
    private val clock: Clock,
    private val receiptDrafts: ReceiptDrafts,
    calendar: CalendarDays,
) : ViewModel() {

    // Yazılanlar SavedStateHandle'da; süreç ölüp dönünce form aynen açılır.
    private val type = savedState.getStateFlow(KEY_TYPE, TransactionType.EXPENSE.name)
    private val amount = savedState.getStateFlow(KEY_AMOUNT, "")
    private val category = savedState.getStateFlow<Long?>(KEY_CATEGORY, null)
    private val date = savedState.getStateFlow<String?>(KEY_DATE, null)
    private val note = savedState.getStateFlow(KEY_NOTE, "")
    private val tried = savedState.getStateFlow(KEY_TRIED, false)
    private val frequency = savedState.getStateFlow<String?>(KEY_FREQUENCY, null)
    private val editId = savedState.getStateFlow<Long?>(KEY_EDIT, null)

    private val transfer = savedState.getStateFlow(KEY_TRANSFER, false)
    private val account = savedState.getStateFlow<Long?>(KEY_ACCOUNT, null)
    private val toAccount = savedState.getStateFlow<Long?>(KEY_TO, null)
    private val accountRows = accounts.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val accountSelection = combine(transfer, account, toAccount, ::AccountSelection)

    private val amountLocale = savedState.getStateFlow(KEY_AMOUNT_LOCALE, Locale.getDefault().toLanguageTag())
    private val categoriesByType = categories.observeAll()
        .map { all -> TransactionType.entries.associateWith(all::forType) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val formChanges = combine(type, amount, category, date, note) { _, _, _, _, _ -> Unit }
    private val validationChanges = combine(tried, editId, amountLocale, frequency) { _, _, _, _ -> Unit }

    val uiState: StateFlow<AddUiState?> =
        combine(formChanges, validationChanges, categoriesByType, calendar.days, accountRows) { _, _, all, today, rows ->
            if (all == null || rows == null) null else Triple(all, today, rows)
        }.combine(accountSelection) { input, selection ->
            input?.let { (all, today, rows) -> resolveState(all, today, rows, selection) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saved = MutableStateFlow<SavedEntry?>(null)
    val saved = _saved.asStateFlow()
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed = _writeFailed.asStateFlow()

    val receiptPath = savedState.getStateFlow<String?>(KEY_RECEIPT, null)
    private val _receiptImporting = MutableStateFlow(false)
    val receiptImporting = _receiptImporting.asStateFlow()
    private val _receiptFailed = MutableStateFlow(false)
    val receiptFailed = _receiptFailed.asStateFlow()

    fun startReceiptCapture(): Uri {
        receiptDrafts.discardCapture(savedState[KEY_CAMERA_CAPTURE])
        val name = receiptDrafts.newName()
        savedState[KEY_CAMERA_CAPTURE] = name
        return receiptDrafts.cameraUri(name)
    }

    fun receiptCaptureFailed() {
        receiptDrafts.discardCapture(savedState[KEY_CAMERA_CAPTURE])
        savedState[KEY_CAMERA_CAPTURE] = null
        _receiptFailed.value = true
    }

    fun finishReceiptCapture(taken: Boolean) {
        val name = savedState.get<String>(KEY_CAMERA_CAPTURE) ?: return
        savedState[KEY_CAMERA_CAPTURE] = null
        if (taken) importReceipt(receiptDrafts.cameraUri(name), capture = name)
        else receiptDrafts.discardCapture(name)
    }

    fun importReceipt(uri: Uri, capture: String? = null) {
        if (_receiptImporting.value || _saving.value) {
            receiptDrafts.discardCapture(capture)
            return
        }
        _receiptImporting.value = true
        _receiptFailed.value = false
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val name = receiptDrafts.newName()
            try {
                receiptDrafts.importPhoto(uri, name)
                setReceipt(name)
            } catch (_: IOException) {
                _receiptFailed.value = true
            } catch (_: SecurityException) {
                _receiptFailed.value = true
            } catch (_: IllegalArgumentException) {
                _receiptFailed.value = true
            } finally {
                if (receiptPath.value != name) receiptDrafts.discardDraft(name)
                receiptDrafts.discardCapture(capture)
                _receiptImporting.value = false
            }
        }
    }

    fun removeReceipt() {
        if (_saving.value || _receiptImporting.value) return
        setReceipt(null)
        _receiptFailed.value = false
    }

    private fun setReceipt(path: String?) {
        if (receiptPath.value != path) receiptDrafts.discardDraft(receiptPath.value)
        savedState[KEY_RECEIPT] = path
    }

    private fun resolveState(
        all: Map<TransactionType, List<CategoryEntity>>,
        today: LocalDate,
        rows: List<AccountEntity>,
        selection: AccountSelection,
    ): AddUiState {
        val selectedType = TransactionType.valueOf(type.value)
        val list = all.getValue(selectedType)
        val previousAccount = savedState.get<Long>(KEY_PREVIOUS_ACCOUNT)
        val from = pickAccount(selection.accountId ?: settings.lastAccountId(), previousAccount, rows)
        val to = if (selection.transfer) {
            pickAccount(selection.toAccountId, savedState.get<Long>(KEY_PREVIOUS_TO), rows.filter { it.id != from })
        } else null
        val repeat = frequency.value?.let(RecurringFrequency::valueOf)
            ?.takeIf { editId.value == null && !selection.transfer }
        val selectedDate = date.value?.let(LocalDate::parse) ?: today
        val form = AddForm(
            selectedType,
            amount.value,
            pickCategory(category.value, list),
            if (repeat == null && editId.value == null) minOf(selectedDate, today) else selectedDate,
            note.value,
            from,
        )
        val locale = Locale.forLanguageTag(amountLocale.value)
        val error = if (!tried.value) null else if (selection.transfer) amountError(form.amountText, locale)
            else (buildEntity(form, locale, 0) as? Result.Invalid)?.error
        return AddUiState(form, list, today, error, editId.value, parseAmount(form.amountText, locale), rows, selection.transfer, to, repeat)
    }

    fun setType(value: TransactionType, fromQuickEntry: Boolean = false) {
        if (!fromQuickEntry) savedState[KEY_TYPE_CHANGED] = true
        savedState[KEY_TYPE] = value.name
        savedState[KEY_TRANSFER] = false
    }

    fun selectTransfer() { savedState[KEY_TRANSFER] = true }
    fun setAccount(id: Long) { savedState[KEY_ACCOUNT] = id }
    fun setToAccount(id: Long) { savedState[KEY_TO] = id }

    fun setAmount(value: String, locale: Locale) {
        savedState[KEY_AMOUNT_LOCALE] = locale.toLanguageTag()
        savedState[KEY_AMOUNT] = value
    }
    fun setCategory(id: Long) { savedState[KEY_CATEGORY] = id }
    fun setDate(value: LocalDate) { savedState[KEY_DATE] = value.toString() }
    fun setNote(value: String) { savedState[KEY_NOTE] = value }
    fun setFrequency(value: RecurringFrequency?) { savedState[KEY_FREQUENCY] = value?.name }

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
        if (_saving.value || _receiptImporting.value) return
        val all = categoriesByType.value ?: return
        val rows = accountRows.value ?: return
        val state = resolveState(all, LocalDate.now(clock), rows, AccountSelection(transfer.value, account.value, toAccount.value))
        val form = state.form
        val locale = Locale.forLanguageTag(amountLocale.value)
        val edit = editId.value
        val repeat = state.frequency
        if (repeat != null && form.date > state.today && receiptPath.value != null) return
        val createdAt = savedState.get<Long>(KEY_CREATED) ?: clock.millis()
        val entry = if (state.transfer) {
            buildTransfer(form, state.toAccountId, state.amountMinor, createdAt) ?: run {
                savedState[KEY_TRIED] = true
                return
            }
        } else null
        val transaction = if (state.transfer) null else {
            val result = buildEntity(form, locale, createdAt)
            if (result is Result.Invalid) {
                savedState[KEY_TRIED] = true
                return
            }
            (result as Result.Valid).entity.copy(receiptPath = receiptPath.value)
        }
        _saving.value = true
        _writeFailed.value = false
        viewModelScope.launch {
            try {
                val id = if (entry != null) {
                    if (edit == null) transfers.add(entry) else edit.takeIf { transfers.update(entry.copy(id = edit)) }
                } else {
                    val entity = checkNotNull(transaction)
                    when {
                        edit != null -> edit.takeIf { transactions.update(entity.copy(id = edit)) }
                        repeat != null -> recurring.add(entity, repeat)
                        else -> transactions.add(entity)
                    }
                }
                if (id == null) {
                    _writeFailed.value = true
                    return@launch
                }
                settings.setLastAccountId(form.accountId)
                _saved.value = SavedEntry(id, transaction?.type, form.date, edited = edit != null, recurring = repeat != null)
            } catch (_: SQLiteException) {
                _writeFailed.value = true
            } catch (_: IOException) {
                _receiptFailed.value = true
            } finally {
                if (_saved.value == null) _saving.value = false
            }
        }
    }

    /** Kaydı yükler. Ekran yeniden kurulursa mevcut taslağı korur. */
    suspend fun prepare(id: Long?, editTransfer: Boolean, locale: Locale): Boolean {
        if (id != null && (editId.value != id || transfer.value != editTransfer)) {
            try {
                if (editTransfer) {
                    val entity = transfers.get(id) ?: return false
                    loadDraft(entity.amountMinor, entity.date, entity.note, entity.createdAt, entity.fromAccountId, locale)
                    savedState[KEY_TO] = entity.toAccountId
                    savedState[KEY_PREVIOUS_TO] = entity.toAccountId
                } else {
                    val entity = transactions.get(id) ?: return false
                    loadDraft(entity.amountMinor, entity.date, entity.note, entity.createdAt, entity.accountId, locale)
                    savedState[KEY_TYPE] = entity.type.name
                    savedState[KEY_CATEGORY] = entity.categoryId
                    setReceipt(entity.receiptPath)
                }
            } catch (_: SQLiteException) {
                return false
            }
            savedState[KEY_TRANSFER] = editTransfer
            savedState[KEY_EDIT] = id
        } else {
            val sourceLocale = savedState.get<String>(KEY_AMOUNT_LOCALE)?.let(Locale::forLanguageTag)
            if (sourceLocale == null) {
                savedState[KEY_AMOUNT_LOCALE] = locale.toLanguageTag()
            } else if (sourceLocale != locale) {
                parseAmount(amount.value, sourceLocale)?.let { setAmount(amountInputText(it, locale), locale) }
            }
        }
        val path = receiptPath.value
        if (path != null && !receiptDrafts.hasPhoto(path) && path == receiptPath.value) setReceipt(null)
        return true
    }

    private fun loadDraft(minor: Long, date: LocalDate, note: String?, createdAt: Long, accountId: Long, locale: Locale) {
        setAmount(amountInputText(minor, locale), locale)
        setDate(date)
        setNote(note.orEmpty())
        savedState[KEY_TRIED] = false
        savedState[KEY_CREATED] = createdAt
        savedState[KEY_ACCOUNT] = accountId
        savedState[KEY_PREVIOUS_ACCOUNT] = accountId
    }

    val hasDraft: Boolean
        get() = _saving.value || _saved.value != null || editId.value != null || amount.value.isNotEmpty() ||
            note.value.isNotEmpty() || savedState.get<Boolean>(KEY_TYPE_CHANGED) == true || category.value != null || date.value != null || account.value != null ||
            toAccount.value != null || frequency.value != null || transfer.value || tried.value ||
            receiptPath.value != null || _receiptImporting.value

    override fun onCleared() {
        receiptDrafts.discardDraft(receiptPath.value)
        receiptDrafts.discardCapture(savedState[KEY_CAMERA_CAPTURE])
    }

    /** Sayfa kapanınca taslak atılır; tür seçimi bir sonraki kayıt için kalır. */
    fun reset() {
        // remove() kullanılırsa getStateFlow akışları anahtardan kopar.
        savedState[KEY_TYPE_CHANGED] = false
        savedState[KEY_AMOUNT] = ""
        savedState[KEY_CATEGORY] = null
        savedState[KEY_DATE] = null
        savedState[KEY_NOTE] = ""
        savedState[KEY_TRIED] = false
        savedState[KEY_EDIT] = null
        savedState[KEY_CREATED] = null
        savedState[KEY_FREQUENCY] = null
        savedState[KEY_ACCOUNT] = null
        savedState[KEY_TO] = null
        savedState[KEY_PREVIOUS_ACCOUNT] = null
        savedState[KEY_PREVIOUS_TO] = null
        savedState[KEY_TRANSFER] = false
        setReceipt(null)
        receiptDrafts.discardCapture(savedState[KEY_CAMERA_CAPTURE])
        savedState[KEY_CAMERA_CAPTURE] = null
        _receiptFailed.value = false
        _saved.value = null
        _saving.value = false
        _writeFailed.value = false
    }
}
