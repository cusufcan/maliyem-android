package com.mercan.maliyem.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object Summary
@Serializable data object Transactions
@Serializable data object Breakdown
@Serializable data object Settings
@Serializable data class TransactionDetail(val id: Long)
@Serializable data object Categories
