package com.bestphotoselect.lite

import android.app.PendingIntent
import android.content.Context
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
}
