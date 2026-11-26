package com.mercan.maliyem.ui

import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Özet'te seçilen ay; İşlemler ve Dağılım aynı ayı gösterir (ADR-0009).
 * Süreç ölümüne karşı AppViewModel değeri SavedStateHandle'a yansıtır.
 */
@Singleton
class MonthSelection @Inject constructor(clock: Clock) {
    private val state = MutableStateFlow(YearMonth.now(clock))
    val month: StateFlow<YearMonth> = state.asStateFlow()

    fun select(month: YearMonth) { state.value = month }
}
