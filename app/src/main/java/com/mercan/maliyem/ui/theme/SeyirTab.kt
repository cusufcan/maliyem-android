package com.mercan.maliyem.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val SeyirTabIndicatorWidth = 48.dp
internal val SeyirTabIndicatorHeight = 26.dp
internal val SeyirTabVerticalPadding = 8.dp

@Composable
internal fun SeyirTab(
    label: String,
    @DrawableRes glyph: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    Column(
        modifier
            .semantics { contentDescription = label }
            .selectable(selected, interactions, indication = null, onClick = onClick, role = Role.Tab)
            .padding(vertical = SeyirTabVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.width(SeyirTabIndicatorWidth).height(SeyirTabIndicatorHeight)
                .clip(CircleShape).indication(interactions, ripple(bounded = false)),
            contentAlignment = Alignment.Center,
        ) {
            SeyirIcon(glyph, tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
        Text(
            label, Modifier.clearAndSetSemantics {},
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}
