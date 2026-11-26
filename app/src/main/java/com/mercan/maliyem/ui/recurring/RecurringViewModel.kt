package com.mercan.maliyem.ui.recurring

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.data.repository.AccountRepository
import com.mercan.maliyem.data.repository.CategoryRepository
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.ui.add.cleanNote
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val repository: RecurringRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
) : ViewModel() {
    val rules = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories = categories.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts = accounts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed = _writeFailed.asStateFlow()
    private val _finished = MutableStateFlow(false)
    val finished = _finished.asStateFlow()

    fun save(
        id: Long,
        amountMinor: Long,
        categoryId: Long,
        accountId: Long,
        note: String,
        frequency: RecurringFrequency,
        endDate: LocalDate?,
    ) = write(close = true) {
        repository.edit(id, amountMinor, categoryId, accountId, cleanNote(note), frequency, endDate)
    }

    fun setPaused(id: Long, paused: Boolean) = write { repository.setPaused(id, paused) }
    fun delete(id: Long) = write(close = true) { repository.delete(id) }

    private fun write(close: Boolean = false, action: suspend () -> Boolean) {
        if (_busy.value || _finished.value) return
        _busy.value = true
        _writeFailed.value = false
        viewModelScope.launch {
            try {
                val success = action()
                _writeFailed.value = !success
                _finished.value = success && close
            } catch (_: SQLiteException) {
                _writeFailed.value = true
            } finally {
                _busy.value = false
            }
        }
    }
}
