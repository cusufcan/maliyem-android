package com.mercan.maliyem.di

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mercan.maliyem.data.local.CategoryDao
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.TransactionDao
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
            .addCallback(SeedDefaultCategories)
            .build()

    @Provides fun provideCategoryDao(db: MaliyemDatabase): CategoryDao = db.categoryDao()

    @Provides fun provideTransactionDao(db: MaliyemDatabase): TransactionDao = db.transactionDao()
}

/** Veritabanı ilk kez oluşturulurken hazır kategorileri aynı işlemde ekler. */
private object SeedDefaultCategories : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
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
