package com.mercan.maliyem.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.accounts.AccountPicker
import com.mercan.maliyem.ui.categories.CategoryDialog
import com.mercan.maliyem.ui.components.ActionTone
import com.mercan.maliyem.ui.components.DateSelectionDialog
import com.mercan.maliyem.ui.components.ReceiptThumbnail
import com.mercan.maliyem.ui.components.ReceiptViewer
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SeyirActionButton
import com.mercan.maliyem.ui.components.SeyirChip
import com.mercan.maliyem.ui.components.SeyirSegmented
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.recurring.FrequencyPicker
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.MoneyInputValue
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.ui.theme.countSlide
import com.mercan.maliyem.ui.theme.crossFade
import com.mercan.maliyem.ui.theme.slideFade
import com.mercan.maliyem.util.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Yeni kayıt alt sayfası; [editId] verilirse aynı form düzenleme için açılır. Kayıt olunca önce sayfa kapanır, sonra [onSaved] çağrılır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onSaved: (SavedEntry) -> Unit,
    onReceiptCamera: () -> Unit,
    onReceiptGallery: () -> Unit,
    editId: Long? = null,
    editTransfer: Boolean = false,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelReceiptReading() }
    }
    val canChangeSheet = remember(viewModel) {
        { target: SheetValue ->
            target != SheetValue.Hidden || (!viewModel.saving.value && !viewModel.receiptImporting.value) || viewModel.saved.value != null
        }
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = canChangeSheet)
    val locale = LocalConfiguration.current.locales[0]
    var ready by remember(editId, editTransfer, locale) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(viewModel, editId, editTransfer, locale) { ready = viewModel.prepare(editId, editTransfer, locale) }
    val onSavedCurrent by rememberUpdatedState(onSaved)
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        saved?.let { entry ->
            sheet.hide()
            onSavedCurrent(entry)
            viewModel.reset()
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val writeFailed by viewModel.writeFailed.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = {
            if (!viewModel.saving.value && !viewModel.receiptImporting.value) {
                viewModel.reset()
                onDismiss()
            }
        },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        when (ready) {
            false -> WriteError(Modifier.padding(Spacing.lg))
            true -> state?.takeIf { it.editId == editId && (editId == null || it.transfer == editTransfer) }?.let {
                AddTransactionContent(it, locale, viewModel, saving, writeFailed, onReceiptCamera, onReceiptGallery)
            }
            null -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionContent(
    state: AddUiState,
    locale: Locale,
    viewModel: AddTransactionViewModel,
    saving: Boolean,
    writeFailed: Boolean,
    onReceiptCamera: () -> Unit,
    onReceiptGallery: () -> Unit,
) {
    val form = state.form
    val receipt by viewModel.receiptPath.collectAsStateWithLifecycle()
    val importing by viewModel.receiptImporting.collectAsStateWithLifecycle()
    val photoFailed by viewModel.receiptFailed.collectAsStateWithLifecycle()
    val receiptReading by viewModel.receiptReading.collectAsStateWithLifecycle()
    val receiptFilled by viewModel.receiptFilled.collectAsStateWithLifecycle()
    val futureRule = state.frequency != null && form.date > state.today
    val amountField = remember { BringIntoViewRequester() }
    val categoryField = remember { BringIntoViewRequester() }
    val accountField = remember { BringIntoViewRequester() }
    var saveAttempts by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.error, saveAttempts) {
        val field = when (state.error) {
            AddFormError.CATEGORY_MISSING -> categoryField
            AddFormError.ACCOUNT_MISSING -> accountField
            null -> null
            else -> amountField
        }
        if (field != null) {
            withFrameNanos { }
            field.bringIntoView()
        }
    }

    Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = Spacing.lg)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(when {
                    state.editId == null -> R.string.add_title
                    state.transfer -> R.string.transfer_edit_title
                    else -> R.string.edit_title
                }),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                fontWeight = FontWeight.Bold,
            )
            if (state.editId == null || !state.transfer) {
                TypeSelector(state, { viewModel.setType(it) }, viewModel::selectTransfer)
            }
            AmountField(
                form.amountText,
                form.type.takeUnless { state.transfer },
                { viewModel.setAmount(it, locale) },
                modifier = Modifier.bringIntoViewRequester(amountField),
                error = state.error?.takeUnless { it == AddFormError.CATEGORY_MISSING || it == AddFormError.ACCOUNT_MISSING },
            )
            val selected = state.accounts.firstOrNull { it.id == form.accountId }
            if (state.transfer) {
                Column(Modifier.bringIntoViewRequester(accountField)) {
                    if (state.canTransfer) {
                        AccountPicker(
                            R.string.transfer_from, selected, state.activeAccounts,
                            viewModel::setAccount,
                        )
                        AccountPicker(
                            R.string.transfer_to, state.accounts.firstOrNull { it.id == state.toAccountId },
                            state.activeAccounts.filter { it.id != form.accountId }, viewModel::setToAccount,
                        )
                    } else {
                        SubText(stringResource(R.string.transfer_need_accounts))
                    }
                }
            } else {
                Column(Modifier.bringIntoViewRequester(categoryField)) {
                    CategoryChips(
                        form.type, form.categoryId, state.categories, state.error == AddFormError.CATEGORY_MISSING,
                        viewModel::setCategory, viewModel::addCategory,
                    )
                }
                if (state.activeAccounts.size > 1 || selected?.archived == true || state.error == AddFormError.ACCOUNT_MISSING) {
                    Column(Modifier.bringIntoViewRequester(accountField)) {
                        AccountPicker(R.string.account_label, selected, state.activeAccounts, viewModel::setAccount)
                        if (state.error == AddFormError.ACCOUNT_MISSING) SubText(stringResource(R.string.account_choose))
                    }
                }
            }
            Column {
                if (state.editId == null && !state.transfer) {
                    FrequencyPicker(state.frequency, viewModel::setFrequency, includeNone = true, enabled = !saving)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                DateRow(form.date, state.today, locale, allowFuture = state.frequency != null, onPick = viewModel::setDate)
                QuickDates(form.date, state.today, viewModel::setDate)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NoteRow(form.note, viewModel::setNote)
                if (!state.transfer) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ReceiptField(
                        receipt, futureRule, saving, importing, photoFailed,
                        onReceiptCamera, onReceiptGallery, viewModel::removeReceipt,
                    )
                }
            }
        }
        // Tutar geçerliyse düğme ne kaydedileceğini söyler: "Kaydet · 412,85 ₺".
        val amountMinor = state.amountMinor?.takeIf { it > 0 }
        if (receiptReading) {
            val reading = stringResource(R.string.receipt_reading)
            SubText(
                reading,
                Modifier.clearAndSetSemantics {
                    contentDescription = reading
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }
        if (receiptFilled != 0) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SubText(
                    stringResource(R.string.receipt_filled),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                SeyirActionButton(stringResource(R.string.add_undo), viewModel::undoReceiptSuggestion, enabled = !saving)
            }
        }
        if (writeFailed) WriteError()
        Button(
            onClick = {
                saveAttempts++
                viewModel.save()
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg).heightIn(min = 48.dp),
            enabled = !saving && !importing && (!state.transfer || state.canTransfer) && !(futureRule && receipt != null),
        ) {
            AnimatedContent(amountMinor, transitionSpec = { crossFade() }, label = "saveLabel") { minor ->
                Text(
                    if (minor != null) stringResource(R.string.add_save_amount, formatMoney(minor, locale)) else stringResource(R.string.add_save),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ReceiptField(
    path: String?,
    futureRule: Boolean,
    saving: Boolean,
    importing: Boolean,
    failed: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf(false) }
    val enabled = !saving && !importing
    val addLabel = stringResource(R.string.receipt_add)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun launchSource(launch: () -> Unit) {
        focus.clearFocus()
        keyboard?.hide()
        choosing = false
        launch()
    }
    if (path == null) {
        SettingRow(
            R.drawable.ic_seyir_receipt, addLabel,
            onClick = { choosing = true }, enabled = enabled && !futureRule, horizontalPadding = 0.dp,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val open = stringResource(R.string.receipt_open)
            ReceiptThumbnail(
                path, Modifier.size(64.dp).clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button, onClickLabel = open) { viewing = true }
                    .semantics { contentDescription = open },
            )
            Column {
                Text(stringResource(R.string.receipt_photo), style = MaterialTheme.typography.bodyLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SeyirActionButton(
                        stringResource(R.string.receipt_replace), { choosing = true },
                        icon = R.drawable.ic_seyir_receipt, enabled = enabled && !futureRule,
                    )
                    SeyirActionButton(
                        stringResource(R.string.receipt_remove), onRemove,
                        icon = R.drawable.ic_seyir_trash, tone = ActionTone.Danger, enabled = enabled,
                    )
                }
            }
        }
    }
    if (futureRule) SubText(stringResource(R.string.receipt_future))
    if (importing) SubText(stringResource(R.string.receipt_importing))
    if (failed) {
        Text(
            stringResource(R.string.receipt_failed),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        )
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(if (path == null) addLabel else stringResource(R.string.receipt_replace)) },
            text = {
                Column {
                    SettingRow(
                        null, stringResource(R.string.receipt_camera),
                        onClick = { launchSource(onCamera) }, horizontalPadding = 0.dp,
                    )
                    SettingRow(
                        null, stringResource(R.string.receipt_gallery),
                        onClick = { launchSource(onGallery) }, horizontalPadding = 0.dp,
                    )
                }
            },
            confirmButton = { TextButton({ choosing = false }) { Text(stringResource(R.string.add_cancel)) } },
        )
    }
    if (viewing && path != null) ReceiptViewer(path, onBack = { viewing = false }, draft = true)
}

