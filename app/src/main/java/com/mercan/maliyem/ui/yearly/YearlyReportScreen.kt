package com.mercan.maliyem.ui.yearly

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.repository.YearlyReport
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.CategoryCircle
import com.mercan.maliyem.ui.components.Eyebrow
import com.mercan.maliyem.ui.components.ScreenLoading
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.components.monthTitle
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.ui.trend.MonthlyTrend
import com.mercan.maliyem.ui.trend.SeyirMonthlyBars
import com.mercan.maliyem.util.formatMoney
import java.util.Locale

@Composable
fun YearlyReportScreen(
    onBack: () -> Unit,
    showMessage: suspend (String) -> Unit,
    viewModel: YearlyReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    var pendingExportYear by rememberSaveable { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val year = pendingExportYear
        pendingExportYear = null
        if (uri != null && year != null) viewModel.export(uri, resources, year)
    }
    LaunchedEffect(viewModel, resources) {
        viewModel.exportMessages.collect { showMessage(resources.getString(it)) }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
    ) {
        BackHeader(stringResource(R.string.yearly_report), onBack)
        when (val current = state) {
            ReportState.Loading -> ScreenLoading()
            ReportState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SubText(stringResource(R.string.yearly_load_failed))
                SeyirButton(stringResource(R.string.yearly_retry), viewModel::retry, tonal = true)
            }
            is ReportState.Ready -> {
                val report = current.report
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    YearSelector(report.year, report.years, viewModel::selectYear)
                    ReportTotals(report, locale)
                    SeyirCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            CardLabel(stringResource(R.string.yearly_chart))
                            key(report.year) {
                                val months = remember(report.months) {
                                    report.months.map { MonthlyTrend(it.month, it.incomeMinor, it.expenseMinor) }
                                }
                                SeyirMonthlyBars(months, locale)
                            }
                        }
                    }
                    SeyirCard {
                        CardLabel(stringResource(R.string.yearly_highest_expense))
                        val peak = report.highestExpenseMonth
                        if (peak != null) {
                            SettingRow(
                                icon = R.drawable.ic_seyir_calendar,
                                label = monthTitle("LLLL", peak.month, locale),
                                value = formatMoney(peak.expenseMinor, locale),
                                horizontalPadding = 0.dp,
                            )
                        } else {
                            SubText(stringResource(R.string.yearly_no_expenses), Modifier.padding(top = Spacing.sm))
                        }
                    }
                    if (report.topCategories.isNotEmpty()) ExpenseCategories(report, locale)
                    SeyirButton(
                        if (exporting) stringResource(R.string.yearly_exporting) else stringResource(R.string.settings_csv),
                        onClick = {
                            pendingExportYear = report.year
                            picker.launch("maliyem-${report.year}.csv")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !exporting && pendingExportYear == null,
                    )
                }
            }
        }
    }
}

@Composable
private fun YearSelector(year: Int, years: List<Int>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SeyirButton(
            stringResource(R.string.yearly_select_year, year.toString()),
            onClick = { expanded = true },
            icon = R.drawable.ic_seyir_calendar,
            tonal = true,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            choice.toString(),
                            fontWeight = if (choice == year) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    },
                    modifier = Modifier.semantics { selected = choice == year },
                )
            }
        }
    }
}

@Composable
private fun ReportTotals(report: YearlyReport, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(colors.primaryContainer, MaterialTheme.shapes.extraLarge).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Eyebrow(stringResource(R.string.yearly_net), color = colors.onPrimaryContainer)
        Text(
            formatMoney(report.netMinor, locale, signed = true),
            color = colors.onPrimaryContainer,
            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold),
        )
    }
    SeyirCard {
        SettingRow(
            null,
            stringResource(R.string.yearly_total_income),
            value = formatMoney(report.incomeMinor, locale),
            horizontalPadding = 0.dp,
        )
        HorizontalDivider(color = colors.outlineVariant)
        SettingRow(
            null,
            stringResource(R.string.yearly_total_expense),
            value = formatMoney(report.expenseMinor, locale),
            horizontalPadding = 0.dp,
        )
        HorizontalDivider(color = colors.outlineVariant)
        SettingRow(
            null,
            stringResource(R.string.yearly_average_expense),
            value = formatMoney(report.averageExpenseMinor, locale),
            horizontalPadding = 0.dp,
        )
    }
}

@Composable
private fun ExpenseCategories(report: YearlyReport, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    SeyirCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CardLabel(stringResource(R.string.yearly_top_categories))
            report.topCategories.forEach { category ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CategoryCircle(categoryIcon(category.icon))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(categoryName(category.key, category.name), style = MaterialTheme.typography.bodyMedium)
                        SubText(formatMoney(category.amountMinor, locale))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small).background(colors.surfaceContainerHigh)) {
                            Box(
                                Modifier.fillMaxWidth(category.sharePercent / 100f).height(6.dp)
                                    .clip(MaterialTheme.shapes.small).background(colors.primary),
                            )
                        }
                    }
                    SubText(stringResource(R.string.percent, category.sharePercent))
                }
            }
        }
    }
}
