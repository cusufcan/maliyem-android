package com.mercan.maliyem.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.accounts.AccountsScreen
import com.mercan.maliyem.ui.add.AddTransactionSheet
import com.mercan.maliyem.ui.breakdown.BreakdownScreen
import com.mercan.maliyem.ui.categories.CategoriesScreen
import com.mercan.maliyem.ui.detail.TransactionDetailScreen
import com.mercan.maliyem.ui.recurring.RecurringRuleScreen
import com.mercan.maliyem.ui.recurring.RecurringScreen
import com.mercan.maliyem.ui.settings.SettingsScreen
import com.mercan.maliyem.ui.summary.SummaryScreen
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.SeyirTab
import com.mercan.maliyem.ui.theme.SeyirTabIndicatorHeight
import com.mercan.maliyem.ui.theme.SeyirTabIndicatorWidth
import com.mercan.maliyem.ui.theme.SeyirTabVerticalPadding
import com.mercan.maliyem.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch

private class TopLevelItem(val route: Any, val labelRes: Int, val icon: Int)

private const val TabTransitionMillis = 150
private val TabBarMinHeight = 60.dp

private val tabs = listOf(
    TopLevelItem(Summary, R.string.nav_summary, R.drawable.ic_seyir_nav_summary),
    TopLevelItem(Transactions, R.string.nav_transactions, R.drawable.ic_seyir_nav_list),
    TopLevelItem(Breakdown, R.string.nav_breakdown, R.drawable.ic_seyir_breakdown),
    TopLevelItem(Settings, R.string.nav_settings, R.drawable.ic_seyir_nav_settings),
)

/** Alt ekranın hangi sekmeye ait sayıldığı: Kategoriler Ayarlar'a, Detay açıldığı sekmeye. */
private fun tabIndexOf(destination: androidx.navigation.NavDestination?): Int =
    tabs.indexOfFirst { destination?.hasRoute(it.route::class) == true }

