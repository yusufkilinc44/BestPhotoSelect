package com.bestphotoselect

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestphotoselect.data.ScanRepository
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.ScanState
import com.bestphotoselect.data.model.ThemeMode
import com.bestphotoselect.ui.AppNav
import com.bestphotoselect.ui.theme.BestPhotoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
// AppCompatActivity: uygulama içi dil seçimi Android 13 altında da etkinlik
// bağlamına uygulansın diye (bkz. AppLanguage).
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var scanRepository: ScanRepository

    /** Otomatik tarama bildirimine dokunulunca artar; AppNav sonuç ekranını açar. */
    private val openResultsRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // İlk karede yanlış temanın yanıp sönmemesi için kayıtlı tema baştan okunur
        // (DataStore tek küçük dosya; okuma milisaniyeler sürer).
        handleOpenResults(intent)
        val initialTheme = runBlocking { settingsRepository.themeMode.first() }
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialTheme)
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Durum/gezinme çubuğu simgelerinin rengi seçilen temayı izlesin.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark }
                )
                onDispose {}
            }
            BestPhotoTheme(darkTheme = dark) {
                AppNav(openResultsRequest = openResultsRequest.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenResults(intent)
    }

    private fun handleOpenResults(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_RESULTS, false) != true) return
        intent.removeExtra(EXTRA_OPEN_RESULTS)
        if (scanRepository.state.value is ScanState.Done) openResultsRequest.intValue++
    }

    companion object {
        const val EXTRA_OPEN_RESULTS = "open_results"

        // androidx.activity'nin varsayılan gezinme çubuğu perdeleri
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
