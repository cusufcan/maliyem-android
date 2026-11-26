package com.mercan.maliyem.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Hazır hesabın adı [key] ile yerelleştirilir. Arşivdeki hesap eski kayıtlarda görünür. */
@Entity(tableName = "accounts", indices = [Index(value = ["key"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String? = null,
    val name: String? = null,
    val icon: String,
    val sortOrder: Int,
    val initialBalanceMinor: Long = 0,
    val archived: Boolean = false,
)
