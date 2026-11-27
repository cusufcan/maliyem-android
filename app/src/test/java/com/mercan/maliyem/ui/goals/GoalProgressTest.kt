package com.mercan.maliyem.ui.goals

import com.mercan.maliyem.data.local.GoalBalance
import com.mercan.maliyem.data.local.SavingsGoalEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressTest {
    private val today = LocalDate.of(2026, 1, 31)
    private val goal = SavingsGoalEntity(1, "Trip", 1_000, null, "plane", 0)

    @Test fun `remaining and progress use kuruş and completion is capped`() {
        assertEquals(GoalProgress(34, 655, null, false), GoalBalance(goal, 345).progress(today))
        assertEquals(GoalProgress(100, 0, null, false), GoalBalance(goal, 1_100).progress(today))
        assertEquals(99, GoalBalance(goal.copy(targetMinor = Long.MAX_VALUE), Long.MAX_VALUE - 1).percent)
    }

    @Test fun `monthly pace counts partial months and rounds up each share`() {
        fun by(deadline: LocalDate) = GoalBalance(goal.copy(deadline = deadline), 5).progress(today)
        assertEquals(995L, by(today).monthlyMinor)
        assertEquals(995L, by(today.plusDays(1)).monthlyMinor)
        assertEquals(995L, by(today.plusMonths(1)).monthlyMinor)
        assertEquals(498L, by(today.plusMonths(1).plusDays(1)).monthlyMinor)
        assertEquals(GoalProgress(0, 995, null, true), by(today.minusDays(1)))
        assertEquals(GoalProgress(100, 0, 0, false), GoalBalance(goal.copy(deadline = today.minusDays(1)), 1_000).progress(today))
    }

    @Test fun `summary prefers unfinished active goals and keeps only two`() {
        val complete = GoalBalance(goal, 1_000)
        val archived = GoalBalance(goal.copy(id = 2, archived = true), 0)
        val first = GoalBalance(goal.copy(id = 3), 200)
        val second = GoalBalance(goal.copy(id = 4), 0)
        assertEquals(listOf(first, second), summaryGoals(listOf(complete, archived, first, second)))
        assertEquals(listOf(first, complete), summaryGoals(listOf(complete, archived, first)))
    }
}
