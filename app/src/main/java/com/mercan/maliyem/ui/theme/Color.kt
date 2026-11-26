package com.mercan.maliyem.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Seyir paleti (Kilo Takibi ile aynı); ekranlar MaterialTheme.colorScheme'den okur.
val LightColors = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDDCFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF7C6299),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDECAF1),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1D1A21),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1D1A21),
    surfaceVariant = Color(0xFFE7E0EF),
    surfaceContainerLow = Color(0xFFF7F2FB),
    surfaceContainer = Color(0xFFF1ECF5),
    surfaceContainerHigh = Color(0xFFECE6F1),
    onSurfaceVariant = Color(0xFF4F4559),
    outline = Color(0xFF7F7389),
    outlineVariant = Color(0xFFD6CFDD),
    scrim = Color(0xA81D112B),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFDBB8FF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF3C245B),
    onPrimaryContainer = Color(0xFFEDDCFF),
    secondary = Color(0xFFAA96BF),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4D336E),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF151218),
    onBackground = Color(0xFFE7E0EE),
    surface = Color(0xFF151218),
    onSurface = Color(0xFFE7E0EE),
    surfaceVariant = Color(0xFF413D4A),
    surfaceContainerLow = Color(0xFF1B171F),
    surfaceContainer = Color(0xFF221E27),
    surfaceContainerHigh = Color(0xFF2B2630),
    onSurfaceVariant = Color(0xFFCDC2D8),
    outline = Color(0xFF978CA3),
    outlineVariant = Color(0xFF50445D),
    scrim = Color(0xA81D112B),
)

/**
 * Gelir/gider rengi: Material şemasında "gelir" rolü yok, ayrı tutulur.
 * Gelir yeşil durum çifti; gider nötr (kırmızı kullanılmaz, işaret "−" anlamı taşır).
 */
@Immutable
data class MoneyColors(
    val incomeContainer: Color,
    val onIncomeContainer: Color,
)

val LightMoneyColors = MoneyColors(
    incomeContainer = Color(0xFFDCECE2),
    onIncomeContainer = Color(0xFF22604D),
)

val DarkMoneyColors = MoneyColors(
    incomeContainer = Color(0xFF203A30),
    onIncomeContainer = Color(0xFFB7DEC9),
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }
