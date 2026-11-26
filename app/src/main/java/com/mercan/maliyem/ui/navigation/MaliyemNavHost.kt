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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.add.AddTransactionSheet
import com.mercan.maliyem.ui.breakdown.BreakdownScreen
import com.mercan.maliyem.ui.categories.CategoriesScreen
import com.mercan.maliyem.ui.detail.TransactionDetailScreen
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
    TopLevelItem(Summary, R.string.nav_summary, R.drawable.ic_seyir_home),
    TopLevelItem(Transactions, R.string.nav_transactions, R.drawable.ic_seyir_history),
    TopLevelItem(Breakdown, R.string.nav_breakdown, R.drawable.ic_seyir_breakdown),
    TopLevelItem(Settings, R.string.nav_settings, R.drawable.ic_seyir_settings),
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
        destination?.hasRoute(Categories::class) == true -> tabs.lastIndex
        destination?.hasRoute(TransactionDetail::class) == true ->
            tabIndexOf(nav.previousBackStackEntry?.destination).takeIf { it >= 0 } ?: 1
        else -> tabIndexOf(destination).coerceAtLeast(0)
    }
    val indicatorPosition by animateFloatAsState(activeTab.toFloat(), tween(TabTransitionMillis), label = "Active tab")

    val hasData by app.hasData.collectAsStateWithLifecycle()
    val monthHasData by app.monthHasData.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Ekle/Düzenle alt sayfası ve snackbar uygulama düzeyinde; her sekme aynısını kullanır (ADR-0010).
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var highlightId by rememberSaveable { mutableStateOf<Long?>(null) }
    val savedExpense = stringResource(R.string.add_saved_expense)
    val savedIncome = stringResource(R.string.add_saved_income)
    val deletedLabel = stringResource(R.string.deleted)
    val undoLabel = stringResource(R.string.add_undo)

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

    fun openSheet(id: Long? = null) {
        editId = id
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
                TransactionsScreen(highlightId = highlightId, onOpen = { nav.navigate(TransactionDetail(it)) })
            }
            composable<Breakdown> { BreakdownScreen() }
            composable<Settings> { SettingsScreen(onCategories = { nav.navigate(Categories) }) }
            composable<TransactionDetail> {
                TransactionDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { openSheet(it) },
                    onDeleted = { entity ->
                        nav.popBackStack()
                        scope.launch {
                            val result = snackbar.showSnackbar(deletedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                            if (result == SnackbarResult.ActionPerformed) app.restore(entity)
                        }
                    },
                )
            }
            composable<Categories> { CategoriesScreen(onBack = { nav.popBackStack() }) }
        }
    }

    if (sheetOpen) {
        AddTransactionSheet(
            onDismiss = { sheetOpen = false },
            editId = editId,
            onSaved = { entry ->
                sheetOpen = false
                if (entry.edited) return@AddTransactionSheet
                app.showMonth(entry.date)
                highlightId = entry.id
                scope.launch {
                    val message = if (entry.type == TransactionType.INCOME) savedIncome else savedExpense
                    val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) app.removeAdded(entry.id)
                    highlightId = null
                }
            },
        )
    }
}
