package com.mercan.maliyem.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeyirFormSheet(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    dismissText: String,
    dismissEnabled: Boolean = true,
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val canDismiss by rememberUpdatedState(dismissEnabled)
    ModalBottomSheet(
        onDismissRequest = { if (canDismiss) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || canDismiss },
        ),
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            val scroll = if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().then(scroll).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                content()
            }
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TextButton(onDismiss, Modifier.weight(1f).heightIn(min = 48.dp), enabled = dismissEnabled) {
                    Text(dismissText)
                }
                if (confirmText != null) {
                    SeyirButton(confirmText, onConfirm, Modifier.weight(1f), enabled = confirmEnabled)
                }
            }
        }
    }
}
