package com.mercan.maliyem.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** "Bugün" tek kaynaktan gelir; testte sabit saat verilebilir (ADR-0007). */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides fun provideClock(): Clock = Clock.systemDefaultZone()
}
