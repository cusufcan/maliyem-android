package com.mercan.maliyem.data.local

import androidx.room.Embedded
import androidx.room.Relation

/** Kayıt ve bağlı kategorisi; Room iki tablonun sonuçlarını @Relation ile eşleştirir. */
data class TransactionWithCategory(
    @Embedded val transaction: TransactionEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity,
)
