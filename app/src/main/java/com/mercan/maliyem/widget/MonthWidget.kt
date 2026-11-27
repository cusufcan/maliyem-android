package com.mercan.maliyem.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.Emittable
import androidx.glance.EmittableWithChildren
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.AppWidgetSession
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.SemanticsModifier
import androidx.glance.semantics.SemanticsPropertyKey
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.TransactionRepository
import com.mercan.maliyem.quickentry.mainActivityIntent
import com.mercan.maliyem.ui.CalendarDays
import com.mercan.maliyem.ui.settings.AppSettings
import com.mercan.maliyem.ui.theme.DarkColors
import com.mercan.maliyem.ui.theme.DarkMoneyColors
import com.mercan.maliyem.ui.theme.LightColors
import com.mercan.maliyem.ui.theme.LightMoneyColors
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.trend.MonthlyTrend
import com.mercan.maliyem.ui.trend.buildTrend
import com.mercan.maliyem.util.formatMoney
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.math.BigInteger
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val MinimumSize = DpSize(112.dp, 48.dp)
private val CompactSize = DpSize(112.dp, 100.dp)
private val ExpandedSize = DpSize(144.dp, 150.dp)
private val SeyirWidgetColors = ColorProviders(light = LightColors, dark = DarkColors)
private val IncomeBackground = ColorProvider(LightMoneyColors.incomeContainer, DarkMoneyColors.incomeContainer)
private val IncomeContent = ColorProvider(LightMoneyColors.onIncomeContainer, DarkMoneyColors.onIncomeContainer)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MonthWidgetEntryPoint {
    fun updater(): GlanceWidgetUpdater
    fun transactions(): TransactionRepository
    fun calendarDays(): CalendarDays
    fun settings(): AppSettings
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MonthWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val stale = widgetsWithStaleMonth(context)
            if (stale.isNotEmpty()) onUpdate(context, AppWidgetManager.getInstance(context), stale)
        }
    }
}

/** Küçük boyda neti, yeterli alan varsa gelir ve gider toplamlarını da gösterir. */
class MonthWidget(private val maskOnly: Boolean = false) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(MinimumSize, CompactSize, ExpandedSize))
    override val stateDefinition = null

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        storeWidgetMonth(context, GlanceAppWidgetManager(context).getAppWidgetId(glanceId), null)
    }

    // Glance 1.2's session hook is the last point before it sends RemoteViews to the launcher.
    @SuppressLint("RestrictedApi")
    override fun createAppWidgetSession(context: Context, id: AppWidgetId, options: Bundle?): AppWidgetSession =
        MonthWidgetSession(this, id, options)

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dependencies = EntryPointAccessors.fromApplication<MonthWidgetEntryPoint>(context)
        val transactions = dependencies.transactions()
        val appLockEnabled = dependencies.settings().appLockEnabled
        val initialMonth = YearMonth.now()
        val initialLocked = maskOnly || appLockEnabled.value
        fun observeTotals(month: YearMonth) = transactions.observeTrendEntries(month, month)
            .map { rows -> buildTrend(listOf(month), rows).single() }

        val initial = if (initialLocked) null else observeTotals(initialMonth).first()
        // Open sessions also observe the lock and discard their cached totals when it changes.
        val totals = combine(appLockEnabled, dependencies.calendarDays().days.map(YearMonth::from).distinctUntilChanged(), ::Pair)
            .flatMapLatest { (locked, month) -> if (locked) flowOf(null) else observeTotals(month) }
        provideContent {
            val lockState by appLockEnabled.collectAsState()
            val locked = maskOnly || lockState
            val current = if (locked) null else totals.collectAsState(initial = initial).value
            val localizedContext = remember(context) { ContextCompat.getContextForLanguage(context) }
            GlanceTheme(colors = SeyirWidgetColors) {
                SeyirMonthWidget(localizedContext, current, LocalSize.current, locked)
            }
        }
    }
}

private data class WidgetFrame(val locked: Boolean, val month: YearMonth)
private val Frame = SemanticsPropertyKey<WidgetFrame>("WidgetFrame")

