package com.bestphotoselect.lite

import com.bestphotoselect.data.model.PhotoGroup

/** Etkileşimli tarama oturumu: sonuçlar süreç yaşadıkça bellekte tutulur. */
object ScanSession {
    @Volatile
    var groups: List<PhotoGroup> = emptyList()

    fun group(groupId: Int): PhotoGroup? = groups.firstOrNull { it.id == groupId }

    @Synchronized
    fun toggleDeletion(groupId: Int, photoId: Long) {
        groups = groups.map { g ->
            if (g.id != groupId) g
            else g.copy(photos = g.photos.map { sp ->
                if (sp.photo.id == photoId && photoId != g.bestPhotoId) {
                    sp.copy(markedForDeletion = !sp.markedForDeletion)
                } else sp
            })
        }
    }

    @Synchronized
    fun setBest(groupId: Int, photoId: Long) {
        groups = groups.map { g ->
            if (g.id != groupId) g
            else g.copy(
                bestPhotoId = photoId,
                photos = g.photos.map { sp -> sp.copy(markedForDeletion = sp.photo.id != photoId) }
            )
        }
    }

    @Synchronized
    fun skipGroup(groupId: Int) {
        groups = groups.filterNot { it.id == groupId }
    }

    @Synchronized
    fun onPhotosDeleted(deletedIds: Set<Long>) {
        groups = groups.mapNotNull { g ->
            val remaining = g.photos.filterNot { it.photo.id in deletedIds }
            when {
                remaining.size == g.photos.size -> g
                remaining.size < 2 -> null
                else -> {
                    val best = if (remaining.any { it.photo.id == g.bestPhotoId }) g.bestPhotoId
                    else remaining.maxByOrNull { it.score }!!.photo.id
                    g.copy(photos = remaining, bestPhotoId = best)
                }
            }
        }
    }
}
