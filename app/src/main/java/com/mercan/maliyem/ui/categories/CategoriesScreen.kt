package com.mercan.maliyem.ui.categories

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.isFallback
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.CategoryCircle
import com.mercan.maliyem.ui.components.EmptyState
import com.mercan.maliyem.ui.components.SeyirFabClearance
import com.mercan.maliyem.ui.components.SeyirListScaffold
import com.mercan.maliyem.ui.components.SeyirSegmented
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.ui.theme.seyirAnimateItem
import com.mercan.maliyem.ui.theme.slideFade
import com.mercan.maliyem.util.formatMoney

private val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

/** Açık iletişim kutusu: yeni kategori (id 0) ya da düzenlenen kategori. */
private const val NONE = -1L

@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state ?: return
    var editing by rememberSaveable { mutableLongStateOf(NONE) }
    var newType by rememberSaveable { mutableStateOf<TransactionType?>(null) }
    var confirmDelete by rememberSaveable { mutableLongStateOf(NONE) }
    val writeFailed by viewModel.writeFailed.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val expenseList = rememberLazyListState()
    val incomeList = rememberLazyListState()
    fun listState(type: TransactionType): LazyListState = if (type == TransactionType.EXPENSE) expenseList else incomeList

    SeyirListScaffold(
        stringResource(R.string.categories_add), listState(current.type),
        onAdd = { newType = current.type; editing = 0L },
    ) {
        Column(
            Modifier.fillMaxSize().padding(top = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                BackHeader(stringResource(R.string.settings_categories), onBack)
                SeyirSegmented(
                    listOf(stringResource(R.string.add_expense), stringResource(R.string.add_income)),
                    types.indexOf(current.type),
                    { viewModel.setType(types[it]) },
                )
                if (writeFailed) {
                    Text(
                        stringResource(R.string.categories_write_failed),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // Tür değişince liste yönlü kayar; aynı tür içinde ekleme/silme satır animasyonuyla olur (ADR-0016).
            AnimatedContent(
                current,
                Modifier.weight(1f),
                transitionSpec = { slideFade(types.indexOf(targetState.type) > types.indexOf(initialState.type)) },
                contentKey = { it.type },
                label = "categoriesType",
            ) { shown ->
                val items = shown.items
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState(shown.type),
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = SeyirFabClearance),
                    ) {
                        itemsIndexed(items, key = { _, item -> item.category.id }) { index, item ->
                            val c = item.category
                            val records = if (item.count > 0) pluralStringResource(R.plurals.records, item.count, item.count)
                            else stringResource(R.string.categories_no_records)
                            val sub = if (c.isFallback()) records + stringResource(R.string.categories_fixed) else records
                            val top = if (index == 0) 20.dp else 0.dp
                            val bottom = if (index == items.lastIndex) 20.dp else 0.dp
                            Column(
                                seyirAnimateItem()
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(top, top, bottom, bottom))
                                    .background(colors.surfaceContainer),
                            ) {
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = colors.outlineVariant)
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = shown.type == current.type, role = Role.Button) { editing = c.id }.heightIn(min = 64.dp)
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                ) {
                                    CategoryCircle(categoryIcon(c.icon))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            categoryName(c.key, c.name), style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        SubText(sub)
                                    }
                                    SeyirIcon(R.drawable.ic_seyir_chevron_right, tint = colors.onSurfaceVariant)
                                }
                            }
                        }
                        if (items.isNotEmpty()) item(key = "note") {
                            SubText(
                                stringResource(R.string.categories_note),
                                seyirAnimateItem().padding(top = Spacing.md, start = Spacing.xs, end = Spacing.xs),
                            )
                        }
                    }
                    if (items.isEmpty()) {
                        EmptyState(
                            R.drawable.ic_seyir_tag, stringResource(R.string.categories_empty_title),
                            stringResource(R.string.categories_empty_body),
                            Modifier.fillMaxSize().padding(start = Spacing.lg, end = Spacing.lg, bottom = SeyirFabClearance),
                        )
                    }
                }
            }
        }
    }

    val category = current.items.firstOrNull { it.category.id == editing }?.category
    if (editing == 0L || category != null) {
        CategoryDialog(
            category = category,
            onDismiss = { editing = NONE },
            onSave = { name, icon ->
                if (category == null) viewModel.add(newType ?: current.type, name, icon) else viewModel.update(category, name, icon)
                editing = NONE
            },
            onDelete = category?.takeUnless { it.isFallback() }?.let { { confirmDelete = it.id; editing = NONE } },
        )
    } else if (editing != NONE) {
        LaunchedEffect(editing) { editing = NONE }
    }
    if (confirmDelete != NONE) {
        val item = current.items.firstOrNull { it.category.id == confirmDelete }
        if (item == null) {
            LaunchedEffect(confirmDelete) { confirmDelete = NONE }
        } else {
            AlertDialog(
                onDismissRequest = { confirmDelete = NONE },
                title = { Text(stringResource(R.string.categories_delete_title, categoryName(item.category.key, item.category.name))) },
                text = {
                    val locale = LocalConfiguration.current.locales[0]
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (item.count > 0) Text(pluralStringResource(R.plurals.categories_delete_body, item.count, item.count))
                        if (item.ruleCount > 0) Text(pluralStringResource(R.plurals.categories_delete_rules, item.ruleCount, item.ruleCount))
                        item.budgetMinor?.let { Text(stringResource(R.string.categories_delete_budget, formatMoney(it, locale))) }
                        if (item.count == 0 && item.ruleCount == 0 && item.budgetMinor == null) {
                            Text(stringResource(R.string.categories_delete_empty))
                        }
                    }
                },
                confirmButton = {
                    TextButton({ viewModel.delete(item.category); confirmDelete = NONE }) { Text(stringResource(R.string.detail_delete)) }
                },
                dismissButton = { TextButton({ confirmDelete = NONE }) { Text(stringResource(R.string.add_cancel)) } },
            )
        }
    }
}
