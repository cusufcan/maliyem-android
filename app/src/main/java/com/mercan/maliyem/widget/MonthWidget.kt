package com.mercan.maliyem.widget

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

private val MinimumSize = DpSize(112.dp, 48.dp)
private val CompactSize = DpSize(112.dp, 68.dp)
private val ExpandedSize = DpSize(144.dp, 112.dp)
private val SeyirWidgetColors = ColorProviders(light = LightColors, dark = DarkColors)
private val IncomeBackground = ColorProvider(LightMoneyColors.incomeContainer, DarkMoneyColors.incomeContainer)
private val IncomeContent = ColorProvider(LightMoneyColors.onIncomeContainer, DarkMoneyColors.onIncomeContainer)

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MonthWidgetEntryPoint {
    fun transactions(): TransactionRepository
    fun calendarDays(): CalendarDays
    fun settings(): AppSettings
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MonthWidget()
}

/** Küçük boyda neti, yeterli alan varsa gelir ve gider toplamlarını da gösterir. */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(MinimumSize, CompactSize, ExpandedSize))
    override val stateDefinition = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dependencies = EntryPointAccessors.fromApplication<MonthWidgetEntryPoint>(context)
        val transactions = dependencies.transactions()
        val appLockEnabled = dependencies.settings().appLockEnabled
        // Glance oturumu açıksa updateAll içerik sağlayıcısını yeniden başlatmaz.
        // Ayı ve Room sorgusunu izleyerek açık oturumda da yeni toplamları alırız.
        val totals = dependencies.calendarDays().days.map(YearMonth::from).distinctUntilChanged()
            .flatMapLatest { month ->
                transactions.observeTrendEntries(month, month).map { rows -> buildTrend(listOf(month), rows).single() }
            }
        provideContent {
            val locked by appLockEnabled.collectAsState()
            val current = if (locked) null else totals.collectAsState(initial = null).value
            val localizedContext = remember(context) { ContextCompat.getContextForLanguage(context) }
            GlanceTheme(colors = SeyirWidgetColors) {
                SeyirMonthWidget(localizedContext, current, LocalSize.current, locked)
            }
        }
    }
}

@Composable
private fun SeyirMonthWidget(context: Context, totals: MonthlyTrend?, size: DpSize, locked: Boolean) {
    val showDetails = !locked && size == ExpandedSize
    val showNetLabel = !locked && size != MinimumSize
    val buttonHeight = if (size != MinimumSize) 26.dp else 22.dp
    val colors = GlanceTheme.colors
    val locale = context.resources.configuration.locales[0]
    fun amount(minor: BigInteger?, signed: Boolean = false) =
        minor?.let { formatMoney(it, locale, signed) } ?: "—"

    val netLabel = context.getString(if (locked) R.string.widget_amounts_hidden else R.string.widget_month_net)
    val net = if (locked) "••••" else amount(totals?.netMinor, signed = true)
    val openApp = if (locked) GlanceModifier else GlanceModifier.clickable(actionStartActivity(mainActivityIntent(context)))
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(20.dp)
            .background(colors.primaryContainer).then(openApp)
            .padding(horizontal = Spacing.sm, vertical = if (showNetLabel) Spacing.xs else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNetLabel) {
            Text(netLabel, style = TextStyle(color = colors.onPrimaryContainer, fontSize = 11.sp), maxLines = 1)
        }
        Text(
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
        Spacer(GlanceModifier.height(if (showNetLabel) Spacing.xs else 2.dp))
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
