package com.mercan.maliyem.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object Summary
@Serializable data object Transactions
@Serializable data class BudgetTransactions(val budgetCategoryId: Long)
@Serializable data object Breakdown
@Serializable data object Settings
@Serializable data object Budgets
@Serializable data class TransactionDetail(val id: Long, val transfer: Boolean = false)
@Serializable data object Categories
@Serializable data object Accounts
@Serializable data object Recurring
@Serializable data class RecurringRule(val id: Long, val tab: Int = -1)
