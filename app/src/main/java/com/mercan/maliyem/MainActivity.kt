package com.mercan.maliyem

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.repository.RecurringRepository
import com.mercan.maliyem.quickentry.quickEntryType
import com.mercan.maliyem.ui.navigation.MaliyemNavHost
import com.mercan.maliyem.ui.theme.MaliyemTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Tek Activity; tüm ekranlar Compose ile çizilir. AppCompat, dil ve tema seçimini uygular. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var recurring: RecurringRepository

    private var pendingQuickEntry by mutableStateOf<TransactionType?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Yeniden kurulan alt sayfa kendi taslağını saklar; açılış eylemi tekrarlanmaz.
        pendingQuickEntry = if (savedInstanceState == null) quickEntryType(intent.dataString)
            else savedInstanceState.getString(KEY_QUICK_ENTRY)?.let(TransactionType::valueOf)
        // İçerik durum ve gezinme çubuklarının altına uzanır; boşluklar Compose'da verilir.
        enableEdgeToEdge()
        setContent {
            MaliyemTheme {
                Surface(Modifier.fillMaxSize()) {
                    MaliyemNavHost(
                        quickEntry = pendingQuickEntry,
                        onQuickEntryConsumed = { pendingQuickEntry = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        quickEntryType(intent.dataString)?.let { pendingQuickEntry = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_QUICK_ENTRY, pendingQuickEntry?.name)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { recurring.generateDue() }
    }
}

private const val KEY_QUICK_ENTRY = "pendingQuickEntry"
