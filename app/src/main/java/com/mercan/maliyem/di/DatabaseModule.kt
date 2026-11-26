package com.mercan.maliyem.di

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mercan.maliyem.data.local.AccountDao
import com.mercan.maliyem.data.local.CategoryDao
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.MIGRATION_1_2
import com.mercan.maliyem.data.local.MIGRATION_2_3
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.RecurringRuleDao
import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MaliyemDatabase =
        Room.databaseBuilder(context, MaliyemDatabase::class.java, MaliyemDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(SeedDefaults)
            .build()

    @Provides fun provideAccountDao(db: MaliyemDatabase): AccountDao = db.accountDao()

    @Provides fun provideRecurringRuleDao(db: MaliyemDatabase): RecurringRuleDao = db.recurringRuleDao()

    @Provides fun provideTransferDao(db: MaliyemDatabase): TransferDao = db.transferDao()

    @Provides fun provideCategoryDao(db: MaliyemDatabase): CategoryDao = db.categoryDao()

    @Provides fun provideTransactionDao(db: MaliyemDatabase): TransactionDao = db.transactionDao()
}

/** Veritabanı ilk kez oluşturulurken hazır hesap ve kategorileri aynı işlemde ekler. */
private object SeedDefaults : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultAccounts.seed(db)
        DefaultCategories.entities().forEach { c ->
            db.insert(
                "categories",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("type", c.type.name)
                    put("key", c.key)
                    put("icon", c.icon)
                    put("sortOrder", c.sortOrder)
                },
            )
        }
    }
}
