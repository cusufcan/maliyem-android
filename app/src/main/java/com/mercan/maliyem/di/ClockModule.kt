package com.mercan.maliyem.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** "Bugün" tek kaynaktan gelir; testte sabit saat verilebilir (ADR-0007). */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = object : Clock() {
        override fun getZone() = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId) = system(zone)
        override fun instant() = Instant.now()
    }
}
