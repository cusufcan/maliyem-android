package com.mercan.maliyem.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.trend.MonthlyTrend
import com.mercan.maliyem.ui.trend.SeyirMonthlyBars
import com.mercan.maliyem.ui.trend.percentChange
import com.mercan.maliyem.util.formatMoney
import java.time.YearMonth
import java.util.Locale

@Composable
internal fun SeyirTrendCard(
    months: List<MonthlyTrend>,
    locale: Locale,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenYearlyReport: (() -> Unit)?,
) {
    val current = months.lastOrNull() ?: return
    val previous = months.getOrNull(months.lastIndex - 1)

    SeyirCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(Modifier.weight(1f)) { CardLabel(stringResource(R.string.trend_title)) }
                if (onOpenYearlyReport != null) {
                    val primary = MaterialTheme.colorScheme.primary
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Row(
                            Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.Button, onClick = onOpenYearlyReport),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                stringResource(R.string.yearly_report),
                                Modifier.weight(1f, fill = false),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = primary,
                            )
                            SeyirIcon(R.drawable.ic_seyir_chevron_right, size = 16.dp, tint = primary)
                        }
                    }
                }
            }
            SeyirMonthlyBars(months, locale, current.month, onSelectMonth)
            val net = formatMoney(current.netMinor, locale, signed = true)
            if (previous == null) {
                SubText(stringResource(R.string.trend_net, net))
            } else {
                SubText(stringResource(R.string.trend_net_comparison, net, formatMoney(previous.netMinor, locale, signed = true)))
            }
            val change = previous?.let { percentChange(current.expenseMinor, it.expenseMinor) }
            SubText(
                when (change?.signum()) {
                    null -> stringResource(R.string.trend_expense_unavailable, stringResource(R.string.trend_unavailable))
                    0 -> stringResource(R.string.trend_expense_steady)
                    1 -> stringResource(R.string.trend_expense_up, change.toString())
                    else -> stringResource(R.string.trend_expense_down, change.abs().toString())
                },
            )
        }
    }
}
