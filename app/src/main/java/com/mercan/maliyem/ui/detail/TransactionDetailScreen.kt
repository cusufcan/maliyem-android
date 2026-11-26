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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.ui.components.BackHeader
import com.mercan.maliyem.ui.components.CategoryCircle
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SettingRow
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.components.categoryName
import com.mercan.maliyem.ui.theme.MoneyHeroValue
import com.mercan.maliyem.ui.theme.Spacing
import com.mercan.maliyem.ui.theme.categoryIcon
import com.mercan.maliyem.util.formatMoney
import com.mercan.maliyem.util.splitFraction
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: (TransactionEntity) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BackHeader(stringResource(R.string.detail_title), onBack)
        val current = state ?: return@Column
        val t = current.item.transaction
        val income = t.type == TransactionType.INCOME
        val category = categoryName(current.item.category.key, current.item.category.name)

        // Kayıt bilgisi kayar; Sil/Düzenle altta sabit kalır (pano: flex boşluğu + .acts).
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(
            Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CategoryCircle(categoryIcon(current.item.category.icon), size = 56.dp, iconSize = 26.dp)
            SubText(stringResource(if (income) R.string.add_income else R.string.add_expense))
            val (whole, fraction) = splitFraction(formatMoney(if (income) t.amountMinor else -t.amountMinor, locale, signed = true), locale)
            BasicText(
                buildAnnotatedString {
                    append(whole)
                    withStyle(SpanStyle(fontSize = .46.em, letterSpacing = (-.01).em)) { append(fraction) }
                },
                style = MoneyHeroValue.copy(fontSize = 52.sp, lineHeight = 56.sp, color = colors.onSurface),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 52.sp, stepSize = 2.sp),
            )
        }

        SeyirCard(padding = PaddingValues(horizontal = Spacing.lg)) {
            SettingRow(R.drawable.ic_seyir_tag, stringResource(R.string.detail_category), value = category, horizontalPadding = 0.dp)
            HorizontalDivider(color = colors.outlineVariant)
            SettingRow(R.drawable.ic_seyir_calendar, stringResource(R.string.detail_date), value = t.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)), horizontalPadding = 0.dp)
            t.note?.let {
                HorizontalDivider(color = colors.outlineVariant)
                SettingRow(R.drawable.ic_seyir_note, stringResource(R.string.add_note), value = it, horizontalPadding = 0.dp)
            }
        }
        SubText(
            stringResource(R.string.detail_share, category, formatMoney(current.categoryMonthMinor, locale), current.sharePercent),
            Modifier.padding(horizontal = Spacing.xs),
        )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SeyirButton(
                stringResource(R.string.detail_delete),
                { scope.launch { viewModel.delete()?.let(onDeleted) } },
                Modifier.weight(1f), icon = R.drawable.ic_seyir_trash, tonal = true,
            )
            SeyirButton(stringResource(R.string.detail_edit), { onEdit(t.id) }, Modifier.weight(1f), icon = R.drawable.ic_seyir_edit)
        }
    }
}
