package com.mercan.maliyem.ui.yearly

import android.content.res.Resources
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.R
import com.mercan.maliyem.data.backup.TransactionCsvExport
import com.mercan.maliyem.data.repository.YearlyReport
import com.mercan.maliyem.data.repository.YearlyReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ReportState {
    data object Loading : ReportState
    data object Failed : ReportState
    data class Ready(val report: YearlyReport) : ReportState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YearlyReportViewModel @Inject constructor(
    repository: YearlyReportRepository,
    private val csvExport: TransactionCsvExport,
    private val savedState: SavedStateHandle,
    clock: Clock,
) : ViewModel() {
    private val currentYear = Year.now(clock).value
    private val selectedYear = savedState.getStateFlow("reportYear", currentYear)
    private val retries = MutableStateFlow(0)

    val state = combine(selectedYear, retries) { year, _ -> year }.flatMapLatest { year ->
        repository.observe(currentYear, year)
            .map<YearlyReport, ReportState> { ReportState.Ready(it) }
            .onStart { emit(ReportState.Loading) }
            .catch { emit(ReportState.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), ReportState.Loading)

    private val _exporting = MutableStateFlow(false)
    val exporting = _exporting.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    val exportMessages = messages.receiveAsFlow()

    fun selectYear(year: Int) {
        savedState["reportYear"] = year
    }

    fun retry() = retries.update { it + 1 }

    fun export(uri: Uri, resources: Resources, year: Int) {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                csvExport.save(uri, resources, year)
                messages.send(R.string.settings_csv_saved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messages.send(R.string.settings_csv_failed)
            } finally {
                _exporting.value = false
            }
        }
    }
}
