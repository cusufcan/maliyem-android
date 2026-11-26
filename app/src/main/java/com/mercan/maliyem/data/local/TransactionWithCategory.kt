package com.mercan.maliyem.data.local

import androidx.room.Embedded
import androidx.room.Relation

/** Kayıt ve bağlı kategorisi; Room iki tabloyu tek sorguda eşleştirir (@Relation). */
data class TransactionWithCategory(
    @Embedded val transaction: TransactionEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity,
)