@Composable
private fun TypeSelector(state: AddUiState, onSelect: (TransactionType) -> Unit, onTransfer: () -> Unit) {
    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
    val labels = listOf(R.string.add_expense, R.string.add_income) +
        if (state.editId == null) listOf(R.string.add_transfer) else emptyList()
    SeyirSegmented(
        options = labels.map { stringResource(it) },
        selected = if (state.transfer) types.size else types.indexOf(state.form.type),
        onSelect = { if (it == types.size) onTransfer() else onSelect(types[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AmountField(text: String, type: TransactionType?, onChange: (String) -> Unit, error: AddFormError?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val income = type == TransactionType.INCOME
    val incomeColor = LocalMoneyColors.current.onIncomeContainer
    val focus = remember { FocusRequester() }
    // Sayfa açılınca klavye doğrudan tutara gelsin.
    LaunchedEffect(Unit) { focus.requestFocus() }
    val label = stringResource(R.string.add_amount)
    val errorText = error?.let { stringResource(errorRes(it)) }
    // Gider "−", gelir "+" ve yeşil; renk 150 ms'de geçer (ADR-0016).
    val lineColor by animateColorAsState(
        when {
            errorText != null -> colors.error
            income -> incomeColor
            else -> colors.primary
        },
        Motion.fast(), label = "amountLine",
    )
    val signColor by animateColorAsState(if (income) incomeColor else colors.onSurfaceVariant, Motion.fast(), label = "amountSign")
    Column(modifier) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics {
                contentDescription = label
                if (errorText != null) error(errorText)
            },
            textStyle = MoneyInputValue.copy(color = colors.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            decorationBox = { inner ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (type != null) {
                            AnimatedContent(income, transitionSpec = { countSlide(targetState) }, label = "amountSign") { inc ->
                                Text(if (inc) "+" else "−", style = MoneyInputValue, color = signColor, modifier = Modifier.padding(end = Spacing.xs))
                            }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text("0", style = MoneyInputValue, color = colors.onSurfaceVariant.copy(alpha = .5f))
                            }
                            inner()
                        }
                        Text("₺", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Spacer(Modifier.fillMaxWidth().height(2.dp).background(lineColor))
                }
            },
        )
        AnimatedContent(errorText, transitionSpec = { crossFade() }, label = "amountError") { message ->
            if (message != null) {
                Text(message, color = colors.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
    }
}

private fun errorRes(error: AddFormError) = when (error) {
    AddFormError.AMOUNT_EMPTY -> R.string.add_error_amount_empty
    AddFormError.AMOUNT_INVALID -> R.string.add_error_amount_invalid
    AddFormError.AMOUNT_ZERO -> R.string.add_error_amount_zero
    AddFormError.CATEGORY_MISSING -> R.string.add_error_category
    AddFormError.ACCOUNT_MISSING -> R.string.account_choose
}

@Composable
private fun WriteError(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.add_error_write),
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private data class CategoryChoices(val type: TransactionType, val selectedId: Long?, val categories: List<CategoryEntity>)

@Composable
private fun CategoryChips(
    type: TransactionType,
    selectedId: Long?,
    categories: List<CategoryEntity>,
    missing: Boolean,
    onSelect: (Long) -> Unit,
    onCreate: (name: String, icon: String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    // Gider/Gelir değişince çipler kayarak değişir; aynı türde seçim yalnız renkle geçer.
    AnimatedContent(
        CategoryChoices(type, selectedId, categories),
        transitionSpec = { slideFade(targetState.type == TransactionType.INCOME) },
        contentKey = { it.type },
        label = "addCategories",
    ) { shown ->
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup().animateContentSize(Motion.enter()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            shown.categories.forEach { category ->
                SeyirChip(
                    categoryName(category.key, category.name),
                    selected = category.id == shown.selectedId,
                    onClick = { onSelect(category.id) },
                    icon = categoryIcon(category.icon),
                )
            }
            SeyirChip(
                stringResource(R.string.add_new_category),
                selected = false,
                onClick = { creating = true },
                icon = R.drawable.ic_seyir_plus,
                role = Role.Button,
            )
        }
    }
    AnimatedVisibility(missing, enter = fadeIn(Motion.fast()) + expandVertically(Motion.fast()), exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.fast())) {
        Text(
            stringResource(R.string.add_error_category),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (creating) {
        CategoryDialog(
            category = null,
            onDismiss = { creating = false },
            onSave = { name, icon ->
                onCreate(name, icon)
                creating = false
            },
        )
    }
}

/** Yaygın iki tarih tek dokunuşla; başka gün için takvim satırdan açılır. */
@Composable
private fun QuickDates(date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val yesterday = today.minusDays(1)
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup().padding(start = 24.dp + Spacing.md, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SeyirChip(stringResource(R.string.add_today), date == today, { onPick(today) })
        SeyirChip(stringResource(R.string.add_yesterday), date == yesterday, { onPick(yesterday) })
    }
}

@Composable
private fun SheetRow(icon: Int, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SeyirIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun DateRow(
    date: LocalDate,
    today: LocalDate,
    locale: Locale,
    allowFuture: Boolean,
    onPick: (LocalDate) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Bugünse sol "Bugün", sağ tam tarih; değilse sol tarih, sağ yalnız haftanın günü (tekrar olmasın).
    val isToday = date == today
    val title = if (isToday) stringResource(R.string.add_today) else remember(date, locale) {
        DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(date)
    }
    val full = remember(date, isToday, locale) {
        DateTimeFormatter.ofPattern(if (isToday) "d MMMM EEEE" else "EEEE", locale).format(date)
    }
    SheetRow(
        R.drawable.ic_seyir_calendar,
        Modifier.clip(MaterialTheme.shapes.small).clickable(role = Role.Button) { open = true },
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(full, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
    if (open) {
        DateSelectionDialog(
            initial = date,
            onDismiss = { open = false },
            onPick = onPick,
            latest = today.takeUnless { allowFuture },
        )
    }
}

@Composable
private fun NoteRow(note: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.add_note)
    SheetRow(R.drawable.ic_seyir_note) {
        BasicTextField(
            value = note,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            decorationBox = { inner ->
                if (note.isEmpty()) {
                    Text(stringResource(R.string.add_note_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                inner()
            },
        )
    }
}