@SuppressLint("RestrictedApi")
private class MonthWidgetSession(
    private val widget: MonthWidget,
    private val widgetId: AppWidgetId,
    options: Bundle?,
) : AppWidgetSession(widget, widgetId, options) {
    override suspend fun processEmittableTree(context: Context, root: EmittableWithChildren): Boolean {
        val frames = root.widgetFrames().distinct().toList()
        // Responsive layouts are composed separately. Reject a tree that crosses a transition.
        val frame = frames.singleOrNull() ?: return false
        val updater = EntryPointAccessors.fromApplication<MonthWidgetEntryPoint>(context).updater()
        return updater.publishIfCurrent(frame.locked) {
            super.processEmittableTree(context, root).also { published ->
                if (published) storeWidgetMonth(context, widgetId.appWidgetId, frame.month)
            }
        }
    }

    // There are no Glance lambda actions to replay. Recreated sessions read current launcher options.
    override suspend fun recreateWithEvents(events: List<Any>): AppWidgetSession =
        MonthWidgetSession(widget, widgetId, options = null)

    private fun Emittable.widgetFrames(): Sequence<WidgetFrame> = sequence {
        val frame = modifier.foldIn<WidgetFrame?>(null) { value, modifier ->
            (modifier as? SemanticsModifier)?.configuration?.getOrNull(Frame) ?: value
        }
        if (frame != null) yield(frame)
        (this@widgetFrames as? EmittableWithChildren)?.children?.forEach { yieldAll(it.widgetFrames()) }
    }
}

@Composable
private fun SeyirMonthWidget(context: Context, totals: MonthlyTrend?, size: DpSize, locked: Boolean) {
    val showDetails = !locked && size == ExpandedSize
    val showNetLabel = !locked && size.height >= CompactSize.height
    val buttonHeight = 48.dp
    val showTotals = size.height >= CompactSize.height
    val colors = GlanceTheme.colors
    val locale = context.resources.configuration.locales[0]
    fun amount(minor: BigInteger?, signed: Boolean = false) =
        minor?.let { formatMoney(it, locale, signed) } ?: "—"

    val netLabel = context.getString(if (locked) R.string.widget_amounts_hidden else R.string.widget_month_net)
    val net = if (locked) "••••" else amount(totals?.netMinor, signed = true)
    val openApp = if (locked || !showTotals) GlanceModifier else GlanceModifier.clickable(actionStartActivity(mainActivityIntent(context)))
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(20.dp)
            .background(colors.primaryContainer).then(openApp)
            .semantics { this[Frame] = WidgetFrame(locked, totals?.month ?: YearMonth.now()) }
            .padding(horizontal = Spacing.xs, vertical = if (showTotals) Spacing.xs else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNetLabel) {
            Text(netLabel, style = TextStyle(color = colors.onPrimaryContainer, fontSize = 11.sp), maxLines = 1)
        }
        if (showTotals) Text(
            net,
            modifier = if (showNetLabel) GlanceModifier else GlanceModifier.semantics {
                contentDescription = if (locked) netLabel else "$netLabel $net"
            },
            style = TextStyle(color = colors.onPrimaryContainer, fontSize = if (showDetails) 22.sp else 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (showDetails) {
            Spacer(GlanceModifier.height(Spacing.xs))
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    SeyirWidgetFigure(context.getString(R.string.summary_expense), amount(totals?.expenseMinor))
                }
                Spacer(GlanceModifier.width(Spacing.xs))
                Column(GlanceModifier.defaultWeight()) {
                    SeyirWidgetFigure(context.getString(R.string.summary_income), amount(totals?.incomeMinor))
                }
            }
        }
        if (showTotals) Spacer(GlanceModifier.height(Spacing.xs))
        Row(GlanceModifier.fillMaxWidth()) {
            SeyirWidgetButton(
                context, R.string.widget_add_expense, colors.surface, colors.onSurface,
                actionStartActivity(mainActivityIntent(context, TransactionType.EXPENSE)), GlanceModifier.defaultWeight().height(buttonHeight),
            )
            Spacer(GlanceModifier.width(Spacing.xs))
            SeyirWidgetButton(
                context, R.string.widget_add_income, IncomeBackground, IncomeContent,
                actionStartActivity(mainActivityIntent(context, TransactionType.INCOME)), GlanceModifier.defaultWeight().height(buttonHeight),
            )
        }
    }
}

@Composable
private fun SeyirWidgetFigure(label: String, value: String) {
    val color = GlanceTheme.colors.onPrimaryContainer
    Text(label, style = TextStyle(color = color, fontSize = 11.sp), maxLines = 1)
    Text(value, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}

@Composable
private fun SeyirWidgetButton(
    context: Context,
    @StringRes label: Int,
    background: ColorProvider,
    content: ColorProvider,
    action: Action,
    modifier: GlanceModifier,
) {
    Box(
        modifier.cornerRadius(12.dp).background(background).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(context.getString(label), style = TextStyle(color = content, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}
