package com.mercan.maliyem.ui.csvimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mercan.maliyem.data.csv.CsvDocument
import com.mercan.maliyem.data.csv.CsvField
import com.mercan.maliyem.data.csv.CsvFileError
import com.mercan.maliyem.data.csv.CsvImportLabels
import com.mercan.maliyem.data.csv.CsvImportException
import com.mercan.maliyem.data.csv.CsvImportParser
import com.mercan.maliyem.data.csv.CsvImportRepository
import com.mercan.maliyem.data.csv.CsvImportResult
import com.mercan.maliyem.data.csv.CsvMapping
import com.mercan.maliyem.data.csv.CsvPreview
import com.mercan.maliyem.data.csv.MappedCsv
import com.mercan.maliyem.data.csv.MaxCsvRows
import com.mercan.maliyem.ui.navigation.CsvImport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface CsvImportState {
    data object Loading : CsvImportState
    data class Failed(val reason: CsvFileError) : CsvImportState
    data class Mapping(val document: CsvDocument, val mapping: CsvMapping) : CsvImportState {
        val width = document.width(mapping.hasHeader)
        val tooManyRows = !mapping.hasHeader && document.records.size > MaxCsvRows
    }
    data class Preview(
        val data: CsvPreview,
        val skipDuplicates: Boolean,
        val updating: Boolean = false,
        val writing: Boolean = false,
        val failed: Boolean = false,
    ) : CsvImportState
    data class Finished(val result: CsvImportResult) : CsvImportState
}

@HiltViewModel
internal class CsvImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CsvImportRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val labels by lazy { CsvImportLabels.localized(context) }
    private val uri = Uri.parse(savedStateHandle.toRoute<CsvImport>().uri)
    private val current = MutableStateFlow<CsvImportState>(CsvImportState.Loading)
    val state = current.asStateFlow()
    private var draft: CsvImportState.Mapping? = null
    private var mapped: MappedCsv? = null
    private var skipDuplicates = true

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        try {
            val initial = withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri) ?: throw CsvImportException(CsvFileError.UNREADABLE)
                val document = input.use(CsvImportParser::read)
                CsvImportState.Mapping(document, document.suggestedMapping())
            }
            val document = initial.document
            draft = initial
            if (document.isMaliyem) showPreview(initial) else current.value = initial
        } catch (e: CancellationException) {
            throw e
        } catch (e: CsvImportException) {
            current.value = CsvImportState.Failed(e.reason)
        } catch (_: Exception) {
            current.value = CsvImportState.Failed(CsvFileError.UNREADABLE)
        }
    }

    fun setHasHeader(hasHeader: Boolean) {
        val state = current.value as? CsvImportState.Mapping ?: return
        val mapping = if (hasHeader) {
            val width = state.document.width(true)
            state.mapping.copy(columns = state.mapping.columns.filterValues { it < width }, hasHeader = true)
        } else {
            state.mapping.copy(hasHeader = false)
        }
        current.value = state.copy(mapping = mapping)
    }

    fun selectColumn(field: CsvField, index: Int?) {
        val state = current.value as? CsvImportState.Mapping ?: return
        val columns = state.mapping.columns - field
        val selected = if (index == null) columns else columns.filterValues { it != index } + (field to index)
        current.value = state.copy(mapping = state.mapping.copy(columns = selected))
    }

    fun editMapping() {
        val state = current.value as? CsvImportState.Preview ?: return
        if (!state.updating && !state.writing) draft?.let { current.value = it }
    }

    fun previewMapping() {
        val state = current.value as? CsvImportState.Mapping ?: return
        if (!state.tooManyRows && state.mapping.isValid(state.width)) showPreview(state)
    }

    private fun showPreview(state: CsvImportState.Mapping) {
        draft = state
        current.value = CsvImportState.Loading
        viewModelScope.launch {
            try {
                val csv = withContext(Dispatchers.Default) { CsvImportParser.map(state.document, state.mapping) }
                val preview = repository.preview(csv, skipDuplicates, labels)
                mapped = csv
                current.value = CsvImportState.Preview(preview, skipDuplicates)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CsvImportException) {
                current.value = CsvImportState.Failed(e.reason)
            } catch (_: Exception) {
                current.value = CsvImportState.Failed(CsvFileError.UNREADABLE)
            }
        }
    }

    fun setSkipDuplicates(skip: Boolean) {
        val state = current.value as? CsvImportState.Preview ?: return
        val csv = mapped ?: return
        if (state.updating || state.writing || skip == state.skipDuplicates) return
        current.value = state.copy(updating = true, failed = false)
        viewModelScope.launch {
            try {
                val preview = repository.preview(csv, skip, labels)
                skipDuplicates = skip
                current.value = CsvImportState.Preview(preview, skip)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                current.value = state.copy(failed = true)
            }
        }
    }

    fun confirm() {
        val state = current.value as? CsvImportState.Preview ?: return
        val csv = mapped ?: return
        if (state.updating || state.writing || state.data.ready == 0) return
        current.value = state.copy(writing = true, failed = false)
        viewModelScope.launch {
            try {
                val result = repository.import(csv, state.skipDuplicates, labels)
                draft = null
                mapped = null
                current.value = CsvImportState.Finished(result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                current.value = state.copy(failed = true)
            }
        }
    }
}
