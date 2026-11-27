package com.mercan.maliyem.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.GoalBalance
import com.mercan.maliyem.data.local.GoalEntryEntity
import com.mercan.maliyem.ui.components.ActionTone
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.CategoryCircle
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.ScreenLoading
import com.mercan.maliyem.ui.components.SeyirActionButton
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirFabClearance
import com.mercan.maliyem.ui.components.SeyirListScaffold
import com.mercan.maliyem.ui.components.SeyirSegmented
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.seyirAnimateItem
import com.mercan.maliyem.util.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GoalsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val write by viewModel.write.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(write.saved) {
        if (write.saved) {
            creating = false
            showArchived = false
            viewModel.resetWrite()
        }
    }
    val listState = rememberLazyListState()
    SeyirListScaffold(
        addLabel = if (showArchived) null else stringResource(R.string.goal_new),
        listState = listState,
        onAdd = { creating = true },
    ) {
        Column(Modifier.fillMaxSize().padding(top = Spacing.sm)) {
            Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                BackHeader(stringResource(R.string.goals_title), onBack)
                SeyirSegmented(
                    listOf(stringResource(R.string.goals_active), stringResource(R.string.account_archived)),
                    if (showArchived) 1 else 0,
                    { showArchived = it == 1 },
                )
            }
            val shown = balances?.filter { it.goal.archived == showArchived }
            if (shown == null) {
                ScreenLoading()
            } else {
                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = Spacing.lg, top = Spacing.md, end = Spacing.lg,
                            bottom = if (showArchived) Spacing.md else SeyirFabClearance,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(shown, key = { it.goal.id }) { balance ->
                            GoalStatusCard(balance, today, seyirAnimateItem(), onClick = { onOpen(balance.goal.id) })
                        }
                    }
                    if (shown.isEmpty()) {
                        EmptyState(
                            R.drawable.ic_seyir_cat_piggy,
                            stringResource(if (showArchived) R.string.goals_no_archived else R.string.goals_no_active),
                            stringResource(if (showArchived) R.string.goals_no_archived_body else R.string.goals_no_active_body),
                            Modifier.fillMaxSize().padding(
                                start = Spacing.lg, end = Spacing.lg,
                                bottom = if (showArchived) 0.dp else SeyirFabClearance,
                            ),
                        )
                    }
                }
            }
        }
    }
    if (creating) {
        EditGoalDialog(null, today, write, onDismiss = {
            creating = false
            viewModel.resetWrite()
        }, onSave = viewModel::save)
    }
}

private enum class GoalDialog { EDIT, DEPOSIT, WITHDRAW, DELETE }
private val GoalPagePadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)

