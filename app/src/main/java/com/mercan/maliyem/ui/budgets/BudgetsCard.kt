package com.mercan.maliyem.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.LocalBudgetWarning
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.util.formatMoney
import java.math.BigInteger
import java.util.Locale

private const val TOP_BUDGET_COUNT = 3

@Composable
internal fun BudgetsCard(budgets: MonthlyBudgets, locale: Locale, onOpen: (Long?) -> Unit) {
    if (budgets.isEmpty) return

    SeyirCard(padding = PaddingValues(vertical = Spacing.xs)) {
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm)) {
            CardLabel(stringResource(R.string.settings_budgets))
        }
        budgets.overall?.let {
            BudgetRow(stringResource(R.string.budget_overall), it, locale) { onOpen(null) }
        }
        budgets.categories.take(TOP_BUDGET_COUNT).forEachIndexed { index, (category, progress) ->
            if (index > 0 || budgets.overall != null) {
                HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            }
            BudgetRow(categoryName(category.key, category.name), progress, locale) { onOpen(category.id) }
        }
    }
}

@Composable
private fun BudgetRow(title: String, progress: BudgetProgress, locale: Locale, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = when (progress.status) {
        BudgetStatus.NORMAL -> colors.onSurfaceVariant
        BudgetStatus.WARNING -> LocalBudgetWarning.current
        BudgetStatus.OVER -> colors.error
    }
    val percent = stringResource(R.string.budget_percent, progress.percent.toString())
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(percent, color = tint, style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(),
                color = tint, trackColor = colors.outlineVariant,
            )
            SubText(budgetAmounts(progress.spentMinor, progress.limitMinor, locale))
            Text(
                stringResource(
                    if (progress.status == BudgetStatus.OVER) R.string.budget_over else R.string.budget_remaining,
                    formatMoney(progress.remainingMinor.abs(), locale),
                ),
                color = tint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SeyirIcon(R.drawable.ic_seyir_chevron_right, tint = colors.onSurfaceVariant)
    }
}

@Composable
internal fun budgetAmounts(spent: BigInteger, limit: Long, locale: Locale): String =
    stringResource(R.string.budget_spent_limit, formatMoney(spent, locale), formatMoney(limit, locale))
