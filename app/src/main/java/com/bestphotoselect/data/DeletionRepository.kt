package com.bestphotoselect.data

import android.app.PendingIntent
import com.bestphotoselect.data.db.HistoryDao
import com.bestphotoselect.data.db.HistoryEntity
import com.bestphotoselect.data.model.PhotoItem
import kotlinx.coroutines.delay
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

    /**
     * MANAGE_MEDIA varken sistem isteği onay diyaloğu göstermez; isteği doğrudan
     * gönderip sonucu MediaStore üzerinden doğrular. İzin yoksa ya da işlem
     * doğrulanamazsa `false` döner — çağıran onaylı akışa düşmelidir.
     */
    suspend fun trySilentDelete(photos: List<PhotoItem>, toTrash: Boolean): Boolean {
        if (photos.isEmpty() || !mediaStore.canManageMedia()) return false
        try {
            buildDeleteRequest(photos, toTrash).send()
        } catch (e: PendingIntent.CanceledException) {
            return false
        } catch (e: SecurityException) {
            return false
        }
        val ids = photos.map { it.id }
        repeat(5) {
            delay(1500)
            if (mediaStore.countStillVisible(ids) == 0) return true
        }
        return false
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
