package com.bestphotoselect.work

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bestphotoselect.BestPhotoApp
import com.bestphotoselect.MainActivity
import com.bestphotoselect.R
import com.bestphotoselect.data.DeletionRepository
import com.bestphotoselect.data.ScanEngine
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.util.formatBytes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Otomatik pilot: seçili albümleri arka planda tarar ve her benzer grupta
 * "en iyi" dışındakileri KULLANICI ONAYI OLMADAN temizler.
 *
 * Onaysız silme Android'de yalnızca MANAGE_MEDIA özel izniyle mümkündür
 * (Ayarlar > Özel uygulama erişimi > Medya yönetimi). İzin yoksa ya da sistem
 * işlemi arka planda tamamlamazsa, kullanıcıya sonuçları onaylaması için
 * bildirim gösterilir — fotoğraflar asla sessizce kaybolmaz ama izin varken
 * de onay istenmez.
 */
@HiltWorker
class AutoPilotWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scanEngine: ScanEngine,
    private val settingsRepository: SettingsRepository,
    private val deletionRepository: DeletionRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.autopilotEnabled) return Result.success()
        if (settings.selectedBucketIds.isEmpty()) return Result.success()
        if (!hasReadPermission()) return Result.success()

        val groups = try {
            scanEngine.scan(settings.selectedBucketIds, settings)
        } catch (e: Exception) {
            return Result.retry()
        }

        val toDelete = groups.flatMap { it.deletionCandidates }.map { it.photo }
        if (toDelete.isEmpty()) return Result.success()

        val deleted = deletionRepository.trySilentDelete(toDelete, settings.trashMode)
        return if (deleted) {
            deletionRepository.recordDeletion(toDelete, wasAuto = true, trashed = settings.trashMode)
            notifyDone(toDelete.size, toDelete.sumOf { it.sizeBytes })
            Result.success()
        } else {
            notifyPendingApproval(toDelete.size)
            Result.success()
        }
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return applicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun notifyDone(count: Int, bytes: Long) {
        notify(
            NOTIF_ID_DONE,
            applicationContext.getString(R.string.notif_autopilot_done_title),
            applicationContext.getString(
                R.string.notif_autopilot_done_text, count, formatBytes(bytes)
            )
        )
    }

    private fun notifyPendingApproval(count: Int) {
        notify(
            NOTIF_ID_PENDING,
            applicationContext.getString(R.string.notif_autopilot_pending_title),
            applicationContext.getString(R.string.notif_autopilot_pending_text, count)
        )
    }

    private fun notify(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, BestPhotoApp.CHANNEL_AUTOPILOT)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(id, notification)
    }

    companion object {
        private const val NOTIF_ID_DONE = 100
        private const val NOTIF_ID_PENDING = 101
    }
}
