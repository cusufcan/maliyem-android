package com.mercan.maliyem.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        // İkon kenarla hizalansın diye 48 dp düğme 12 dp dışarı taşar (pano: margin −12).
        IconButton(onBack, Modifier.offset(x = (-12).dp)) {
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
        modifier.fillMaxWidth().background(colors.surfaceContainerHigh, RoundedCornerShape(24.dp)).padding(4.dp)
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
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp))
                    .selectable(on, role = Role.Tab) { onSelect(index) }
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

/** Pano .chips.f: çerçeveli süzgeç çipleri; seçili olan dolu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val sel = LocalSelectionColors.current
    val shape = RoundedCornerShape(12.dp)
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEachIndexed { index, label ->
            SeyirChip(label, index == selected, { onSelect(index) }, role = Role.RadioButton)
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
) {
    val colors = MaterialTheme.colorScheme
    val sel = LocalSelectionColors.current
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(if (selected) sel.container else Color.Transparent, Motion.fast(), label = "chipBg")
    val border by animateColorAsState(if (selected) sel.container else colors.outlineVariant, Motion.fast(), label = "chipBorder")
    val content by animateColorAsState(if (selected) sel.content else colors.onSurfaceVariant, Motion.fast(), label = "chipText")
    val action = if (role == Role.Button) Modifier.clickable(role = role, onClick = onClick)
        else Modifier.selectable(selected, role = role, onClick = onClick)
    Row(
        modifier.heightIn(min = 48.dp).clip(shape).background(background).border(1.dp, border, shape)
            .then(action)
            .padding(start = if (icon != null) Spacing.md else Spacing.lg, end = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { SeyirIcon(it, size = 18.dp, tint = content) }
        Text(label, color = content, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Pano .card: dolgu 16, köşe 20. */
@Composable
fun SeyirCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(Spacing.lg), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(padding),
        content = content,
    )
}

/** Pano .card içindeki .lb: 600 14/18. */
@Composable
fun CardLabel(text: String) = Text(
    text, Modifier.semantics { heading() },
    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
)

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
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
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

/** Pano .btn: 48 dp hap; [tonal] ikincil dolgu (.btn.t). */
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
    Button(
        onClick, modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = if (tonal) ButtonDefaults.buttonColors(colors.secondaryContainer, colors.onSecondaryContainer) else ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = Spacing.xl),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, @DrawableRes icon: Int?) {
    icon?.let { SeyirIcon(it); Box(Modifier.size(Spacing.sm)) }
    Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
}

/** Kategori dairesi (40 dp, secondaryContainer). */
@Composable
fun CategoryCircle(@DrawableRes icon: Int, size: androidx.compose.ui.unit.Dp = 40.dp, iconSize: androidx.compose.ui.unit.Dp = 20.dp, background: Color = MaterialTheme.colorScheme.secondaryContainer, tint: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        SeyirIcon(icon, size = iconSize, tint = tint)
    }
}

/** Pano .ctr: dikeyde ortalı boş durum; ikon, büyük başlık, açıklama ve eylemler. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String, modifier: Modifier = Modifier, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        CategoryCircle(icon, size = 56.dp, iconSize = 26.dp, background = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            title, Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.02).em),
        )
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp))
        extra()
    }
}
