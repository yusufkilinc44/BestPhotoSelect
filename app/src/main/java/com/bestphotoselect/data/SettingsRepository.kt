package com.bestphotoselect.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bestphotoselect.data.model.AppSettings
import com.bestphotoselect.data.model.AutopilotSchedule
import com.bestphotoselect.data.model.ScoringWeights
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
        val buckets = stringSetPreferencesKey("selected_buckets")

        // "En iyi" seçim ağırlıkları (bkz. ScoringWeights, BestPhotoSelector)
        val wFaceQuality = intPreferencesKey("w_face_quality")
        val wSharpness = intPreferencesKey("w_sharpness")
        val wExposure = intPreferencesKey("w_exposure")
        val wResolution = intPreferencesKey("w_resolution")
        val wEyesOpen = intPreferencesKey("w_eyes_open")
        val wFrontal = intPreferencesKey("w_frontal")
        val wSmile = intPreferencesKey("w_smile")
    }

    private val defaultWeights = ScoringWeights()

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            hammingThreshold = p[Keys.hamming] ?: DEFAULT_HAMMING,
            timeWindowSec = p[Keys.timeWindow] ?: DEFAULT_TIME_WINDOW_SEC,
            trashMode = p[Keys.trashMode] ?: true,
            autopilotEnabled = p[Keys.autopilot] ?: false,
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
                smile = p[Keys.wSmile] ?: defaultWeights.smile
            )
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setHammingThreshold(value: Int) =
        context.dataStore.edit { it[Keys.hamming] = value.coerceIn(2, 20) }

    suspend fun setTimeWindowSec(value: Int) =
        context.dataStore.edit { it[Keys.timeWindow] = value.coerceIn(0, 3600) }

    suspend fun setTrashMode(value: Boolean) =
        context.dataStore.edit { it[Keys.trashMode] = value }

    suspend fun setAutopilotEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.autopilot] = value }

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
        }

    companion object {
        const val DEFAULT_HAMMING = 10
        const val DEFAULT_TIME_WINDOW_SEC = 60
    }
}
