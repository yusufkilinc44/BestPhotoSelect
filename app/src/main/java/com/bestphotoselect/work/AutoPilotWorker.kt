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
import com.bestphotoselect.data.ScanRepository
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.AutoScanMode
import com.bestphotoselect.util.formatBytes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Otomatik tarama: seçili albümleri arka planda belirlenen aralıkla tarar ve
 * sonucu her seferinde bildirir (kaç benzer grup, kaç fotoğraf, kaç MB).
 *
 * - [AutoScanMode.NOTIFY_ONLY]: hiçbir şey silinmez; bulunanlar bildirilir ve
 *   bildirime dokununca uygulama sonuç ekranında açılır.
 * - [AutoScanMode.AUTO_DELETE]: her grupta "en iyi" dışındakiler KULLANICI ONAYI
 *   OLMADAN temizlenir; silinen fotoğraf sayısı ve boşalan alan bildirilir.
 *
 * Onaysız silme Android'de yalnızca MANAGE_MEDIA özel izniyle mümkündür
 * (Ayarlar > Özel uygulama erişimi > Medya yönetimi). İzin yoksa ya da sistem
 * işlemi arka planda tamamlamazsa, kullanıcıya onay için bildirim gösterilir —
 * fotoğraflar asla sessizce kaybolmaz ama izin varken de onay istenmez.
 */
@HiltWorker
class AutoPilotWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scanEngine: ScanEngine,
    private val scanRepository: ScanRepository,
    private val settingsRepository: SettingsRepository,
    private val deletionRepository: DeletionRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (settings.autoScanMode == AutoScanMode.OFF) return Result.success()
        if (settings.selectedBucketIds.isEmpty()) return Result.success()
        if (!hasReadPermission()) return Result.success()

        val groups = try {
            scanEngine.scan(settings.selectedBucketIds, settings)
        } catch (e: Exception) {
            return Result.retry()
        }

        val toDelete = groups.flatMap { it.deletionCandidates }.map { it.photo }
        val bytes = toDelete.sumOf { it.sizeBytes }
        val groupCount = groups.count { it.deletionCandidates.isNotEmpty() }
        if (toDelete.isEmpty()) {
            notifyNothingFound()
            return Result.success()
        }

        if (settings.autoScanMode == AutoScanMode.NOTIFY_ONLY) {
            scanRepository.publishBackgroundResults(groups, settings.selectedBucketIds)
            notifyFound(groupCount, toDelete.size, bytes)
            return Result.success()
        }

        val deleted = deletionRepository.trySilentDelete(toDelete, settings.trashMode)
        if (deleted) {
            deletionRepository.recordDeletion(toDelete, wasAuto = true, trashed = settings.trashMode)
            notifyDeleted(groupCount, toDelete.size, bytes)
        } else {
            scanRepository.publishBackgroundResults(groups, settings.selectedBucketIds)
            notifyPendingApproval(groupCount, toDelete.size, bytes)
        }
        return Result.success()
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return applicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun notifyNothingFound() = notify(
        NOTIF_ID_RESULT,
        str(R.string.notif_autoscan_title),
        str(R.string.notif_autoscan_none_text),
        openResults = false
    )

    private fun notifyFound(groups: Int, photos: Int, bytes: Long) = notify(
        NOTIF_ID_RESULT,
        str(R.string.notif_autoscan_title),
        str(R.string.notif_autoscan_found_text, groups, photos, formatBytes(bytes)),
        openResults = true
    )

    private fun notifyDeleted(groups: Int, photos: Int, bytes: Long) = notify(
        NOTIF_ID_RESULT,
        str(R.string.notif_autopilot_done_title),
        str(R.string.notif_autopilot_done_text, groups, photos, formatBytes(bytes)),
        openResults = false
    )

    private fun notifyPendingApproval(groups: Int, photos: Int, bytes: Long) = notify(
        NOTIF_ID_PENDING,
        str(R.string.notif_autopilot_pending_title),
        str(R.string.notif_autopilot_pending_text, groups, photos, formatBytes(bytes)),
        openResults = true
    )

    private fun str(id: Int, vararg args: Any): String = applicationContext.getString(id, *args)

    private fun notify(id: Int, title: String, text: String, openResults: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_RESULTS, openResults)
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, BestPhotoApp.CHANNEL_AUTOPILOT)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(id, notification)
    }

    companion object {
        private const val NOTIF_ID_RESULT = 100
        private const val NOTIF_ID_PENDING = 101
    }
}
