package com.bestphotoselect.lite

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.bestphotoselect.data.model.PhotoItem

object Deleter {
    /**
     * Sistem silme isteği: çöp kutusu modunda 30 gün geri alınabilir taşıma,
     * aksi halde kalıcı silme. MANAGE_MEDIA izni verilmişse sistem onay
     * diyaloğu göstermez.
     */
    fun buildRequest(context: Context, photos: List<PhotoItem>, toTrash: Boolean): PendingIntent {
        val uris = photos.map { it.uri }
        return if (toTrash) {
            MediaStore.createTrashRequest(context.contentResolver, uris, true)
        } else {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        }
    }

    /**
     * MANAGE_MEDIA özel izni verilmişse (Ayarlar > Özel uygulama erişimi > Medya
     * yönetimi), sistemin her silmede gösterdiği onay diyaloğu tamamen atlanabilir.
     */
    fun canDeleteSilently(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context)

    /**
     * MANAGE_MEDIA varsa arka planda sessizce siler ve MediaStore'dan gerçekten
     * kaybolduğunu doğrulayıp [onResult]'ı ana iş parçacığında çağırır; izin yoksa
     * hiçbir şey yapmadan `false` döner (çağıran, eski onaylı akışa düşmelidir).
     */
    fun trySilentDelete(
        context: Context,
        photos: List<PhotoItem>,
        toTrash: Boolean,
        onResult: (Boolean) -> Unit
    ): Boolean {
        if (!canDeleteSilently(context)) return false
        val appContext = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            var success = false
            try {
                buildRequest(appContext, photos, toTrash).send()
                val media = MediaQuery(appContext)
                val ids = photos.map { it.id }
                for (attempt in 1..5) {
                    try {
                        Thread.sleep(1500)
                    } catch (e: InterruptedException) {
                        break
                    }
                    if (media.countStillVisible(ids) == 0) {
                        success = true
                        break
                    }
                }
            } catch (e: Exception) {
                success = false
            }
            main.post { onResult(success) }
        }.start()
        return true
    }
}
