package com.mercan.maliyem

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mercan.maliyem.ui.navigation.MaliyemNavHost
import com.mercan.maliyem.ui.theme.MaliyemTheme
import dagger.hilt.android.AndroidEntryPoint

/** Tek Activity; tüm ekranlar Compose ile çizilir. AppCompat, dil ve tema seçimini uygular. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // İçerik durum ve gezinme çubuklarının altına uzanır; boşluklar Compose'da verilir.
        enableEdgeToEdge()
        setContent {
            MaliyemTheme {
                Surface(Modifier.fillMaxSize()) {
                    MaliyemNavHost()
                }
            }
        }
    }
}
