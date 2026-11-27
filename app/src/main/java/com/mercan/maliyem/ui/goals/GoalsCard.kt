package com.mercan.maliyem.ui.goals

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.GoalBalance
import com.mercan.maliyem.ui.components.CategoryCircle
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import java.text.NumberFormat

@Composable
internal fun GoalsCard(goals: List<GoalBalance>, onOpen: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    SeyirCard(Modifier.animateContentSize(Motion.contentSize()), onClick = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SeyirIcon(R.drawable.ic_seyir_cat_piggy, tint = tint)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.bodyLarge)
                    if (goals.isEmpty()) SubText(stringResource(R.string.goals_empty_summary))
                }
                SeyirIcon(R.drawable.ic_seyir_chevron_right, tint = tint)
            }
            goals.forEach { goal ->
                key(goal.goal.id) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                goal.goal.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            SubText(goalPercent(goal.percent))
                        }
                        GoalProgressBar(goal.percent)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GoalHeading(balance: GoalBalance, progress: GoalProgress) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CategoryCircle(categoryIcon(balance.goal.icon))
        Text(balance.goal.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(goalPercent(progress.percent), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun GoalProgressBar(percent: Int) {
    val fraction by animateFloatAsState(percent / 100f, Motion.enter(), label = "Goal progress")
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth(),
        color = if (percent == 100) LocalMoneyColors.current.onIncomeContainer else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
private fun goalPercent(percent: Int): String =
    NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).format(percent / 100.0)
