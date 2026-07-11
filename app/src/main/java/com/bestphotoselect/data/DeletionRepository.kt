package com.bestphotoselect.data

import android.app.PendingIntent
import com.bestphotoselect.data.db.HistoryDao
import com.bestphotoselect.data.db.HistoryEntity
import com.bestphotoselect.data.model.PhotoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletionRepository @Inject constructor(
    private val mediaStore: MediaStoreDataSource,
    private val historyDao: HistoryDao
) {
    /**
     * Onaylı silme için sistem isteği üretir. [toTrash] açıkken fotoğraflar 30 gün
     * geri alınabilir şekilde çöp kutusuna taşınır, kapalıyken kalıcı silinir.
     */
    fun buildDeleteRequest(photos: List<PhotoItem>, toTrash: Boolean): PendingIntent {
        val uris = photos.map { it.uri }
        return if (toTrash) mediaStore.createTrashRequest(uris)
        else mediaStore.createDeleteRequest(uris)
    }

    suspend fun recordDeletion(photos: List<PhotoItem>, wasAuto: Boolean, trashed: Boolean) {
        if (photos.isEmpty()) return
        val now = System.currentTimeMillis()
        historyDao.insertAll(photos.map {
            HistoryEntity(
                displayName = it.displayName,
                sizeBytes = it.sizeBytes,
                deletedAtMs = now,
                wasAuto = wasAuto,
                trashed = trashed
            )
        })
    }
}
