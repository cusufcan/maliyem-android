package com.mercan.maliyem.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon

/**
 * Kayıt satırı: kategori dairesi, başlık (not ya da kategori), alt satırda kategori ve işaretli tutar.
 * Özet ve İşlemler ekranı ortak kullanır. [amount] işaretiyle birlikte biçimlenmiş gelir.
 */
@Composable
fun TransactionRow(
    title: String,
    subtitle: String?,
    icon: String,
    amount: String,
    income: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    horizontalPadding: Dp = 0.dp,
    recurring: Boolean = false,
    onOpenReceipt: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val justNow = stringResource(R.string.add_just_now)
    // Yeni eklenen kayıt "az önce" ile işaretlenir; snackbar kapanınca vurgu kalkar.
    val shownSubtitle = if (highlighted) listOfNotNull(subtitle, justNow).joinToString(" · ") else subtitle
    val repeatLabel = if (recurring) stringResource(R.string.settings_recurring) else null
    val label = listOfNotNull(title, shownSubtitle, repeatLabel, amount).joinToString(", ")
    val amountStyle = type.bodyLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)
    val amountWidth = rememberTextMeasurer().measure(amount, amountStyle, softWrap = false).size.width
    val amountColor = if (income) LocalMoneyColors.current.onIncomeContainer else colors.onSurface
    Row(
        modifier.fillMaxWidth().background(if (highlighted) colors.primaryContainer else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen)
                .heightIn(min = 64.dp).padding(
                    start = horizontalPadding,
                    end = if (onOpenReceipt == null) horizontalPadding else 0.dp,
                    top = Spacing.sm, bottom = Spacing.sm,
                ).clearAndSetSemantics { contentDescription = label },
            contentAlignment = Alignment.CenterStart,
        ) {
            val amountDp = with(LocalDensity.current) { amountWidth.toDp() }
            val titleSpace = with(LocalDensity.current) { 100.dp * fontScale }
            val stacked = maxWidth < 40.dp + Spacing.md * 2 + amountDp + titleSpace
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    Modifier.size(40.dp).background(colors.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SeyirIcon(categoryIcon(icon), tint = colors.onSecondaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (recurring) SeyirIcon(R.drawable.ic_seyir_repeat, tint = colors.onSurfaceVariant, size = 16.dp)
                        Text(title, style = type.bodyLarge, fontWeight = FontWeight.Normal, maxLines = if (stacked) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (shownSubtitle != null) {
                        Text(shownSubtitle, style = type.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (stacked) Text(amount, style = amountStyle, color = amountColor)
                }
                if (!stacked) Text(amount, style = amountStyle, color = amountColor, maxLines = 1)
            }
        }
        if (onOpenReceipt != null) {
            IconButton(onOpenReceipt, Modifier.padding(end = Spacing.xs)) {
                SeyirIcon(
                    R.drawable.ic_seyir_receipt, description = stringResource(R.string.receipt_open),
                    tint = colors.onSurfaceVariant, size = 16.dp,
                )
            }
        }
    }
}
