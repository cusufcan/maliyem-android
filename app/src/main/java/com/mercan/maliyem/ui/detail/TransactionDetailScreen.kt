package com.mercan.maliyem.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.accounts.accountName
import com.mercan.maliyem.ui.navigation.UndoAction
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.CategoryCircle
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.recurring.detailRes
import com.mercan.maliyem.ui.theme.MoneyHeroValue
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.util.formatMoney
import com.mercan.maliyem.util.splitFraction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    onOpenRule: (Long) -> Unit,
    onDeleted: (UndoAction) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val rule by viewModel.rule.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val deleteFailed by viewModel.deleteFailed.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) {
        deleted?.let {
            viewModel.acknowledgeDeleted()
            onDeleted(it)
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val route = viewModel.route
        BackHeader(stringResource(if (route.transfer) R.string.add_transfer else R.string.detail_title), onBack)
        val current = state ?: return@Column
        if (current == DetailUiState.Missing) {
            if (deleted == null) SubText(stringResource(R.string.detail_missing))
            return@Column
        }

        // Kayıt bilgisi kayar; Sil/Düzenle altta sabit kalır (pano: flex boşluğu + .acts).
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            when (current) {
                is DetailUiState.Record -> {
                    val (transaction, info) = current.item
                    val income = transaction.type == TransactionType.INCOME
                    val category = categoryName(info.key, info.name)
                    val account = accounts.firstOrNull { it.id == transaction.accountId }
                    DetailAmount(
                        categoryIcon(info.icon),
                        stringResource(if (income) R.string.add_income else R.string.add_expense),
                        formatMoney(if (income) transaction.amountMinor else -transaction.amountMinor, locale, signed = true),
                    )
                    DetailFields(transaction.date, transaction.note) {
                        SettingRow(R.drawable.ic_seyir_tag, stringResource(R.string.detail_category), value = category, horizontalPadding = 0.dp)
                        HorizontalDivider(color = colors.outlineVariant)
                        if (account != null && accounts.size > 1) {
                            SettingRow(categoryIcon(account.icon), stringResource(R.string.account_label), value = accountName(account), horizontalPadding = 0.dp)
                            HorizontalDivider(color = colors.outlineVariant)
                        }
                    }
                    transaction.receiptPath?.let { path ->
                        SettingRow(
                            R.drawable.ic_seyir_receipt, stringResource(R.string.receipt_open),
                            onClick = { onOpenReceipt(path) }, horizontalPadding = 0.dp,
                        )
                    }
                    rule?.takeIf { it.id == transaction.recurringRuleId }?.let { recurring ->
                        SettingRow(
                            R.drawable.ic_seyir_repeat, stringResource(recurring.frequency.detailRes()),
                            onClick = { onOpenRule(recurring.id) }, horizontalPadding = 0.dp,
                        )
                    }
                    SubText(
                        stringResource(R.string.detail_share, category, formatMoney(current.categoryMonthMinor, locale), current.sharePercent),
                        Modifier.padding(horizontal = Spacing.xs),
                    )
                }
                is DetailUiState.Transfer -> {
                    val transfer = current.item
                    DetailAmount(R.drawable.ic_seyir_transfer, stringResource(R.string.add_transfer), formatMoney(transfer.amountMinor, locale))
                    DetailFields(transfer.date, transfer.note) {
                        SettingRow(R.drawable.ic_seyir_wallet, stringResource(R.string.transfer_from),
                            value = accountName(accounts, transfer.fromAccountId), horizontalPadding = 0.dp)
                        HorizontalDivider(color = colors.outlineVariant)
                        SettingRow(R.drawable.ic_seyir_wallet, stringResource(R.string.transfer_to),
                            value = accountName(accounts, transfer.toAccountId), horizontalPadding = 0.dp)
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
                DetailUiState.Missing -> Unit
            }
        }
        if (deleteFailed) SubText(stringResource(R.string.detail_delete_failed))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SeyirButton(
                stringResource(R.string.detail_delete),
                viewModel::delete,
                Modifier.weight(1f), icon = R.drawable.ic_seyir_trash, tonal = true, enabled = !deleting,
            )
            SeyirButton(stringResource(R.string.detail_edit), onEdit, Modifier.weight(1f), icon = R.drawable.ic_seyir_edit, enabled = !deleting)
        }
    }
}


@Composable
private fun DetailAmount(icon: Int, label: String, amount: String) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CategoryCircle(icon, size = 56.dp, iconSize = 26.dp)
        SubText(label)
        val (whole, fraction) = splitFraction(amount, locale)
        BasicText(
            buildAnnotatedString {
                append(whole)
                withStyle(SpanStyle(fontSize = .46.em, letterSpacing = (-.01).em)) { append(fraction) }
            },
            style = MoneyHeroValue.copy(fontSize = 52.sp, lineHeight = 56.sp, color = MaterialTheme.colorScheme.onSurface),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 52.sp, stepSize = 2.sp),
        )
    }
}

@Composable
private fun DetailFields(date: LocalDate, note: String?, leading: @Composable () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val divider = MaterialTheme.colorScheme.outlineVariant
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    SeyirCard(padding = PaddingValues(horizontal = Spacing.lg)) {
        leading()
        SettingRow(R.drawable.ic_seyir_calendar, stringResource(R.string.detail_date), value = date.format(formatter), horizontalPadding = 0.dp)
        note?.let {
            HorizontalDivider(color = divider)
            SettingRow(R.drawable.ic_seyir_note, stringResource(R.string.add_note), value = it, horizontalPadding = 0.dp)
        }
    }
}
