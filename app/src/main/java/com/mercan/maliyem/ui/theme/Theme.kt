package com.mercan.maliyem.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

// Çip 12, FAB 16, kart/liste 20, hero 28.
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Uygulama teması; şimdilik telefonun açık/koyu ayarını izler (tema seçimi Ayarlar'la gelir). */
@Composable
fun MaliyemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalReducedMotion provides rememberReducedMotion(),
        LocalMoneyColors provides if (darkTheme) DarkMoneyColors else LightMoneyColors,
        LocalCategoryPalette provides if (darkTheme) DarkCategoryPalette else LightCategoryPalette,
        LocalBackdropAlpha provides if (darkTheme) 0.06f else 0.09f,
        LocalSelectionColors provides if (darkTheme) DarkSelectionColors else LightSelectionColors,
        LocalBudgetWarning provides if (darkTheme) DarkBudgetWarning else LightBudgetWarning,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            typography = SeyirTypography,
            content = content,
        )
    }
}
