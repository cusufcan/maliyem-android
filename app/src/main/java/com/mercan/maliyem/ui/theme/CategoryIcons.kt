package com.mercan.maliyem.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mercan.maliyem.R

/** Kategori simgesi: çizim ve ekran okuyucu adı (ADR-0017). Veritabanına yalnız anahtar yazılır. */
internal data class CategoryIconDef(@param:DrawableRes val drawable: Int, @param:StringRes val label: Int)

internal val categoryIcons: Map<String, CategoryIconDef> = linkedMapOf(
    "cart" to CategoryIconDef(R.drawable.ic_seyir_cart, R.string.icon_cart),
    "home" to CategoryIconDef(R.drawable.ic_seyir_category_home, R.string.icon_home),
    "bolt" to CategoryIconDef(R.drawable.ic_seyir_bolt, R.string.icon_bolt),
    "bus" to CategoryIconDef(R.drawable.ic_seyir_bus, R.string.icon_bus),
    "fork" to CategoryIconDef(R.drawable.ic_seyir_cup, R.string.icon_fork),
    "heart" to CategoryIconDef(R.drawable.ic_seyir_heart, R.string.icon_heart),
    "briefcase" to CategoryIconDef(R.drawable.ic_seyir_briefcase, R.string.icon_briefcase),
    "laptop" to CategoryIconDef(R.drawable.ic_seyir_laptop, R.string.icon_laptop),
    "shirt" to CategoryIconDef(R.drawable.ic_seyir_cat_shirt, R.string.icon_shirt),
    "coffee" to CategoryIconDef(R.drawable.ic_seyir_cat_coffee, R.string.icon_coffee),
    "book" to CategoryIconDef(R.drawable.ic_seyir_cat_book, R.string.icon_book),
    "game" to CategoryIconDef(R.drawable.ic_seyir_cat_game, R.string.icon_game),
    "film" to CategoryIconDef(R.drawable.ic_seyir_cat_film, R.string.icon_film),
    "music" to CategoryIconDef(R.drawable.ic_seyir_cat_music, R.string.icon_music),
    "gift" to CategoryIconDef(R.drawable.ic_seyir_cat_gift, R.string.icon_gift),
    "pet" to CategoryIconDef(R.drawable.ic_seyir_cat_pet, R.string.icon_pet),
    "baby" to CategoryIconDef(R.drawable.ic_seyir_cat_baby, R.string.icon_baby),
    "sport" to CategoryIconDef(R.drawable.ic_seyir_cat_sport, R.string.icon_sport),
    "plane" to CategoryIconDef(R.drawable.ic_seyir_cat_plane, R.string.icon_plane),
    "car" to CategoryIconDef(R.drawable.ic_seyir_cat_car, R.string.icon_car),
    "fuel" to CategoryIconDef(R.drawable.ic_seyir_cat_fuel, R.string.icon_fuel),
    "phone" to CategoryIconDef(R.drawable.ic_seyir_cat_phone, R.string.icon_phone),
    "wifi" to CategoryIconDef(R.drawable.ic_seyir_cat_wifi, R.string.icon_wifi),
    "water" to CategoryIconDef(R.drawable.ic_seyir_cat_water, R.string.icon_water),
    "pill" to CategoryIconDef(R.drawable.ic_seyir_cat_pill, R.string.icon_pill),
    "scissors" to CategoryIconDef(R.drawable.ic_seyir_cat_scissors, R.string.icon_scissors),
    "building" to CategoryIconDef(R.drawable.ic_seyir_cat_building, R.string.icon_building),
    "card" to CategoryIconDef(R.drawable.ic_seyir_cat_card, R.string.icon_card),
    "piggy" to CategoryIconDef(R.drawable.ic_seyir_cat_piggy, R.string.icon_piggy),
    "chart" to CategoryIconDef(R.drawable.ic_seyir_cat_chart, R.string.icon_chart),
    "key" to CategoryIconDef(R.drawable.ic_seyir_cat_key, R.string.icon_key),
    "percent" to CategoryIconDef(R.drawable.ic_seyir_cat_percent, R.string.icon_percent),
    "star" to CategoryIconDef(R.drawable.ic_seyir_cat_star, R.string.icon_star),
    "tools" to CategoryIconDef(R.drawable.ic_seyir_cat_tools, R.string.icon_tools),
    "dots" to CategoryIconDef(R.drawable.ic_seyir_dots, R.string.icon_dots),
)

/** Veritabanındaki simge anahtarını çizime çevirir; bilinmeyen anahtar "dots" olur. */
@DrawableRes
internal fun categoryIcon(key: String): Int = categoryIcons[key]?.drawable ?: R.drawable.ic_seyir_dots
