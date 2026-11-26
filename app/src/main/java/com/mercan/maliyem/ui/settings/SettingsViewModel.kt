package com.mercan.maliyem.ui.settings

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.R
import com.mercan.maliyem.data.backup.BackupCodec
import com.mercan.maliyem.data.backup.BackupDecodeResult
import com.mercan.maliyem.data.backup.BackupRepository
import com.mercan.maliyem.data.backup.CsvExporter
import com.mercan.maliyem.data.backup.CsvRow
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.AccountRepository
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    categories: CategoryRepository,
    accounts: AccountRepository,
    private val recurring: RecurringRepository,
    private val backupRepository: BackupRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val resolver = context.contentResolver

    val categoryCount: StateFlow<Int?> = categories.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recurringCount: StateFlow<Int?> = recurring.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val accountCount: StateFlow<Int?> = accounts.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val theme: StateFlow<ThemeMode> = settings.theme

    // Dil değişince Activity yeniden kurulur ama ViewModel yaşar; değer bu yüzden durumda tutulur.
    private val _language = MutableStateFlow(settings.language())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _dataBusy = MutableStateFlow(false)
    val dataBusy = _dataBusy.asStateFlow()
    private val _pendingBackup = MutableStateFlow<BackupDecodeResult.Valid?>(null)
    val pendingBackup = _pendingBackup.asStateFlow()
    private val _dataMessages = Channel<Int>(Channel.BUFFERED)
    val dataMessages = _dataMessages.receiveAsFlow()

    fun setTheme(mode: ThemeMode) = settings.setTheme(mode)

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        settings.setLanguage(language)
    }

    fun saveBackup(uri: Uri) = runDataAction(R.string.settings_backup_failed) {
        withContext(Dispatchers.IO) {
            writeDocument(uri, BackupCodec.encode(backupRepository.snapshot()))
        }
        _dataMessages.send(R.string.settings_backup_saved)
    }

    fun openBackup(uri: Uri) = runDataAction(R.string.settings_backup_invalid) {
        val result = withContext(Dispatchers.IO) {
            val input = resolver.openInputStream(uri) ?: throw IOException()
            input.use { readBackup(it) }?.let(BackupCodec::decode)
        }
        when (result) {
            is BackupDecodeResult.Valid -> _pendingBackup.value = result
            null -> _dataMessages.send(R.string.settings_backup_too_large)
            BackupDecodeResult.IdOutOfRange -> _dataMessages.send(R.string.settings_backup_id_out_of_range)
            BackupDecodeResult.Invalid -> _dataMessages.send(R.string.settings_backup_invalid)
        }
    }

    fun dismissBackup() {
        if (!_dataBusy.value) _pendingBackup.value = null
    }

    fun restoreBackup(beforeRestore: suspend () -> Unit) {
        val backup = _pendingBackup.value ?: return
        runDataAction(R.string.settings_restore_failed) {
            try {
                beforeRestore()
                withContext(Dispatchers.IO) { backupRepository.replaceAll(backup) }
                settings.setLastAccountId(null)
                recurring.generateDue()
                _dataMessages.send(R.string.settings_restore_saved)
            } finally {
                _pendingBackup.value = null
            }
        }
    }

    /** Ekranın kaynakları, CSV'deki tür ve kategori adlarının seçili dili izlemesini sağlar. */
    fun exportCsv(uri: Uri, resources: Resources) = runDataAction(R.string.settings_csv_failed) {
        withContext(Dispatchers.IO) {
            val snapshot = backupRepository.snapshot()
            val categories = snapshot.categories.associate { category ->
                category.id to (category.name ?: DefaultCategories.nameRes(category.key)?.let(resources::getString).orEmpty())
            }
            val accounts = snapshot.accounts.associate { account ->
                account.id to (account.name ?: DefaultAccounts.nameRes(account.key)?.let(resources::getString).orEmpty())
            }
            val expense = resources.getString(R.string.add_expense)
            val income = resources.getString(R.string.add_income)
            val rows = snapshot.transactions.map { transaction ->
                CsvRow(
                    LocalDate.parse(transaction.date),
                    if (transaction.type == TransactionType.EXPENSE) expense else income,
                    categories.getValue(transaction.categoryId),
                    transaction.amountMinor,
                    transaction.note,
                    accounts.getValue(transaction.accountId),
                )
            }
            val transfers = snapshot.transfers.map { transfer ->
                CsvRow(
                    LocalDate.parse(transfer.date), resources.getString(R.string.add_transfer), "", transfer.amountMinor,
                    transfer.note, resources.getString(
                        R.string.transfer_direction, accounts.getValue(transfer.fromAccountId), accounts.getValue(transfer.toAccountId),
                    ),
                )
            }
            writeDocument(uri, CsvExporter.encode(rows + transfers))
        }
        _dataMessages.send(R.string.settings_csv_saved)
    }

    private fun runDataAction(@StringRes error: Int, action: suspend () -> Unit) {
        if (_dataBusy.value) return
        _dataBusy.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _dataMessages.send(error)
            } finally {
                _dataBusy.value = false
            }
        }
    }

    private fun writeDocument(uri: Uri, content: String) {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException()
        output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }
}

private const val MAX_BACKUP_BYTES = 20_000_000

/** A document provider may omit or misreport the size, so count bytes as they arrive. */
internal fun readBackup(input: InputStream): String? {
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(chunk, 0, minOf(chunk.size, MAX_BACKUP_BYTES - output.size() + 1))
        if (count < 0) break
        if (count > MAX_BACKUP_BYTES - output.size()) return null
        output.write(chunk, 0, count)
    }
    return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString()
}
