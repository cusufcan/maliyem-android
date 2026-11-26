package com.mercan.maliyem.di

import com.mercan.maliyem.data.backup.BackupRepository
import com.mercan.maliyem.data.backup.RoomBackupRepository
import com.mercan.maliyem.data.repository.CategoryRepository
import com.mercan.maliyem.data.repository.RoomCategoryRepository
import com.mercan.maliyem.data.repository.RoomTransactionRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.notifications.BudgetAlertTracker
import com.mercan.maliyem.notifications.BudgetAlerts
import com.mercan.maliyem.widget.GlanceWidgetUpdater
import com.mercan.maliyem.widget.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    @Binds abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds internal abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater

    @Binds abstract fun bindBudgetAlertTracker(impl: BudgetAlerts): BudgetAlertTracker

    @Binds abstract fun bindBackupRepository(impl: RoomBackupRepository): BackupRepository
}
