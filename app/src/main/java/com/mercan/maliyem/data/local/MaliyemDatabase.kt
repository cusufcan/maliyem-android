package com.mercan.maliyem.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, AccountEntity::class, TransferEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MaliyemDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    abstract fun accountDao(): AccountDao
    abstract fun transferDao(): TransferDao

    companion object {
        const val NAME = "maliyem.db"
    }
}
