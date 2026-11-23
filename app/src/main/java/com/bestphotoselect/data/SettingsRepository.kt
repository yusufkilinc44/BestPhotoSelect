package com.bestphotoselect.data

import androidx.datastore.preferences.core.longPreferencesKey
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bestphotoselect.data.model.AppSettings
import com.bestphotoselect.data.model.AutoScanMode
import com.bestphotoselect.data.model.AutopilotSchedule
import com.bestphotoselect.data.model.ScoringWeights
import com.bestphotoselect.data.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val hamming = intPreferencesKey("hamming_threshold")
        val timeWindow = intPreferencesKey("time_window_sec")
        val trashMode = booleanPreferencesKey("trash_mode")
        val autopilot = booleanPreferencesKey("autopilot_enabled")
        val schedule = stringPreferencesKey("autopilot_schedule")
        val autoScanMode = stringPreferencesKey("auto_scan_mode")
        val buckets = stringSetPreferencesKey("selected_buckets")
        val manageMediaPrompted = booleanPreferencesKey("manage_media_prompted")
        val timeWindowDefaultV2 = booleanPreferencesKey("time_window_default_v2")
        val albumsAsList = booleanPreferencesKey("albums_as_list")
        val includeWhatsApp = booleanPreferencesKey("include_whatsapp")
        val themeMode = stringPreferencesKey("theme_mode")
        val seenTips = stringSetPreferencesKey("seen_tips")
        val tipsLastShownAt = longPreferencesKey("tips_last_shown_at")

        // "En iyi" seçim ağırlıkları (bkz. ScoringWeights, BestPhotoSelector)
        val wFaceQuality = intPreferencesKey("w_face_quality")
        val wSharpness = intPreferencesKey("w_sharpness")
        val wExposure = intPreferencesKey("w_exposure")
        val wResolution = intPreferencesKey("w_resolution")
        val wEyesOpen = intPreferencesKey("w_eyes_open")
        val wFrontal = intPreferencesKey("w_frontal")
        val wSmile = intPreferencesKey("w_smile")
        val wMouthClosed = intPreferencesKey("w_mouth_closed")
        val wTogetherness = intPreferencesKey("w_togetherness")
        val wComposition = intPreferencesKey("w_composition")
    }

    private val defaultWeights = ScoringWeights()

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            hammingThreshold = p[Keys.hamming] ?: DEFAULT_HAMMING,
            timeWindowSec = p[Keys.timeWindow] ?: DEFAULT_TIME_WINDOW_SEC,
            trashMode = p[Keys.trashMode] ?: true,
            // Eski tek anahtarlı otomatik pilot (açıksa tarayıp siliyordu) yeni
            // moda karşılık gelecek şekilde okunur.
            autoScanMode = p[Keys.autoScanMode]
                ?.let { runCatching { AutoScanMode.valueOf(it) }.getOrNull() }
                ?: if (p[Keys.autopilot] == true) AutoScanMode.AUTO_DELETE else AutoScanMode.OFF,
            autopilotSchedule = p[Keys.schedule]
                ?.let { runCatching { AutopilotSchedule.valueOf(it) }.getOrNull() }
                ?: AutopilotSchedule.DAILY,
            selectedBucketIds = p[Keys.buckets]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
            scoringWeights = ScoringWeights(
                faceQuality = p[Keys.wFaceQuality] ?: defaultWeights.faceQuality,
                sharpness = p[Keys.wSharpness] ?: defaultWeights.sharpness,
                exposure = p[Keys.wExposure] ?: defaultWeights.exposure,
                resolution = p[Keys.wResolution] ?: defaultWeights.resolution,
                eyesOpen = p[Keys.wEyesOpen] ?: defaultWeights.eyesOpen,
                frontal = p[Keys.wFrontal] ?: defaultWeights.frontal,
                smile = p[Keys.wSmile] ?: defaultWeights.smile,
                mouthClosed = p[Keys.wMouthClosed] ?: defaultWeights.mouthClosed,
                togetherness = p[Keys.wTogetherness] ?: defaultWeights.togetherness,
                composition = p[Keys.wComposition] ?: defaultWeights.composition
            )
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** "Medya yönetimi" izni kurulumdan sonra yalnızca bir kez sorulur. */
    val manageMediaPrompted: Flow<Boolean> = context.dataStore.data.map { it[Keys.manageMediaPrompted] ?: false }

    /** Albümler ekranı: true = liste, false = ızgara (varsayılan). */
    val albumsAsList: Flow<Boolean> = context.dataStore.data.map { it[Keys.albumsAsList] ?: false }

    /** Gösterilmiş ipuçlarının kimlikleri ve son ipucu gösterim zamanı (0 = hiç). */
    val seenTips: Flow<Set<String>> = context.dataStore.data.map { it[Keys.seenTips] ?: emptySet() }
    val tipsLastShownAt: Flow<Long> = context.dataStore.data.map { it[Keys.tipsLastShownAt] ?: 0L }

    suspend fun markTipShown(id: String, now: Long) =
        context.dataStore.edit {
            it[Keys.seenTips] = (it[Keys.seenTips] ?: emptySet()) + id
            it[Keys.tipsLastShownAt] = now
        }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.themeMode] = mode.name }

    /** WhatsApp klasörleri albümler listesinde gösterilsin mi (varsayılan: evet). */
    val includeWhatsApp: Flow<Boolean> = context.dataStore.data.map { it[Keys.includeWhatsApp] ?: true }

    suspend fun setIncludeWhatsApp(value: Boolean) =
        context.dataStore.edit { it[Keys.includeWhatsApp] = value }

    suspend fun setAlbumsAsList(value: Boolean) =
        context.dataStore.edit { it[Keys.albumsAsList] = value }

    /**
     * Varsayılan zaman penceresi 60 sn'den 3 dk'ya çıktı. Kayıtlı değer tam eski
     * varsayılansa (kullanıcı özellikle başka bir değer seçmemişse) bir kez yeni
     * varsayılana yükseltilir; başka değerlere dokunulmaz.
     */
    suspend fun applyTimeWindowDefaultV2() =
        context.dataStore.edit { p ->
            if (p[Keys.timeWindowDefaultV2] == true) return@edit
            if (p[Keys.timeWindow] == OLD_DEFAULT_TIME_WINDOW_SEC) p[Keys.timeWindow] = DEFAULT_TIME_WINDOW_SEC
            p[Keys.timeWindowDefaultV2] = true
        }

    /**
     * Ayarları ve görünüm tercihlerini (tema, ızgara/liste, WhatsApp klasörleri)
     * varsayılanlara döndürür. Seçili albümler, yoksayılan gruplar, görülen
     * ipuçları ve dil (sistemde saklanır) korunur.
     */
    suspend fun resetToDefaults() =
        context.dataStore.edit { p ->
            listOf(
                Keys.hamming, Keys.timeWindow, Keys.trashMode, Keys.autopilot, Keys.schedule,
                Keys.autoScanMode, Keys.albumsAsList, Keys.includeWhatsApp, Keys.themeMode,
                Keys.wFaceQuality, Keys.wSharpness, Keys.wExposure, Keys.wResolution, Keys.wEyesOpen,
                Keys.wFrontal, Keys.wSmile, Keys.wMouthClosed, Keys.wTogetherness, Keys.wComposition
            ).forEach { p.remove(it) }
            p[Keys.timeWindowDefaultV2] = true
        }

    suspend fun setManageMediaPrompted() =
        context.dataStore.edit { it[Keys.manageMediaPrompted] = true }

    suspend fun setHammingThreshold(value: Int) =
        context.dataStore.edit { it[Keys.hamming] = value.coerceIn(2, 20) }

    suspend fun setTimeWindowSec(value: Int) =
        context.dataStore.edit { it[Keys.timeWindow] = value.coerceIn(0, 3600) }

    suspend fun setTrashMode(value: Boolean) =
        context.dataStore.edit { it[Keys.trashMode] = value }

    suspend fun setAutoScanMode(value: AutoScanMode) =
        context.dataStore.edit { it[Keys.autoScanMode] = value.name }

    suspend fun setAutopilotSchedule(value: AutopilotSchedule) =
        context.dataStore.edit { it[Keys.schedule] = value.name }

    suspend fun setSelectedBuckets(bucketIds: Set<Long>) =
        context.dataStore.edit { it[Keys.buckets] = bucketIds.map(Long::toString).toSet() }

    suspend fun setScoringWeights(weights: ScoringWeights) =
        context.dataStore.edit { p ->
            p[Keys.wFaceQuality] = weights.faceQuality.coerceIn(0, 100)
            p[Keys.wSharpness] = weights.sharpness.coerceIn(0, 100)
            p[Keys.wExposure] = weights.exposure.coerceIn(0, 100)
            p[Keys.wResolution] = weights.resolution.coerceIn(0, 100)
            p[Keys.wEyesOpen] = weights.eyesOpen.coerceIn(0, 100)
            p[Keys.wFrontal] = weights.frontal.coerceIn(0, 100)
            p[Keys.wSmile] = weights.smile.coerceIn(0, 100)
            p[Keys.wMouthClosed] = weights.mouthClosed.coerceIn(0, 100)
            p[Keys.wTogetherness] = weights.togetherness.coerceIn(0, 100)
            p[Keys.wComposition] = weights.composition.coerceIn(0, 100)
        }

    companion object {
        const val DEFAULT_HAMMING = 10
        const val DEFAULT_TIME_WINDOW_SEC = 180
        private const val OLD_DEFAULT_TIME_WINDOW_SEC = 60
    }
}
