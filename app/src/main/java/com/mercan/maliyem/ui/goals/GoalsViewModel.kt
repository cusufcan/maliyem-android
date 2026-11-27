package com.mercan.maliyem.ui.goals

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.GoalDetail
import com.mercan.maliyem.data.repository.GoalWriteResult
import com.mercan.maliyem.data.repository.SavingsGoalRepository
import com.mercan.maliyem.ui.CalendarDays
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class LoadedGoal(val detail: GoalDetail?)

internal data class GoalWriteState(
    val busy: Boolean = false,
    val saved: Boolean = false,
    val error: Int? = null,
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: SavingsGoalRepository,
    calendar: CalendarDays,
    clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    internal val balances = repository.observeBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val id = savedState.get<Long>("id")
    internal val detail = id?.let {
        repository.observeDetail(it).map(::LoadedGoal)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    internal val today = calendar.days
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now(clock))

    private val _write = MutableStateFlow(GoalWriteState())
    internal val write = _write.asStateFlow()

    internal fun save(name: String, target: Long, deadline: LocalDate?, icon: String) =
        mutate { repository.save(id, name, target, deadline, icon) }

    internal fun addEntry(amount: Long, date: LocalDate, note: String) =
        mutate { repository.addEntry(checkNotNull(id), amount, date, note) }

    internal fun archive(archived: Boolean) = mutate { repository.setArchived(checkNotNull(id), archived) }

    internal fun delete() = mutate { repository.delete(checkNotNull(id)) }

    internal fun resetWrite() {
        if (!_write.value.busy) _write.value = GoalWriteState()
    }

    private fun mutate(action: suspend () -> GoalWriteResult) {
        if (_write.value.busy || _write.value.saved) return
        _write.value = GoalWriteState(busy = true)
        viewModelScope.launch {
            _write.value = try {
                when (action()) {
                    GoalWriteResult.SAVED -> GoalWriteState(saved = true)
                    GoalWriteResult.INVALID -> GoalWriteState(error = R.string.goal_invalid)
                    GoalWriteResult.UNAVAILABLE -> GoalWriteState(error = R.string.goal_unavailable)
                    GoalWriteResult.OVERDRAWN -> GoalWriteState(error = R.string.goal_overdrawn)
                    GoalWriteResult.LIMIT_EXCEEDED -> GoalWriteState(error = R.string.goal_limit)
                }
            } catch (_: SQLiteException) {
                GoalWriteState(error = R.string.goal_write_error)
            }
        }
    }
}
