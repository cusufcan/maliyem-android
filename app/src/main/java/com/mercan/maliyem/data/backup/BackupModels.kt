package com.mercan.maliyem.data.backup

import com.mercan.maliyem.data.local.RecurringFrequency
import com.mercan.maliyem.data.local.TransactionType
import kotlinx.serialization.Serializable

/** JSON biçimi Room şemasından bağımsızdır. Sonradan eklenen listeler boş varsayılanla gelir (ADR-0018). */
@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val accounts: List<BackupAccount> = emptyList(),
    val transfers: List<BackupTransfer> = emptyList(),
    val recurringRules: List<BackupRecurringRule> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val overallBudgetMinor: Long? = null,
    val savingsGoals: List<BackupSavingsGoal> = emptyList(),
    val goalEntries: List<BackupGoalEntry> = emptyList(),
) {
    companion object {
        const val FORMAT = "maliyem-backup"
        const val VERSION = 6
    }
}

@Serializable
data class BackupCategory(
    val id: Long,
    val type: TransactionType,
    val key: String? = null,
    val name: String? = null,
    val icon: String,
    val sortOrder: Int,
)

@Serializable
data class BackupTransaction(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val date: String,
    val note: String? = null,
    val createdAt: Long,
    val accountId: Long = 0,
    val recurringRuleId: Long? = null,
    val receiptPath: String? = null,
)

@Serializable
data class BackupAccount(
    val id: Long,
    val key: String? = null,
    val name: String? = null,
    val icon: String,
    val sortOrder: Int,
    val initialBalanceMinor: Long = 0,
    val archived: Boolean = false,
)

@Serializable
data class BackupTransfer(
    val id: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountMinor: Long,
    val date: String,
    val note: String? = null,
    val createdAt: Long,
)

@Serializable
data class BackupRecurringRule(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String? = null,
    val frequency: RecurringFrequency,
    val startDate: String,
    val nextDate: String,
    val endDate: String? = null,
    val paused: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class BackupBudget(
    val id: Long,
    val categoryId: Long,
    val limitMinor: Long,
    val createdAt: Long,
)

@Serializable
data class BackupSavingsGoal(
    val id: Long,
    val name: String,
    val targetMinor: Long,
    val deadline: String? = null,
    val icon: String,
    val createdAt: Long,
    val archived: Boolean = false,
)

@Serializable
data class BackupGoalEntry(
    val id: Long,
    val goalId: Long,
    val amountMinor: Long,
    val date: String,
    val note: String? = null,
)
