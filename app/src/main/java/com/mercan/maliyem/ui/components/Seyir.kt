package com.mercan.maliyem.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.ui.theme.LocalSelectionColors
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val SeyirButtonHeight = 44.dp
private val SeyirActionHeight = 40.dp

/** Kategori adı: kullanıcının verdiği ad önce, yoksa hazır kategorinin çevirisi (ADR-0011). */
@Composable
fun categoryName(key: String?, name: String?): String =
    name ?: DefaultCategories.nameRes(key)?.let { stringResource(it) }.orEmpty()

fun monthTitle(pattern: String, month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(month).replaceFirstChar { it.titlecase(locale) }

/** Küçük kalın başlık (pano .eyebrow: 700 11/15, .02em). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) = Text(
    text, modifier, color = color,
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = .02.em),
)

/** Gri yardımcı metin (pano .sub: 12/17). */
@Composable
fun SubText(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) = Text(
    text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp, fontFeatureSettings = "tnum"),
    textAlign = textAlign,
)

/** Sekme ekranı başlığı (pano .top h2: 28/32 kalın). */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
        Text(
            title, Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        )
        subtitle?.let { SubText(it) }
    }
}

private val SmallTitle @Composable get() =
    MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)

/** Alt ekran başlığı: geri düğmesi ve 22/28 başlık. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        // İkon kenarla hizalansın diye 48 dp düğme 12 dp dışarı taşar (pano: margin −12).
        IconButton(onBack, Modifier.offset(x = (-12).dp), enabled = enabled) {
            SeyirIcon(R.drawable.ic_seyir_chevron_left, description = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, Modifier.offset(x = (-12).dp).semantics { heading() }, style = SmallTitle)
    }
}

/** ‹ Ay › başlığı; gidilecek ay yoksa düğme pasif. */
@Composable
fun MonthHeader(month: YearMonth, locale: Locale, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, subtitle: String? = null) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    // M3'ün pasif içerik saydamlığı (%38).
    fun tintFor(action: (() -> Unit)?) = if (action != null) tint else tint.copy(alpha = .38f)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onPrevious ?: {}, Modifier.offset(x = (-12).dp), enabled = onPrevious != null) {
            SeyirIcon(R.drawable.ic_seyir_chevron_left, description = stringResource(R.string.summary_previous_month), tint = tintFor(onPrevious))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(monthTitle("LLLL yyyy", month, locale), Modifier.semantics { heading() }, style = SmallTitle, textAlign = TextAlign.Center)
            subtitle?.let { SubText(it, textAlign = TextAlign.Center) }
        }
        IconButton(onNext ?: {}, Modifier.offset(x = 12.dp), enabled = onNext != null) {
            SeyirIcon(R.drawable.ic_seyir_chevron_right, description = stringResource(R.string.summary_next_month), tint = tintFor(onNext))
        }
    }
}

