package com.bestphotoselect.lite

import android.content.Context
import android.content.SharedPreferences
import com.bestphotoselect.data.model.AppSettings
import com.bestphotoselect.data.model.AutopilotSchedule

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

    fun toAppSettings() = AppSettings(
        hammingThreshold = hammingThreshold,
        timeWindowSec = timeWindowSec,
        trashMode = trashMode,
        autopilotEnabled = autopilotEnabled,
        autopilotSchedule = if (autopilotDaily) AutopilotSchedule.DAILY else AutopilotSchedule.WEEKLY,
        selectedBucketIds = selectedBuckets
    )
}
