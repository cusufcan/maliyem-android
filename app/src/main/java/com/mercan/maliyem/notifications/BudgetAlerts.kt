package com.mercan.maliyem.notifications

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.mercan.maliyem.data.local.BudgetDao
import com.mercan.maliyem.data.local.BudgetEntity
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigInteger
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal fun crossedBudgetThresholds(before: BigInteger, after: BigInteger, limitMinor: Long): List<Int> {
    val beforePercent = before * 100.toBigInteger()
    val afterPercent = after * 100.toBigInteger()
    return listOf(80, 100).filter { threshold ->
        val boundary = limitMinor.toBigInteger() * threshold.toBigInteger()
        beforePercent < boundary && afterPercent >= boundary
    }
}

internal data class BudgetAlert(
    val month: YearMonth,
    val budget: BudgetEntity?,
    val category: CategoryEntity?,
    val threshold: Int,
) {
    val key: String
        get() {
            val budgetKey = budget?.let { "${it.id}:${it.categoryId}:${it.createdAt}" } ?: "overall"
            return "$month:$budgetKey:$threshold"
        }
}

data class ExpenseWrite<T>(
    val result: T,
    val added: List<TransactionEntity> = emptyList(),
    val replaced: TransactionEntity? = null,
)

/** Keep the spending snapshot in the same Room transaction as the write that changed it. */
interface BudgetAlertTracker {
    suspend fun <T> track(write: suspend () -> ExpenseWrite<T>): T
}

@Singleton
class BudgetAlerts @Inject constructor(
    @ApplicationContext context: Context,
    private val db: MaliyemDatabase,
    private val budgets: BudgetDao,
    private val notifications: LocalNotifications,
) : BudgetAlertTracker {
    private val sent = context.getSharedPreferences("budget_alerts_sent", Context.MODE_PRIVATE)

    override suspend fun <T> track(write: suspend () -> ExpenseWrite<T>): T {
        val (result, alerts) = db.withTransaction {
            val change = write()
            val alerts = if (change.added.isNotEmpty() && notifications.canPostBudgetAlerts()) {
                findAlerts(change)
            } else {
                emptyList()
            }
            change.result to alerts
        }
        if (alerts.isNotEmpty()) withContext(NonCancellable + Dispatchers.IO) {
            publish(alerts)
        }
        return result
    }

    private suspend fun findAlerts(change: ExpenseWrite<*>): List<BudgetAlert> {
        val expensesByMonth = change.added.asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { YearMonth.from(it.date) }
        if (expensesByMonth.isEmpty()) return emptyList()
        val overall = budgets.getOverall()
        val categoryBudgets = budgets.getAll().associateBy { it.categoryId }
        if (overall == null && categoryBudgets.isEmpty()) return emptyList()

        return buildList {
            for ((month, expenses) in expensesByMonth) {
                val previous = change.replaced?.takeIf {
                    it.type == TransactionType.EXPENSE && YearMonth.from(it.date) == month
                }
                val byCategory = expenses.groupBy { it.categoryId }
                if (overall == null && byCategory.keys.none(categoryBudgets::containsKey)) continue
                val spending = budgets.expenses(month.atDay(1), month.atEndOfMonth())
                    .groupingBy { it.categoryId }
                    .fold(BigInteger.ZERO) { sum, entry -> sum + entry.amountMinor.toBigInteger() }

                if (overall != null) {
                    val after = spending.values.fold(BigInteger.ZERO, BigInteger::add)
                    val before = after - expenses.sumOf { it.amountMinor.toBigInteger() } +
                        (previous?.amountMinor ?: 0L).toBigInteger()
                    for (threshold in crossedBudgetThresholds(before, after, overall.limitMinor)) {
                        add(BudgetAlert(month, null, null, threshold))
                    }
                }

                for ((categoryId, entries) in byCategory) {
                    val budget = categoryBudgets[categoryId] ?: continue
                    val after = spending[categoryId] ?: BigInteger.ZERO
                    val replaced = previous?.takeIf { it.categoryId == categoryId }?.amountMinor ?: 0L
                    val before = after - entries.sumOf { it.amountMinor.toBigInteger() } + replaced.toBigInteger()
                    val thresholds = crossedBudgetThresholds(before, after, budget.limitMinor)
                    if (thresholds.isEmpty()) continue
                    val category = budgets.getCategory(categoryId) ?: continue
                    for (threshold in thresholds) add(BudgetAlert(month, budget, category, threshold))
                }
            }
        }
    }

    @Synchronized
    private fun publish(alerts: List<BudgetAlert>) {
        val posted = alerts.filter { !sent.getBoolean(it.key, false) && notifications.budgetAlert(it.key, it) }
        if (posted.isNotEmpty()) sent.edit(commit = true) {
            for (alert in posted) putBoolean(alert.key, true)
        }
    }
}