@Composable
fun GoalDetailScreen(onBack: () -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val loaded by checkNotNull(viewModel.detail).collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val write by viewModel.write.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<GoalDialog?>(null) }
    LaunchedEffect(write.saved) {
        if (write.saved) {
            val deleted = dialog == GoalDialog.DELETE
            dialog = null
            viewModel.resetWrite()
            if (deleted) onBack()
        }
    }
    fun open(mode: GoalDialog) {
        viewModel.resetWrite()
        dialog = mode
    }
    fun dismiss() {
        viewModel.resetWrite()
        dialog = null
    }

    val detail = loaded?.detail
    Column(Modifier.fillMaxSize().padding(top = Spacing.sm)) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            BackHeader(stringResource(R.string.goal_detail), onBack)
        }
        when {
            loaded == null -> ScreenLoading()
            detail == null -> SubText(stringResource(R.string.goal_unavailable), Modifier.padding(Spacing.lg))
            else -> {
                val goal = detail.balance.goal
                LazyColumn(contentPadding = GoalPagePadding, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    item(key = "goal-status") { GoalStatusCard(detail.balance, today, seyirAnimateItem()) }
                    item(key = "goal-actions") {
                        Column(seyirAnimateItem(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (goal.archived) {
                                SubText(stringResource(R.string.goal_archived_hint))
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    SeyirButton(stringResource(R.string.goal_deposit), { open(GoalDialog.DEPOSIT) },
                                        enabled = !write.busy, icon = R.drawable.ic_seyir_plus)
                                    SeyirActionButton(stringResource(R.string.goal_withdraw), { open(GoalDialog.WITHDRAW) },
                                        icon = R.drawable.ic_seyir_arrow_out, enabled = !write.busy && detail.balance.savedMinor > 0)
                                }
                                SubText(stringResource(R.string.goal_balance_hint))
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                SeyirActionButton(
                                    stringResource(R.string.detail_edit), { open(GoalDialog.EDIT) },
                                    icon = R.drawable.ic_seyir_edit, enabled = !write.busy,
                                )
                                SeyirActionButton(
                                    stringResource(if (goal.archived) R.string.account_unarchive else R.string.account_archive),
                                    { viewModel.archive(!goal.archived) },
                                    icon = if (goal.archived) R.drawable.ic_seyir_arrow_out else R.drawable.ic_seyir_archive,
                                    enabled = !write.busy,
                                )
                                SeyirActionButton(
                                    stringResource(R.string.detail_delete), { open(GoalDialog.DELETE) },
                                    icon = R.drawable.ic_seyir_trash, tone = ActionTone.Danger, enabled = !write.busy,
                                )
                            }
                            if (dialog == null) GoalWriteError(write.error)
                        }
                    }
                    item(key = "goal-history") {
                        Column(seyirAnimateItem()) { CardLabel(stringResource(R.string.goal_history)) }
                    }
                    if (detail.entries.isEmpty()) {
                        item(key = "goal-history-empty") {
                            EmptyState(
                                R.drawable.ic_seyir_note, stringResource(R.string.goal_history_empty), "",
                                seyirAnimateItem().fillParentMaxHeight(.35f).padding(vertical = Spacing.md),
                            )
                        }
                    }
                    items(
                        detail.entries.sortedWith(compareByDescending<GoalEntryEntity> { it.date }.thenByDescending { it.id }),
                        key = { it.id },
                    ) { entry -> GoalEntryRow(entry, seyirAnimateItem()) }
                }

                when (dialog) {
                    GoalDialog.EDIT -> EditGoalDialog(goal, today, write, ::dismiss, viewModel::save)
                    GoalDialog.DEPOSIT, GoalDialog.WITHDRAW ->
                        GoalEntryDialog(dialog == GoalDialog.WITHDRAW, today, write, ::dismiss, viewModel::addEntry)
                    GoalDialog.DELETE -> DeleteGoalDialog(goal.name, write, ::dismiss, viewModel::delete)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun GoalStatusCard(balance: GoalBalance, today: LocalDate, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val locale = LocalConfiguration.current.locales[0]
    val goal = balance.goal
    val progress = balance.progress(today)
    SeyirCard(modifier, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GoalHeading(balance, progress)
            GoalProgressBar(progress.percent)
            Text(
                stringResource(R.string.goal_saved_of, formatMoney(balance.savedMinor, locale), formatMoney(goal.targetMinor, locale)),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
            SubText(stringResource(R.string.goal_remaining, formatMoney(progress.remainingMinor, locale)))
            goal.deadline?.let { SubText(stringResource(R.string.goal_deadline_value, goalDate(it))) }
            progress.monthlyMinor?.let { SubText(stringResource(R.string.goal_monthly, formatMoney(it, locale))) }
            if (progress.overdue) SubText(stringResource(R.string.goal_overdue))
            val moneyColors = LocalMoneyColors.current
            AnimatedVisibility(progress.complete, enter = Motion.expand(), exit = Motion.collapse()) {
                Text(
                    stringResource(R.string.goal_complete),
                    color = moneyColors.onIncomeContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun GoalEntryRow(entry: GoalEntryEntity, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val deposit = entry.amountMinor > 0
    SeyirCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CategoryCircle(if (deposit) R.drawable.ic_seyir_arrow_in else R.drawable.ic_seyir_arrow_out)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(if (deposit) R.string.goal_deposit_entry else R.string.goal_withdraw_entry),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    formatMoney(entry.amountMinor, locale, signed = true),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                )
                SubText(goalDate(entry.date))
                entry.note?.let { SubText(it) }
            }
        }
    }
}

@Composable
internal fun goalDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]).format(date)
