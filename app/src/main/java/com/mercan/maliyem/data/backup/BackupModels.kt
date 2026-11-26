package com.mercan.maliyem.data.backup

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
) {
    companion object {
        const val FORMAT = "maliyem-backup"
        const val VERSION = 1
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
)
