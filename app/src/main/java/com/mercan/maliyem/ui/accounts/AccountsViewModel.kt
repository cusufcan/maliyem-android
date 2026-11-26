package com.mercan.maliyem.ui.accounts

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.data.local.AccountEntity
import com.mercan.maliyem.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountsViewModel @Inject constructor(private val repository: AccountRepository) : ViewModel() {
    val balances = repository.observeBalances().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed = _writeFailed.asStateFlow()

    fun save(account: AccountEntity?, name: String?, icon: String, initialBalanceMinor: Long) = write {
        if (account != null) {
            repository.edit(account.id, name, icon, initialBalanceMinor)
        } else {
            repository.add(checkNotNull(name), icon, initialBalanceMinor)
            true
        }
    }

    fun setArchived(account: AccountEntity) = write { repository.setArchived(account.id, !account.archived) }
    fun delete(account: AccountEntity) = write { repository.delete(account.id) }

    private fun write(action: suspend () -> Boolean) {
        if (_busy.value) return
        _busy.value = true
        _writeFailed.value = false
        viewModelScope.launch {
            try {
                _writeFailed.value = !action()
            } catch (_: SQLiteException) {
                _writeFailed.value = true
            } finally {
                _busy.value = false
            }
        }
    }
}