@Composable
fun MaliyemNavHost(app: AppViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val activeTab = when {
        destination?.hasRoute(Categories::class) == true || destination?.hasRoute(Accounts::class) == true ||
            destination?.hasRoute(Recurring::class) == true -> tabs.lastIndex
        destination?.hasRoute(RecurringRule::class) == true ->
            backStackEntry?.toRoute<RecurringRule>()?.tab?.takeIf { it in tabs.indices } ?: tabs.lastIndex
        destination?.hasRoute(TransactionDetail::class) == true ->
            tabIndexOf(nav.previousBackStackEntry?.destination).takeIf { it >= 0 } ?: 1
        else -> tabIndexOf(destination).coerceAtLeast(0)
    }
    val indicatorPosition by animateFloatAsState(activeTab.toFloat(), tween(TabTransitionMillis), label = "Active tab")

    val hasData by app.hasData.collectAsStateWithLifecycle()
    val monthHasData by app.monthHasData.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ruleSaved = stringResource(R.string.recurring_saved)
    // Ekle/Düzenle alt sayfası ve snackbar uygulama düzeyinde; her sekme aynısını kullanır (ADR-0010).
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editTransfer by rememberSaveable { mutableStateOf(false) }
    val undoActions by app.undoActions.collectAsStateWithLifecycle()
    val pendingUndo = undoActions.firstOrNull()
    val addedEntry = (pendingUndo as? UndoAction.Added)?.entry
    val highlightId by rememberUpdatedState(addedEntry?.takeIf { it.type != null }?.id)
    val highlightTransferId by rememberUpdatedState(addedEntry?.takeIf { it.type == null }?.id)
    val undoFailed by app.undoFailed.collectAsStateWithLifecycle()
    val savedExpense = stringResource(R.string.add_saved_expense)
    val savedIncome = stringResource(R.string.add_saved_income)
    val savedTransfer = stringResource(R.string.transfer_saved)
    val deletedLabel = stringResource(R.string.deleted)
    val undoLabel = stringResource(R.string.add_undo)
    val undoError = stringResource(R.string.undo_failed)
    LaunchedEffect(pendingUndo) {
        val message = when (pendingUndo) {
            null -> return@LaunchedEffect
            is UndoAction.Added -> when (pendingUndo.entry.type) {
                TransactionType.INCOME -> savedIncome
                TransactionType.EXPENSE -> savedExpense
                null -> savedTransfer
            }
            is UndoAction.Deleted, is UndoAction.DeletedTransfer -> deletedLabel
        }
        val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        app.finishUndo(pendingUndo, perform = result == SnackbarResult.ActionPerformed)
    }
    LaunchedEffect(undoFailed) {
        if (undoFailed) {
            snackbar.showSnackbar(undoError)
            app.acknowledgeUndoFailure()
        }
    }

    val showFab = when {
        destination?.hasRoute(Transactions::class) == true -> true
        destination?.hasRoute(Summary::class) == true -> hasData == true && monthHasData
        else -> false
    }

    fun openTab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun openSheet(id: Long? = null, transfer: Boolean = false) {
        editId = id
        editTransfer = transfer
        sheetOpen = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = { openSheet() },
                    icon = { SeyirIcon(R.drawable.ic_seyir_plus) },
                    text = { Text(stringResource(R.string.add_open), fontWeight = FontWeight.SemiBold) },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        bottomBar = {
            val colors = MaterialTheme.colorScheme
            Column(Modifier.background(colors.surfaceContainerHigh).navigationBarsPadding()) {
                HorizontalDivider(color = colors.outlineVariant)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tabWidth = maxWidth / tabs.size
                    Box(
                        Modifier.offset {
                            IntOffset(
                                (tabWidth * indicatorPosition + (tabWidth - SeyirTabIndicatorWidth) / 2).roundToPx(),
                                SeyirTabVerticalPadding.roundToPx(),
                            )
                        }.width(SeyirTabIndicatorWidth).height(SeyirTabIndicatorHeight)
                            .background(colors.primaryContainer, CircleShape),
                    )
                    Row(Modifier.fillMaxWidth().heightIn(min = TabBarMinHeight).selectableGroup()) {
                        tabs.forEachIndexed { index, tab ->
                            SeyirTab(
                                label = stringResource(tab.labelRes),
                                glyph = tab.icon,
                                selected = index == activeTab,
                                onClick = { openTab(tab.route) },
                                modifier = Modifier.weight(1f).heightIn(min = TabBarMinHeight),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = Summary,
            modifier = Modifier.fillMaxSize().padding(padding),
            enterTransition = { fadeIn(tween(TabTransitionMillis)) },
            exitTransition = { fadeOut(tween(TabTransitionMillis)) },
        ) {
            composable<Summary> {
                SummaryScreen(
                    highlightId = highlightId,
                    firstLaunch = hasData == false,
                    onSeeAll = { openTab(Transactions) },
                    onOpen = { nav.navigate(TransactionDetail(it)) },
                    onAdd = { openSheet() },
                )
            }
            composable<Transactions> {
                TransactionsScreen(
                    highlightId = highlightId,
                    onOpen = { nav.navigate(TransactionDetail(it)) },
                    onOpenTransfer = { nav.navigate(TransactionDetail(it, transfer = true)) },
                    highlightTransferId = highlightTransferId,
                )
            }
            composable<Breakdown> { BreakdownScreen() }
            composable<Settings> {
                SettingsScreen(
                    onCategories = { nav.navigate(Categories) },
                    onAccounts = { nav.navigate(Accounts) },
                    onRecurring = { nav.navigate(Recurring) },
                    snackbar = snackbar,
                    beforeRestore = app::clearUndo,
                )
            }
            composable<TransactionDetail> { entry ->
                val route = entry.toRoute<TransactionDetail>()
                TransactionDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { openSheet(route.id, route.transfer) },
                    onOpenRule = { nav.navigate(RecurringRule(it, activeTab)) },
                    onDeleted = { action ->
                        if (nav.currentBackStackEntry == entry) nav.popBackStack()
                        app.offerUndo(action)
                    },
                )
            }
            composable<Recurring> {
                RecurringScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(RecurringRule(it)) })
            }
            composable<RecurringRule> { entry ->
                RecurringRuleScreen(entry.toRoute<RecurringRule>().id, onBack = { nav.popBackStack() })
            }
            composable<Accounts> { AccountsScreen(onBack = { nav.popBackStack() }) }
            composable<Categories> { CategoriesScreen(onBack = { nav.popBackStack() }) }
        }
    }

    if (sheetOpen) {
        AddTransactionSheet(
            onDismiss = { sheetOpen = false },
            editId = editId,
            editTransfer = editTransfer,
            onSaved = { entry ->
                sheetOpen = false
                if (entry.recurring) scope.launch { snackbar.showSnackbar(ruleSaved) }
                else if (!entry.edited) app.offerUndo(UndoAction.Added(entry))
            },
        )
    }
}
