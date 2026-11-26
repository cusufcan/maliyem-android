package com.mercan.maliyem.di

import com.mercan.maliyem.data.repository.RoomTransactionRepository
import com.mercan.maliyem.data.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository
}
