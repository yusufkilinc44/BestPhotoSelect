package com.bestphotoselect.data

import android.app.job.JobScheduler
import android.content.Context
import android.content.SharedPreferences
import com.bestphotoselect.data.db.HistoryDao
import com.bestphotoselect.data.db.HistoryEntity
import com.bestphotoselect.data.model.AutopilotSchedule
import com.bestphotoselect.data.model.ScoringWeights
import com.bestphotoselect.work.AutoPilotScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eski "lite" sürüm (aynı paket adı ve imza) üzerine kurulumda, onun
 * SharedPreferences'ta tuttuğu ayarları, yoksayılan grupları ve silme geçmişini
 * DataStore/Room'a bir kez taşır; ardından eski kayıtları temizler. Lite
 * kurulu olmayan cihazlarda dosyalar boş olduğundan hiçbir şey yapmaz.
 */
@Singleton
class LegacyLiteMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ignoredGroupsRepository: IgnoredGroupsRepository,
    private val historyDao: HistoryDao,
    private val autoPilotScheduler: AutoPilotScheduler
) {
    suspend fun runIfNeeded() = withContext(Dispatchers.IO) {
        val settings = prefs(LITE_SETTINGS)
        val ignored = prefs(LITE_IGNORED)
        val history = prefs(LITE_HISTORY)
        if (settings.all.isEmpty() && ignored.all.isEmpty() && history.all.isEmpty()) return@withContext

        if (settings.all.isNotEmpty()) migrateSettings(settings)
        ignored.getStringSet("signatures", null)?.let { ignoredGroupsRepository.importSignatures(it) }
        history.getString("entries", null)?.let { migrateHistory(it) }

        // Lite'ın JobScheduler tabanlı otomatik pilot işi artık var olmayan bir
        // servise işaret eder; iptal edilir (gerekiyorsa WorkManager'la yeniden kuruldu).
        context.getSystemService(JobScheduler::class.java)?.cancel(LITE_AUTOPILOT_JOB_ID)

        settings.edit().clear().commit()
        ignored.edit().clear().commit()
        history.edit().clear().commit()
    }

    private suspend fun migrateSettings(sp: SharedPreferences) {
        if (sp.contains("hamming")) settingsRepository.setHammingThreshold(sp.getInt("hamming", 10))
        if (sp.contains("time_window")) settingsRepository.setTimeWindowSec(sp.getInt("time_window", 60))
        if (sp.contains("trash_mode")) settingsRepository.setTrashMode(sp.getBoolean("trash_mode", true))
        if (sp.contains("buckets")) {
            settingsRepository.setSelectedBuckets(
                sp.getStringSet("buckets", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
            )
        }
        val d = ScoringWeights()
        settingsRepository.setScoringWeights(
            ScoringWeights(
                faceQuality = sp.getInt("w_face", d.faceQuality),
                sharpness = sp.getInt("w_sharp", d.sharpness),
                exposure = sp.getInt("w_exposure", d.exposure),
                resolution = sp.getInt("w_resolution", d.resolution),
                eyesOpen = sp.getInt("w_eyes", d.eyesOpen),
                frontal = sp.getInt("w_frontal", d.frontal),
                smile = sp.getInt("w_smile", d.smile),
                mouthClosed = sp.getInt("w_mouth", d.mouthClosed)
            )
        )
        val schedule = if (sp.getBoolean("autopilot_daily", true)) AutopilotSchedule.DAILY else AutopilotSchedule.WEEKLY
        settingsRepository.setAutopilotSchedule(schedule)
        if (sp.getBoolean("autopilot", false)) {
            settingsRepository.setAutopilotEnabled(true)
            autoPilotScheduler.schedule(schedule)
        }
    }

    private suspend fun migrateHistory(json: String) {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return
        val entries = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HistoryEntity(
                displayName = o.optString("n"),
                sizeBytes = o.optLong("s"),
                deletedAtMs = o.optLong("t"),
                wasAuto = o.optBoolean("a"),
                trashed = o.optBoolean("tr")
            )
        }
        if (entries.isNotEmpty()) historyDao.insertAll(entries)
    }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private companion object {
        const val LITE_SETTINGS = "settings"
        const val LITE_IGNORED = "ignored_groups"
        const val LITE_HISTORY = "history"
        const val LITE_AUTOPILOT_JOB_ID = 1001
    }
}
