package com.mercan.maliyem.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Gelir veya gider kaydı. [amountMinor] kuruş cinsinden ve her zaman > 0 (ADR-0002).
 * Kayıtlı kategori silinemez (RESTRICT); silmeden önce kayıtlar "Diğer"e taşınır.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val date: LocalDate,
    val note: String? = null,
    val createdAt: Long,
)
