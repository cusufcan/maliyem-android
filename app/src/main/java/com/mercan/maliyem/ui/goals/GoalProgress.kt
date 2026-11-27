package com.mercan.maliyem.ui.goals

import com.mercan.maliyem.data.local.GoalBalance
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit.MONTHS

internal data class GoalProgress(
    val percent: Int,
    val remainingMinor: Long,
    val monthlyMinor: Long?,
    val overdue: Boolean,
) {
    val complete: Boolean get() = remainingMinor == 0L
}

internal val GoalBalance.percent: Int
    get() = if (savedMinor >= goal.targetMinor) 100 else
        (savedMinor.toBigInteger() * 100.toBigInteger() / goal.targetMinor.toBigInteger()).toInt()

internal fun GoalBalance.progress(today: LocalDate): GoalProgress {
    val target = goal.targetMinor
    val remaining = (target - savedMinor).coerceAtLeast(0L)
    val deadline = goal.deadline
    val overdue = deadline != null && deadline < today && remaining > 0
    val monthly = when {
        deadline == null || overdue -> null
        deadline <= today -> remaining
        else -> {
            val wholeMonths = MONTHS.between(YearMonth.from(today), YearMonth.from(deadline))
            val months = (wholeMonths + if (today.plusMonths(wholeMonths) < deadline) 1 else 0).coerceAtLeast(1)
            // Round up to the next kuruş without overflowing Long.
            remaining / months + if (remaining % months == 0L) 0 else 1
        }
    }
    return GoalProgress(percent, remaining, monthly, overdue)
}

internal fun summaryGoals(goals: List<GoalBalance>): List<GoalBalance> = goals
    .filterNot { it.goal.archived }
    .sortedBy { it.savedMinor >= it.goal.targetMinor }
    .take(2)
