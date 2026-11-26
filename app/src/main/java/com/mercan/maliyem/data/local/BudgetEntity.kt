package com.mercan.maliyem.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Gider kategorisinin her ay geçerli olan kuruş cinsinden limiti. */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val limitMinor: Long,
    val createdAt: Long,
) {
    init {
        require(limitMinor > 0)
    }
}

/** Genel limit tek satırdır. Satır yoksa genel bütçe kapalıdır. */
@Entity(tableName = "overall_budget")
data class OverallBudgetEntity(
    @PrimaryKey val id: Int = 1,
    val limitMinor: Long,
) {
    init {
        require(id == 1 && limitMinor > 0)
    }
}
