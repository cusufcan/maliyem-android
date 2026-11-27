package com.mercan.maliyem.ui.lock

import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.CardLabel
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirCard
import com.mercan.maliyem.ui.components.SeyirSegmented
import com.mercan.maliyem.ui.components.SubText
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing

/** Ayarlar'da kilidin durumu ve arka planda beklenecek süre. */
@Composable
internal fun SeyirSecurityCard(
    enabled: Boolean,
    available: Boolean,
    authenticating: Boolean,
    timeout: LockTimeout,
    onEnabledChange: (Boolean) -> Unit,
    onTimeoutChange: (LockTimeout) -> Unit,
) {
    val canToggle = available && !authenticating
    SeyirCard(padding = PaddingValues()) {
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg)) {
            CardLabel(stringResource(R.string.settings_security))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(enabled, enabled = canToggle, role = Role.Switch, onValueChange = onEnabledChange)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_app_lock), style = MaterialTheme.typography.bodyLarge)
                SubText(stringResource(if (available) R.string.settings_app_lock_body else R.string.app_lock_unavailable))
            }
            Switch(checked = enabled, onCheckedChange = null, enabled = canToggle)
        }
        AnimatedVisibility(enabled, enter = Motion.expand(), exit = Motion.collapse()) {
            Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                CardLabel(stringResource(R.string.settings_app_lock_timing))
                SeyirSegmented(
                    listOf(R.string.app_lock_immediate, R.string.app_lock_one_minute, R.string.app_lock_five_minutes)
                        .map { stringResource(it) },
                    timeout.ordinal,
                    { onTimeoutChange(LockTimeout.entries[it]) },
                    Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

/** Kilitliyken tek eylem sistem doğrulamasını yeniden açar. */
@Composable
internal fun SeyirLockedScreen(available: Boolean, authenticating: Boolean, onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AndroidView(
                factory = { context -> ImageView(context).apply { setImageResource(R.mipmap.ic_launcher_round) } },
                modifier = Modifier.size(72.dp),
            )
            Text(
                stringResource(R.string.app_lock_locked),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            if (!available) {
                Text(
                    stringResource(R.string.app_lock_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            SeyirButton(stringResource(R.string.app_lock_unlock), onUnlock, enabled = available && !authenticating)
        }
    }
}
