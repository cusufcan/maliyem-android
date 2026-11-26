package com.mercan.maliyem.ui.categories

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcons

/**
 * Kategori ekleme/düzenleme kutusu. Kategoriler ekranı ve Ekle sayfası ("+ Yeni") ortak kullanır (ADR-0017).
 * [category] null ise yeni kategori açılır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryDialog(
    category: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val initialName = category?.let { categoryName(it.key, it.name) }.orEmpty()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var icon by rememberSaveable { mutableStateOf(category?.icon ?: "dots") }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category == null) R.string.categories_new else R.string.categories_edit)) },
        text = {
            // %200 yazı boyutunda taşmasın diye içerik kayar.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    name, { name = it.take(40) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.categories_name)) }, singleLine = true,
                )
                SubText(stringResource(R.string.categories_icon))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    maxItemsInEachRow = 5,
                ) {
                    categoryIcons.forEach { (key, def) ->
                        val selected = key == icon
                        val label = stringResource(def.label)
                        val bg by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, Motion.fast(), label = "iconBg")
                        val ring by animateColorAsState(if (selected) colors.primary else Color.Transparent, Motion.fast(), label = "iconRing")
                        val tint by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant, Motion.fast(), label = "iconTint")
                        Box(
                            Modifier.size(48.dp).background(bg, CircleShape).border(2.dp, ring, CircleShape)
                                .selectable(selected, role = Role.RadioButton) { icon = key }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            SeyirIcon(def.drawable, tint = tint)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({ onSave(name.trim(), icon) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.add_save)) }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(it) { Text(stringResource(R.string.detail_delete), color = colors.error) } }
                TextButton(onDismiss) { Text(stringResource(R.string.add_cancel)) }
            }
        },
    )
}
