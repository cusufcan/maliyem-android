package com.mercan.maliyem.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
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
            CardLabel(stringResource(R.string.trend_title))
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
            onOpenYearlyReport?.let {
                SeyirButton(stringResource(R.string.yearly_report), it, tonal = true)
            }
        }
    }
}
