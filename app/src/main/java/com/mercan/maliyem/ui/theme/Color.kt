package com.mercan.maliyem.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Maliyem’in Seyir paleti.
val LightColors = lightColorScheme(
    primary = Color(0xFF6A3DE8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DBFF),
    onPrimaryContainer = Color(0xFF23005C),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F0EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFE8664A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF3B0900),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1D1A21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1D1A21),
    surfaceVariant = Color(0xFFE5DEF3),
    surfaceContainerLow = Color(0xFFF6F1FF),
    surfaceContainer = Color(0xFFEFE9FB),
    surfaceContainerHigh = Color(0xFFE8E1F7),
    onSurfaceVariant = Color(0xFF4F4559),
    outline = Color(0xFF7F7389),
    outlineVariant = Color(0xFFD6CFDD),
    scrim = Color(0xA81D112B),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFCDB8FF),
    onPrimary = Color(0xFF36007F),
    primaryContainer = Color(0xFF4E2BB8),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = Color(0xFF6FD8C8),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFF8EF5E4),
    tertiary = Color(0xFFFFB4A1),
    onTertiary = Color(0xFF5F1500),
    tertiaryContainer = Color(0xFF7E2B16),
    onTertiaryContainer = Color(0xFFFFDBD1),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE7E0EE),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE7E0EE),
    surfaceVariant = Color(0xFF47445A),
    surfaceContainerLow = Color(0xFF1C1A22),
    surfaceContainer = Color(0xFF221F2A),
    surfaceContainerHigh = Color(0xFF2C2935),
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

/** Seçili segment ve çip (pano --sel/--osel); şemada tam karşılığı yok. */
@Immutable
data class SelectionColors(val container: Color, val content: Color)

val LightSelectionColors = SelectionColors(LightColors.primary, LightColors.onPrimary)
val DarkSelectionColors = SelectionColors(DarkColors.primary, DarkColors.onPrimary)

val LocalSelectionColors = staticCompositionLocalOf { LightSelectionColors }

/** Bütçe sınırına yaklaşan harcamalar için Seyir uyarı tonu. */
internal val LightBudgetWarning = Color(0xFF78502F)
internal val DarkBudgetWarning = Color(0xFFEDC7AA)
internal val LocalBudgetWarning = staticCompositionLocalOf { LightBudgetWarning }

internal val LightCategoryPalette = listOf(
    LightColors.primary, Color(0xFF007D70), Color(0xFFC54F39), Color(0xFF3968C6),
    Color(0xFF9D6410), Color(0xFFA63C82), Color(0xFF5A791D), Color(0xFF605A76),
)

internal val DarkCategoryPalette = listOf(
    DarkColors.primary, DarkColors.secondary, DarkColors.tertiary, Color(0xFF91B7FF),
    Color(0xFFF8CD71), Color(0xFFF2A1D7), Color(0xFFB8D983), Color(0xFFB4AEC9),
)

internal val LocalCategoryPalette = staticCompositionLocalOf { LightCategoryPalette }
internal val LocalBackdropAlpha = staticCompositionLocalOf { 0.09f }
