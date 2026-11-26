package com.mercan.maliyem.data.local

import androidx.annotation.StringRes
import com.mercan.maliyem.R

/** Hazır kategori: [key] veritabanında, [nameRes] ekranda gösterilen ad. */
data class DefaultCategory(
    val key: String,
    val type: TransactionType,
    @StringRes val nameRes: Int,
    val icon: String,
)

/** İlk açılışta eklenen kategoriler; sıra listedeki sıradır. Her türün son öğesi "Diğer"dir. */
object DefaultCategories {
    const val OTHER_EXPENSE = "other_expense"
    const val OTHER_INCOME = "other_income"

    val all: List<DefaultCategory> = listOf(
        DefaultCategory("groceries", TransactionType.EXPENSE, R.string.category_groceries, "cart"),
        DefaultCategory("rent", TransactionType.EXPENSE, R.string.category_rent, "home"),
        DefaultCategory("bills", TransactionType.EXPENSE, R.string.category_bills, "bolt"),
        DefaultCategory("transport", TransactionType.EXPENSE, R.string.category_transport, "bus"),
        DefaultCategory("dining", TransactionType.EXPENSE, R.string.category_dining, "fork"),
        DefaultCategory("health", TransactionType.EXPENSE, R.string.category_health, "heart"),
        DefaultCategory(OTHER_EXPENSE, TransactionType.EXPENSE, R.string.category_other_expense, "dots"),
        DefaultCategory("salary", TransactionType.INCOME, R.string.category_salary, "briefcase"),
        DefaultCategory("freelance", TransactionType.INCOME, R.string.category_freelance, "laptop"),
        DefaultCategory(OTHER_INCOME, TransactionType.INCOME, R.string.category_other_income, "dots"),
    )

    private val byKey = all.associateBy { it.key }

    /** Hazır kategorinin ekran adı; kullanıcı kategorisi ya da bilinmeyen anahtarsa null. */
    @StringRes
    fun nameRes(key: String?): Int? = key?.let { byKey[it]?.nameRes }

    fun entities(): List<CategoryEntity> = all.groupBy { it.type }.flatMap { (_, list) ->
        list.mapIndexed { index, c ->
            CategoryEntity(type = c.type, key = c.key, icon = c.icon, sortOrder = index)
        }
    }
}
