package com.mercan.maliyem.ui.theme

import androidx.annotation.DrawableRes
import com.mercan.maliyem.R

private val categoryIcons = mapOf(
    "cart" to R.drawable.ic_seyir_cart,
    "home" to R.drawable.ic_seyir_category_home,
    "bolt" to R.drawable.ic_seyir_bolt,
    "bus" to R.drawable.ic_seyir_bus,
    "fork" to R.drawable.ic_seyir_cup,
    "heart" to R.drawable.ic_seyir_heart,
    "dots" to R.drawable.ic_seyir_dots,
    "briefcase" to R.drawable.ic_seyir_briefcase,
    "laptop" to R.drawable.ic_seyir_laptop,
)

/** Veritabanındaki simge anahtarını çizime çevirir; bilinmeyen anahtar "dots" olur. */
@DrawableRes
internal fun categoryIcon(key: String): Int = categoryIcons[key] ?: R.drawable.ic_seyir_dots
