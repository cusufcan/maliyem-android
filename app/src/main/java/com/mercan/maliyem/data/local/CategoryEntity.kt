package com.mercan.maliyem.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kategori. Hazır kategorilerde [key] dolu, ad strings.xml'den gelir (dil değişince ad da değişir);
 * kullanıcının eklediklerinde [name] dolu, [key] boş (ADR-0004).
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["key"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val key: String? = null,
    val name: String? = null,
    val icon: String,
    val sortOrder: Int,
)
