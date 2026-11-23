package com.bestphotoselect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/*
 * Renkli, eğlenceli görünüm: uygulama ikonundaki diyafram paletinden (kırmızı,
 * turuncu, sarı, yeşil, mavi, mor) türetilmiş açık ve koyu şemalar. Duvar
 * kâğıdından renk alan "dinamik renk" bilerek kullanılmıyor; uygulamanın kendi
 * kimliği her telefonda aynı görünsün.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF6C4CE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E0FF),
    onPrimaryContainer = Color(0xFF250A6B),
    secondary = Color(0xFFF26B1D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D1),
    onSecondaryContainer = Color(0xFF4A1C00),
    tertiary = Color(0xFF13A05A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC9F4D9),
    onTertiaryContainer = Color(0xFF003A1D),
    error = Color(0xFFE5383B),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF5C0A0E),
    background = Color(0xFFFFF9F4),
    onBackground = Color(0xFF1D1A2B),
    surface = Color(0xFFFFF9F4),
    onSurface = Color(0xFF1D1A2B),
    surfaceVariant = Color(0xFFEFE9FA),
    onSurfaceVariant = Color(0xFF4F4866),
    outline = Color(0xFF8C84A6),
    outlineVariant = Color(0xFFD6CFEA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF6FF),
    surfaceContainer = Color(0xFFF6F0FF),
    surfaceContainerHigh = Color(0xFFF1EAFF),
    surfaceContainerHighest = Color(0xFFEBE3FF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBA8FF),
    onPrimary = Color(0xFF2A0C7A),
    primaryContainer = Color(0xFF4630B0),
    onPrimaryContainer = Color(0xFFEDE5FF),
    secondary = Color(0xFFFFB088),
    onSecondary = Color(0xFF4F1D00),
    secondaryContainer = Color(0xFF8A3A0D),
    onSecondaryContainer = Color(0xFFFFE3D1),
    tertiary = Color(0xFF7FE3A6),
    onTertiary = Color(0xFF00391C),
    tertiaryContainer = Color(0xFF00602F),
    onTertiaryContainer = Color(0xFFC9F4D9),
    error = Color(0xFFFF8A8C),
    onError = Color(0xFF5C0A0E),
    errorContainer = Color(0xFF8C1D22),
    onErrorContainer = Color(0xFFFFDAD8),
    background = Color(0xFF14122B),
    onBackground = Color(0xFFEAE6FF),
    surface = Color(0xFF14122B),
    onSurface = Color(0xFFEAE6FF),
    surfaceVariant = Color(0xFF2C2850),
    onSurfaceVariant = Color(0xFFCBC3E6),
    outline = Color(0xFF948CB3),
    outlineVariant = Color(0xFF433D6B),
    surfaceContainerLowest = Color(0xFF0F0D22),
    surfaceContainerLow = Color(0xFF1B1835),
    surfaceContainer = Color(0xFF201D3D),
    surfaceContainerHigh = Color(0xFF262246),
    surfaceContainerHighest = Color(0xFF2D2952)
)

/** Kartlar için renk çifti: zemin ve üstündeki yazı. */
@Immutable
data class Accent(val container: Color, val content: Color)

/** İkon paletinin altı rengi; kartlar sırayla/kimliğe göre bunlardan birini alır. */
private val LightAccents = listOf(
    Accent(Color(0xFFFFE1E1), Color(0xFF7A1518)), // kırmızı
    Accent(Color(0xFFFFE7D6), Color(0xFF7A3300)), // turuncu
    Accent(Color(0xFFFFF1C2), Color(0xFF654A00)), // sarı
    Accent(Color(0xFFDDF5C8), Color(0xFF2C5200)), // yeşil
    Accent(Color(0xFFD6ECFF), Color(0xFF0B4A75)), // mavi
    Accent(Color(0xFFEADFFF), Color(0xFF3F2470))  // mor
)

private val DarkAccents = listOf(
    Accent(Color(0xFF4D2026), Color(0xFFFFD6D6)),
    Accent(Color(0xFF4D2D16), Color(0xFFFFDCC2)),
    Accent(Color(0xFF45390F), Color(0xFFFFEFB0)),
    Accent(Color(0xFF24421B), Color(0xFFD4F5BC)),
    Accent(Color(0xFF133B59), Color(0xFFCDE8FF)),
    Accent(Color(0xFF36275E), Color(0xFFE7DAFF))
)

private val LocalAccents = staticCompositionLocalOf { LightAccents }

/** [key] (ör. albüm ya da grup kimliği) her zaman aynı rengi verir. */
@Composable
fun accentFor(key: Long): Accent {
    val accents = LocalAccents.current
    return accents[(key % accents.size).toInt().absoluteValue]
}

/** Üst çubuklar: açık temada lila, koyu temada mor zemin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appTopBarColors(): TopAppBarColors {
    val c = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = c.primaryContainer,
        scrolledContainerColor = c.primaryContainer,
        titleContentColor = c.onPrimaryContainer,
        navigationIconContentColor = c.onPrimaryContainer,
        actionIconContentColor = c.onPrimaryContainer
    )
}

@Composable
fun BestPhotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAccents provides if (darkTheme) DarkAccents else LightAccents) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
