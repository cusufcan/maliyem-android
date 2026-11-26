package com.mercan.maliyem.ui.budgets

import com.mercan.maliyem.data.local.BudgetEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.math.BigInteger
import java.time.YearMonth

enum class BudgetStatus { NORMAL, WARNING, OVER }

data class BudgetProgress(val spentMinor: BigInteger, val limitMinor: Long) {
    private val limit = limitMinor.toBigInteger()
    private val scaledSpend = spentMinor * 100.toBigInteger()

    val remainingMinor: BigInteger = limit - spentMinor
    val percent: BigInteger = scaledSpend / limit
    val fraction: Float = spentMinor.min(limit).toFloat() / limitMinor.toFloat()
    val status: BudgetStatus = when {
        remainingMinor.signum() < 0 -> BudgetStatus.OVER
        scaledSpend >= limit * 80.toBigInteger() -> BudgetStatus.WARNING
        else -> BudgetStatus.NORMAL
    }
}

data class CategoryBudgetProgress(val category: CategoryEntity, val progress: BudgetProgress)

data class MonthlyBudgets(
    val overall: BudgetProgress? = null,
    val categories: List<CategoryBudgetProgress> = emptyList(),
) {
    val isEmpty: Boolean get() = overall == null && categories.isEmpty()
}

/** Seçili ayın giderlerini toplar. Genel bütçe, limiti olmayan kategorilerin giderlerini de kapsar. */
fun buildMonthlyBudgets(
    month: YearMonth,
    rows: List<TransactionWithCategory>,
    budgets: List<BudgetEntity>,
    overallLimitMinor: Long?,
    categories: List<CategoryEntity>,
): MonthlyBudgets {
    if (budgets.isEmpty() && overallLimitMinor == null) return MonthlyBudgets()

    val spending = mutableMapOf<Long, BigInteger>()
    for (row in rows) {
        val transaction = row.transaction
        if (transaction.type == TransactionType.EXPENSE && YearMonth.from(transaction.date) == month) {
            val id = transaction.categoryId
            spending[id] = spending.getOrDefault(id, BigInteger.ZERO) + transaction.amountMinor.toBigInteger()
        }
    }
    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }.associateBy { it.id }
    val progress = budgets.mapNotNull { budget ->
        val category = expenseCategories[budget.categoryId] ?: return@mapNotNull null
        CategoryBudgetProgress(
            category,
            BudgetProgress(spending.getOrDefault(category.id, BigInteger.ZERO), budget.limitMinor),
        )
    }
    return MonthlyBudgets(
        overall = overallLimitMinor?.let { BudgetProgress(spending.values.fold(BigInteger.ZERO, BigInteger::add), it) },
        categories = progress.sortedWith(::compareUsage),
    )
}

/** Ekrandaki yuvarlamadan bağımsız olarak gerçek kullanım oranına göre sıralar. */
private fun compareUsage(first: CategoryBudgetProgress, second: CategoryBudgetProgress): Int {
    val a = first.progress
    val b = second.progress
    val comparison = (b.spentMinor * a.limitMinor.toBigInteger()).compareTo(a.spentMinor * b.limitMinor.toBigInteger())
    return if (comparison == 0) first.category.id.compareTo(second.category.id) else comparison
}
