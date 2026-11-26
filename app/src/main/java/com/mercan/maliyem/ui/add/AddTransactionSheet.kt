package com.mercan.maliyem.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.semantics.Role
import com.mercan.maliyem.ui.categories.CategoryDialog
import com.mercan.maliyem.ui.components.SeyirChip
import com.mercan.maliyem.ui.theme.LocalMoneyColors
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.countSlide
import com.mercan.maliyem.ui.theme.crossFade
import com.mercan.maliyem.ui.theme.slideFade
import com.mercan.maliyem.util.formatMoney
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mercan.maliyem.ui.components.SeyirSegmented
import com.mercan.maliyem.ui.components.categoryName
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.theme.MoneyInputValue
import com.mercan.maliyem.ui.theme.SeyirIcon
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Yeni kayıt alt sayfası; [editId] verilirse aynı form düzenleme için açılır. Kayıt olunca önce sayfa kapanır, sonra [onSaved] çağrılır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onSaved: (SavedEntry) -> Unit,
    editId: Long? = null,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { viewModel.setLocale(locale) }
    LaunchedEffect(editId) { editId?.let(viewModel::startEdit) }
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { entry ->
            sheet.hide()
            onSaved(entry)
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        state?.let { AddTransactionContent(it, locale, viewModel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionContent(state: AddUiState, locale: Locale, viewModel: AddTransactionViewModel) {
    val form = state.form
    Column(
        Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(if (state.editing) R.string.edit_title else R.string.add_title),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
            fontWeight = FontWeight.Bold,
        )
        TypeSelector(form.type, viewModel::setType)
        AmountField(
            form.amountText,
            form.type,
            viewModel::setAmount,
            error = state.error?.takeIf { it != AddFormError.CATEGORY_MISSING },
        )
        CategoryChips(state, viewModel::setCategory, viewModel::addCategory)
        Column {
            DateRow(form.date, state.today, locale, viewModel::setDate)
            QuickDates(form.date, state.today, viewModel::setDate)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NoteRow(form.note, viewModel::setNote)
        }
        // Tutar geçerliyse düğme ne kaydedileceğini söyler: "Kaydet · 412,85 ₺".
        val amountMinor = parseAmount(form.amountText, locale)?.takeIf { it > 0 }
        Button(viewModel::save, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
private fun TypeSelector(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val options = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
    SeyirSegmented(
        options = listOf(stringResource(R.string.add_expense), stringResource(R.string.add_income)),
        selected = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AmountField(text: String, type: TransactionType, onChange: (String) -> Unit, error: AddFormError?) {
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
    var lastError by remember { mutableStateOf(errorText) }
    if (errorText != null) lastError = errorText
    Column {
        BasicTextField(
            value = text,
            onValueChange = { value -> onChange(value.filter { it.isDigit() || it == ',' || it == '.' }) },
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
                        AnimatedContent(income, transitionSpec = { countSlide(targetState) }, label = "amountSign") { inc ->
                            Text(if (inc) "+" else "−", style = MoneyInputValue, color = signColor, modifier = Modifier.padding(end = Spacing.xs))
                        }
                        Column(Modifier.weight(1f)) {
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
        AnimatedVisibility(errorText != null, enter = fadeIn(Motion.fast()) + expandVertically(Motion.fast()), exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.fast())) {
            Text(lastError.orEmpty(), color = colors.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

private fun errorRes(error: AddFormError) = when (error) {
    AddFormError.AMOUNT_EMPTY -> R.string.add_error_amount_empty
    AddFormError.AMOUNT_INVALID -> R.string.add_error_amount_invalid
    AddFormError.AMOUNT_ZERO -> R.string.add_error_amount_zero
    AddFormError.CATEGORY_MISSING -> R.string.add_error_category
}

@Composable
private fun CategoryChips(state: AddUiState, onSelect: (Long) -> Unit, onCreate: (name: String, icon: String) -> Unit) {
    var creating by rememberSaveable { mutableStateOf(false) }
    // Gider/Gelir değişince çipler kayarak değişir; aynı türde seçim yalnız renkle geçer.
    AnimatedContent(
        state,
        transitionSpec = { slideFade(targetState.form.type == TransactionType.INCOME) },
        contentKey = { it.form.type },
        label = "addCategories",
    ) { shown ->
        FlowRow(
            Modifier.fillMaxWidth().animateContentSize(Motion.enter()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            shown.categories.forEach { category ->
                SeyirChip(
                    categoryName(category.key, category.name),
                    selected = category.id == shown.form.categoryId,
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
    AnimatedVisibility(state.error == AddFormError.CATEGORY_MISSING, enter = fadeIn(Motion.fast()) + expandVertically(Motion.fast()), exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.fast())) {
        Text(stringResource(R.string.add_error_category), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp + Spacing.md, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate, today: LocalDate, locale: Locale, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Bugünse sol "Bugün", sağ tam tarih; değilse sol tarih, sağ yalnız haftanın günü (tekrar olmasın).
    val isToday = date == today
    val title = if (isToday) stringResource(R.string.add_today) else DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(date)
    val full = DateTimeFormatter.ofPattern(if (isToday) "d MMMM EEEE" else "EEEE", locale).format(date)
    SheetRow(R.drawable.ic_seyir_calendar, Modifier.clickable { open = true }) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(full, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
    if (open) {
        // DatePicker UTC gece yarısı milisaniyesiyle çalışır; yerel saat dilimi karışmasın diye UTC'ye çevrilir.
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    picker.selectedDateMillis?.let {
                        onPick(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.add_pick)) }
            },
            dismissButton = { TextButton({ open = false }) { Text(stringResource(R.string.add_cancel)) } },
        ) { DatePicker(picker) }
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
