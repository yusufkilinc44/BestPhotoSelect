package com.bestphotoselect

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.ThemeMode
import com.bestphotoselect.ui.AppNav
import com.bestphotoselect.ui.theme.BestPhotoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // İlk karede yanlış temanın yanıp sönmemesi için kayıtlı tema baştan okunur
        // (DataStore tek küçük dosya; okuma milisaniyeler sürer).
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
                AppNav()
            }
        }
    }

    private companion object {
        // androidx.activity'nin varsayılan gezinme çubuğu perdeleri
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
