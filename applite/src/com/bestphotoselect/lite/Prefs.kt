package com.bestphotoselect.lite

import android.content.Context
import android.content.SharedPreferences
import com.bestphotoselect.data.model.AppSettings
import com.bestphotoselect.data.model.AutopilotSchedule
import com.bestphotoselect.data.model.ScoringWeights

/** SharedPreferences tabanlı ayarlar (lite sürümde DataStore yerine). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var hammingThreshold: Int
        get() = sp.getInt("hamming", 10)
        set(v) = sp.edit().putInt("hamming", v.coerceIn(2, 20)).apply()

    var timeWindowSec: Int
        get() = sp.getInt("time_window", 60)
        set(v) = sp.edit().putInt("time_window", v.coerceIn(0, 3600)).apply()

    var trashMode: Boolean
        get() = sp.getBoolean("trash_mode", true)
        set(v) = sp.edit().putBoolean("trash_mode", v).apply()

    var autopilotEnabled: Boolean
        get() = sp.getBoolean("autopilot", false)
        set(v) = sp.edit().putBoolean("autopilot", v).apply()

    var autopilotDaily: Boolean
        get() = sp.getBoolean("autopilot_daily", true)
        set(v) = sp.edit().putBoolean("autopilot_daily", v).apply()

    var selectedBuckets: Set<Long>
        get() = sp.getStringSet("buckets", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("buckets", v.map(Long::toString).toSet()).apply()

    // ---- "En iyi" seçim ağırlıkları (bkz. ScoringWeights, BestPhotoSelector) ----
    // Ayarlar ekranındaki kaydırıcılarla değiştirilir; toplamları 100 olmak
    // zorunda değildir, BestPhotoSelector her grubu kendi toplamına göre normalize eder.
    private val defaults = ScoringWeights()

    var scoreFaceQuality: Int
        get() = sp.getInt("w_face", defaults.faceQuality)
        set(v) = sp.edit().putInt("w_face", v.coerceIn(0, 100)).apply()

    var scoreSharpness: Int
        get() = sp.getInt("w_sharp", defaults.sharpness)
        set(v) = sp.edit().putInt("w_sharp", v.coerceIn(0, 100)).apply()

    var scoreExposure: Int
        get() = sp.getInt("w_exposure", defaults.exposure)
        set(v) = sp.edit().putInt("w_exposure", v.coerceIn(0, 100)).apply()

    var scoreResolution: Int
        get() = sp.getInt("w_resolution", defaults.resolution)
        set(v) = sp.edit().putInt("w_resolution", v.coerceIn(0, 100)).apply()

    var scoreEyesOpen: Int
        get() = sp.getInt("w_eyes", defaults.eyesOpen)
        set(v) = sp.edit().putInt("w_eyes", v.coerceIn(0, 100)).apply()

    var scoreFrontal: Int
        get() = sp.getInt("w_frontal", defaults.frontal)
        set(v) = sp.edit().putInt("w_frontal", v.coerceIn(0, 100)).apply()

    var scoreSmile: Int
        get() = sp.getInt("w_smile", defaults.smile)
        set(v) = sp.edit().putInt("w_smile", v.coerceIn(0, 100)).apply()

    var scoreMouthClosed: Int
        get() = sp.getInt("w_mouth", defaults.mouthClosed)
        set(v) = sp.edit().putInt("w_mouth", v.coerceIn(0, 100)).apply()

    fun resetScoringWeights() {
        scoreFaceQuality = defaults.faceQuality
        scoreSharpness = defaults.sharpness
        scoreExposure = defaults.exposure
        scoreResolution = defaults.resolution
        scoreEyesOpen = defaults.eyesOpen
        scoreFrontal = defaults.frontal
        scoreSmile = defaults.smile
        scoreMouthClosed = defaults.mouthClosed
    }

    fun toAppSettings() = AppSettings(
        hammingThreshold = hammingThreshold,
        timeWindowSec = timeWindowSec,
        trashMode = trashMode,
        autopilotEnabled = autopilotEnabled,
        autopilotSchedule = if (autopilotDaily) AutopilotSchedule.DAILY else AutopilotSchedule.WEEKLY,
        selectedBucketIds = selectedBuckets,
        scoringWeights = ScoringWeights(
            faceQuality = scoreFaceQuality,
            sharpness = scoreSharpness,
            exposure = scoreExposure,
            resolution = scoreResolution,
            eyesOpen = scoreEyesOpen,
            frontal = scoreFrontal,
            smile = scoreSmile,
            mouthClosed = scoreMouthClosed
        )
    )
}
