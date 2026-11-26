package com.mercan.maliyem.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val label = listOfNotNull(title, subtitle, amount).joinToString(", ")
    Row(
        modifier.heightIn(min = 64.dp).padding(vertical = Spacing.sm).clearAndSetSemantics { contentDescription = label },
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
            Text(title, style = type.bodyLarge, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = type.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            amount,
            style = type.bodyLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (income) LocalMoneyColors.current.onIncomeContainer else colors.onSurface,
            maxLines = 1,
        )
    }
}
