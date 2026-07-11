package com.bestphotoselect.lite

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import com.bestphotoselect.R
import com.bestphotoselect.util.formatBytes
import java.util.concurrent.TimeUnit

/**
 * Otomatik pilot: seçili albümleri periyodik tarar ve her grupta "en iyi"
 * dışındakileri KULLANICI ONAYI OLMADAN temizler.
 *
 * Onaysız silme yalnızca MANAGE_MEDIA özel izniyle mümkündür (Android 12+,
 * Ayarlar > Özel uygulama erişimi > Medya yönetimi). İzin yoksa ya da işlem
 * arka planda doğrulanamazsa kullanıcıya onay bildirimi gösterilir.
 */
class AutoPilotJobService : JobService() {

    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        worker = Thread {
            try {
                run()
            } catch (t: Throwable) {
                // Otomatik pilot hiçbir koşulda uygulamayı düşürmemeli.
            } finally {
                jobFinished(params, false)
            }
        }.also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        worker?.interrupt()
        return true
    }

    private fun run() {
        val prefs = Prefs(this)
        if (!prefs.autopilotEnabled) return
        val buckets = prefs.selectedBuckets
        if (buckets.isEmpty()) return
        if (!hasReadPermission()) return

        val groups = LiteScanEngine(this).scan(buckets, prefs.toAppSettings())
        val toDelete = groups.flatMap { it.deletionCandidates }.map { it.photo }
        if (toDelete.isEmpty()) return

        val canManage = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(this)
        if (!canManage) {
            notifyPending(toDelete.size)
            return
        }

        // MANAGE_MEDIA varken sistem isteği onay diyaloğu göstermez.
        val request = Deleter.buildRequest(this, toDelete, prefs.trashMode)
        try {
            request.send()
        } catch (e: Exception) {
            notifyPending(toDelete.size)
            return
        }

        // İşlemin gerçekleştiğini doğrula.
        val media = MediaQuery(this)
        val ids = toDelete.map { it.id }
        var confirmed = false
        repeat(5) {
            try {
                Thread.sleep(1500)
            } catch (e: InterruptedException) {
                return
            }
            if (media.countStillVisible(ids) == 0) {
                confirmed = true
                return@repeat
            }
        }

        if (confirmed) {
            HistoryStore.addAll(this, toDelete, auto = true, trashed = prefs.trashMode)
            notifyDone(toDelete.size, toDelete.sumOf { it.sizeBytes })
        } else {
            notifyPending(toDelete.size)
        }
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun notifyDone(count: Int, bytes: Long) = notify(
        NOTIF_DONE,
        getString(R.string.notif_autopilot_done_title),
        getString(R.string.notif_autopilot_done_text, count, formatBytes(bytes))
    )

    private fun notifyPending(count: Int) = notify(
        NOTIF_PENDING,
        getString(R.string.notif_autopilot_pending_title),
        getString(R.string.notif_autopilot_pending_text, count)
    )

    private fun notify(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, App.CHANNEL_AUTOPILOT)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    companion object {
        private const val JOB_ID = 1001
        private const val NOTIF_DONE = 100
        private const val NOTIF_PENDING = 101

        fun schedule(context: Context, daily: Boolean) {
            val interval = if (daily) TimeUnit.DAYS.toMillis(1) else TimeUnit.DAYS.toMillis(7)
            val job = JobInfo.Builder(
                JOB_ID, ComponentName(context, AutoPilotJobService::class.java)
            )
                .setPeriodic(interval)
                .setPersisted(true)
                .setRequiresBatteryNotLow(true)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