/** Pano .seg: yuvarlak hap içinde eşit genişlikte seçenekler. */
@Composable
fun SeyirSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val sel = LocalSelectionColors.current
    // Seçili kapsül seçenekler arasında kayar (ADR-0016).
    val position by animateFloatAsState(selected.toFloat(), Motion.fast(), label = "segment")
    Row(
        modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(24.dp)).padding(Spacing.xs)
            .drawBehind {
                if (selected < 0) return@drawBehind
                val width = size.width / options.size
                val x = width * position
                drawRoundRect(
                    sel.container,
                    topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - x - width else x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                )
            }
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            val content by animateColorAsState(if (on) sel.content else colors.onSurfaceVariant, Motion.fast(), label = "segmentText")
            Box(
                Modifier.weight(1f).heightIn(min = SeyirActionHeight).clip(RoundedCornerShape(20.dp))
                    .seyirTouchTarget(Role.Tab, on, enabled = true) { onSelect(index) }
                    .padding(horizontal = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = content,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Pano .chip: çerçeveli çip; seçilince dolar, renk geçişi 150 ms (pano: .15s). */
@Composable
fun SeyirChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    role: Role = Role.RadioButton,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val sel = LocalSelectionColors.current
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(if (selected) sel.container else Color.Transparent, Motion.fast(), label = "chipBg")
    val border by animateColorAsState(if (selected) sel.container else colors.outline, Motion.fast(), label = "chipBorder")
    val content by animateColorAsState(if (selected) sel.content else colors.onSurfaceVariant, Motion.fast(), label = "chipText")
    Row(
        modifier.heightIn(min = 36.dp).clip(shape).background(background).border(1.dp, border, shape)
            .seyirTouchTarget(role, selected, enabled, onClick)
            .padding(start = if (icon != null) Spacing.md else Spacing.lg, end = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        icon?.let { SeyirIcon(it, size = 18.dp, tint = content) }
        Text(label, color = content, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

// Compose expands clickable and selectable hit bounds to the 48 dp touch minimum without
// affecting measurement. Keep the stock handlers so focus, semantics and nearest-hit routing
// still work when the expanded bounds of adjacent chip rows meet in the gap.
private fun Modifier.seyirTouchTarget(
    role: Role,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = if (role == Role.Button) {
    clickable(enabled = enabled, role = role, onClick = onClick)
} else {
    selectable(selected, enabled = enabled, role = role, onClick = onClick)
}

/** Pano .card: dolgu 16, köşe 20. */
@Composable
fun SeyirCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Spacing.lg),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val action = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .then(action).background(MaterialTheme.colorScheme.surfaceContainer).padding(padding),
        content = content,
    )
}

/** Pano .card içindeki .lb: 600 14/18. */
@Composable
fun CardLabel(text: String) = Text(
    text, Modifier.semantics { heading() },
    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
internal fun SettingSwitch(
    @StringRes title: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    horizontalPadding: Dp = Spacing.lg,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Pano .rw: ikon, etiket ve sağda gri değer; tıklanabilirse sonda ›. */
@Composable
fun SettingRow(
    @DrawableRes icon: Int?,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = Spacing.lg,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier,
        )
            .heightIn(min = 56.dp).padding(horizontal = horizontalPadding, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        icon?.let { SeyirIcon(it, tint = colors.onSurfaceVariant) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { SubText(it) }
        }
        value?.let {
            Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.End)
        }
        if (onClick != null) SeyirIcon(R.drawable.ic_seyir_chevron_right, tint = colors.onSurfaceVariant)
    }
}

// Material3 reserves layout space separately from Compose's touch hit testing. Opt out only
// around compact controls so the drawn surface measures at its intended height.
@Composable
internal fun SeyirCompactControl(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, content = content)
}

@Composable
internal fun SeyirTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    SeyirCompactControl {
        TextButton(onClick, modifier.heightIn(min = SeyirButtonHeight), enabled = enabled, content = content)
    }
}

/** Pano .btn: 44 dp hap; [tonal] ikincil dolgu (.btn.t). */
@Composable
fun SeyirButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    tonal: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    SeyirCompactControl {
        Button(
            onClick, modifier.heightIn(min = SeyirButtonHeight),
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            colors = if (tonal) ButtonDefaults.buttonColors(colors.secondaryContainer, colors.onSecondaryContainer) else ButtonDefaults.buttonColors(),
            contentPadding = PaddingValues(horizontal = Spacing.xl),
        ) {
            ButtonContent(text, icon)
        }
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, @DrawableRes icon: Int?) {
    icon?.let { SeyirIcon(it); Box(Modifier.size(Spacing.sm)) }
    Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
}

enum class ActionTone { Neutral, Danger }

@Composable
fun SeyirActionButton(
    text: String,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    tone: ActionTone = ActionTone.Neutral,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = SeyirActionHeight,
) {
    val colors = MaterialTheme.colorScheme
    val danger = tone == ActionTone.Danger
    SeyirCompactControl {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = minHeight),
            enabled = enabled,
            shape = CircleShape,
            colors = if (danger) ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
                else ButtonDefaults.outlinedButtonColors(),
            border = if (danger && enabled) BorderStroke(1.dp, colors.error)
                else ButtonDefaults.outlinedButtonBorder(enabled),
            contentPadding = PaddingValues(horizontal = Spacing.lg),
        ) {
            icon?.let {
                SeyirIcon(it, size = 18.dp, tint = LocalContentColor.current)
                Box(Modifier.size(Spacing.sm))
            }
            Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal val SeyirFabClearance = 96.dp

@Composable
internal fun SeyirAddFab(text: String, onClick: () -> Unit, listState: LazyListState? = null) {
    val expanded = listState?.canScrollBackward != true
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { SeyirIcon(R.drawable.ic_seyir_plus, description = text.takeUnless { expanded }) },
        text = { Text(text, fontWeight = FontWeight.SemiBold) },
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
internal fun SeyirListScaffold(
    addLabel: String?,
    listState: LazyListState,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (addLabel != null) SeyirAddFab(addLabel, onAdd, listState)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** Kategori dairesi (40 dp, primaryContainer). */
@Composable
fun CategoryCircle(@DrawableRes icon: Int, size: androidx.compose.ui.unit.Dp = 40.dp, iconSize: androidx.compose.ui.unit.Dp = 20.dp, background: Color = MaterialTheme.colorScheme.primaryContainer, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        SeyirIcon(icon, size = iconSize, tint = tint)
    }
}

/** Pano .ctr: dikeyde ortalı boş durum; ikon, büyük başlık, açıklama ve eylemler. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String, modifier: Modifier = Modifier, extra: @Composable ColumnScope.() -> Unit = {}) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val scroll = if (constraints.hasBoundedHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            scroll.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CategoryCircle(icon, size = 56.dp, iconSize = 26.dp, background = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                title, Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.02).em),
                textAlign = TextAlign.Center,
            )
            if (body.isNotEmpty()) {
                Text(
                    body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
                )
            }
            extra()
        }
    }
}

@Composable
internal fun ScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
