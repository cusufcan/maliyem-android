package com.mercan.maliyem.ui.navigation

import com.mercan.maliyem.data.local.TransactionType
import kotlinx.serialization.Serializable

@Serializable data object Summary
@Serializable data object Intro
@Serializable data object YearlyReport
@Serializable data object Transactions
@Serializable data class BudgetTransactions(
    val budgetCategoryId: Long,
    val yearMonth: String? = null,
    val type: String = TransactionType.EXPENSE.name,
)
@Serializable data object Breakdown
@Serializable data object Settings
@Serializable data class CsvImport(val uri: String)
@Serializable data object Budgets
@Serializable data class TransactionDetail(val id: Long, val transfer: Boolean = false)
@Serializable data object Categories
@Serializable data object Accounts
@Serializable data object Recurring
@Serializable data class RecurringRule(val id: Long, val tab: Int = -1)
@Serializable data class SavingsGoals(val fromSettings: Boolean = false)
@Serializable data class SavingsGoalDetail(val id: Long, val fromSettings: Boolean = false)
