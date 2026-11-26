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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.mercan.maliyem.ui.breakdown.BreakdownScreen
import com.mercan.maliyem.ui.settings.SettingsScreen
import com.mercan.maliyem.ui.summary.SummaryScreen
import com.mercan.maliyem.ui.theme.SeyirTab
import com.mercan.maliyem.ui.theme.SeyirTabIndicatorHeight
import com.mercan.maliyem.ui.theme.SeyirTabIndicatorWidth
import com.mercan.maliyem.ui.theme.SeyirTabVerticalPadding
import com.mercan.maliyem.ui.transactions.TransactionsScreen

private class TopLevelItem(val route: Any, val labelRes: Int, val icon: Int)

private const val TabTransitionMillis = 150
private val TabBarMinHeight = 60.dp

private val tabs = listOf(
    TopLevelItem(Summary, R.string.nav_summary, R.drawable.ic_seyir_home),
    TopLevelItem(Transactions, R.string.nav_transactions, R.drawable.ic_seyir_history),
    TopLevelItem(Breakdown, R.string.nav_breakdown, R.drawable.ic_seyir_breakdown),
    TopLevelItem(Settings, R.string.nav_settings, R.drawable.ic_seyir_settings),
)

@Composable
fun MaliyemNavHost() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val activeTab = tabs.indexOfFirst { destination?.hasRoute(it.route::class) == true }.coerceAtLeast(0)
    val indicatorPosition by animateFloatAsState(activeTab.toFloat(), tween(TabTransitionMillis), label = "Active tab")

    fun openTab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
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
            composable<Summary> { SummaryScreen(onSeeAll = { openTab(Transactions) }) }
            composable<Transactions> { TransactionsScreen() }
            composable<Breakdown> { BreakdownScreen() }
            composable<Settings> { SettingsScreen() }
        }
    }
}
