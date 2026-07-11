package com.bestphotoselect.data

import com.bestphotoselect.data.db.PhotoCacheDao
import com.bestphotoselect.data.db.PhotoCacheEntity
import com.bestphotoselect.data.model.AppSettings
import com.bestphotoselect.data.model.FaceMetrics
import com.bestphotoselect.data.model.PhotoAnalysis
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.PhotoItem
import com.bestphotoselect.domain.BestPhotoSelector
import com.bestphotoselect.domain.DHash
import com.bestphotoselect.domain.FaceAnalyzer
import com.bestphotoselect.domain.PhotoGrouper
import com.bestphotoselect.domain.QualityScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tarama hattının tamamı: MediaStore okuma -> dHash -> gruplama -> AI puanlama.
 * Hem etkileşimli tarama (ScanRepository) hem otomatik pilot (AutoPilotWorker)
 * bu sınıfı kullanır. Sonuçlar Room'da önbelleklenir; değişmeyen fotoğraflar
 * sonraki taramalarda yeniden analiz edilmez.
 */
@Singleton
class ScanEngine @Inject constructor(
    private val mediaStore: MediaStoreDataSource,
    private val cacheDao: PhotoCacheDao,
    private val faceAnalyzer: FaceAnalyzer
) {

    sealed interface Progress {
        data object Reading : Progress
        data class Hashing(val done: Int, val total: Int) : Progress
        data object Grouping : Progress
        data class Scoring(val done: Int, val total: Int) : Progress
    }

    suspend fun scan(
        bucketIds: Set<Long>,
        settings: AppSettings,
        onProgress: suspend (Progress) -> Unit = {}
    ): List<PhotoGroup> {
        onProgress(Progress.Reading)
        val photos = mediaStore.queryPhotos(bucketIds)
        if (photos.size < 2) return emptyList()

        val cache = loadCache(photos)

        // 1) Hash aşaması
        val hashes = HashMap<Long, Long>(photos.size)
        val newCacheEntries = ArrayList<PhotoCacheEntity>()
        val hashDone = AtomicInteger(0)
        coroutineScope {
            val semaphore = Semaphore(PARALLELISM)
            photos.map { photo ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val cached = cache[photo.id]
                        val hash = if (cached != null && cached.dateModified == photo.dateModified) {
                            cached.hash
                        } else {
                            val bmp = mediaStore.loadThumbnail(photo.uri, HASH_THUMB_SIZE)
                            val h = bmp?.let { b ->
                                try { DHash.compute(b) } finally { b.recycle() }
                            }
                            if (h != null) {
                                synchronized(newCacheEntries) {
                                    newCacheEntries += PhotoCacheEntity(
                                        mediaId = photo.id,
                                        dateModified = photo.dateModified,
                                        hash = h,
                                        sharpness = null, exposure = null,
                                        faceCount = null, eyesOpen = null,
                                        frontal = null, smile = null, faceAreaRatio = null
                                    )
                                }
                            }
                            h
                        }
                        if (hash != null) synchronized(hashes) { hashes[photo.id] = hash }
                        onProgress(Progress.Hashing(hashDone.incrementAndGet(), photos.size))
                    }
                }
            }.awaitAll()
        }
        persistCache(newCacheEntries)

        // 2) Gruplama
        onProgress(Progress.Grouping)
        val inputs = photos.mapNotNull { p ->
            hashes[p.id]?.let { PhotoGrouper.Input(p.id, p.dateTakenMs, it) }
        }
        val idGroups = PhotoGrouper.group(
            photos = inputs,
            timeWindowMs = settings.timeWindowSec * 1000L,
            maxHammingDistance = settings.hammingThreshold
        )
        if (idGroups.isEmpty()) return emptyList()

        // 3) Puanlama (yalnızca grup üyeleri)
        val photoById = photos.associateBy { it.id }
        val memberIds = idGroups.flatten()
        val scoreCache = loadCache(memberIds.mapNotNull { photoById[it] })
        val analyses = HashMap<Long, PhotoAnalysis>(memberIds.size)
        val scoreDone = AtomicInteger(0)
        val scoredCacheEntries = ArrayList<PhotoCacheEntity>()
        coroutineScope {
            val semaphore = Semaphore(PARALLELISM)
            memberIds.map { id ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val photo = photoById.getValue(id)
                        val hash = hashes.getValue(id)
                        val analysis = analyzeQuality(photo, hash, scoreCache[id])
                        if (analysis != null) {
                            synchronized(analyses) { analyses[id] = analysis }
                            synchronized(scoredCacheEntries) {
                                scoredCacheEntries += analysis.toCacheEntity()
                            }
                        }
                        onProgress(Progress.Scoring(scoreDone.incrementAndGet(), memberIds.size))
                    }
                }
            }.awaitAll()
        }
        persistCache(scoredCacheEntries)

        // 4) En iyiyi seç, grupları kur
        return idGroups.mapIndexedNotNull { index, ids ->
            val members = ids.mapNotNull { analyses[it] }
            if (members.size < 2) null
            else BestPhotoSelector.buildGroup(groupId = index, members = members)
        }.sortedByDescending { it.bytesToFree }
    }

    private suspend fun analyzeQuality(
        photo: PhotoItem,
        hash: Long,
        cached: PhotoCacheEntity?
    ): PhotoAnalysis? {
        if (cached != null &&
            cached.dateModified == photo.dateModified &&
            cached.sharpness != null && cached.exposure != null
        ) {
            val face = cached.faceCount?.takeIf { it > 0 }?.let {
                FaceMetrics(
                    faceCount = it,
                    eyesOpen = cached.eyesOpen ?: 0.7f,
                    frontal = cached.frontal ?: 0.7f,
                    smile = cached.smile ?: 0.5f,
                    faceAreaRatio = cached.faceAreaRatio ?: 0f
                )
            }
            return PhotoAnalysis(photo, hash, cached.sharpness, cached.exposure, face)
        }

        val bitmap = mediaStore.loadThumbnail(photo.uri, SCORE_THUMB_SIZE) ?: return null
        return try {
            val sharpness = QualityScorer.sharpness(bitmap)
            val exposure = QualityScorer.exposure(bitmap)
            val face = faceAnalyzer.analyze(bitmap)
            PhotoAnalysis(photo, hash, sharpness, exposure, face)
        } finally {
            bitmap.recycle()
        }
    }

    private fun PhotoAnalysis.toCacheEntity() = PhotoCacheEntity(
        mediaId = photo.id,
        dateModified = photo.dateModified,
        hash = hash,
        sharpness = sharpness,
        exposure = exposure,
        faceCount = face?.faceCount ?: 0,
        eyesOpen = face?.eyesOpen,
        frontal = face?.frontal,
        smile = face?.smile,
        faceAreaRatio = face?.faceAreaRatio
    )

    private suspend fun loadCache(photos: List<PhotoItem>): Map<Long, PhotoCacheEntity> =
        withContext(Dispatchers.IO) {
            photos.map { it.id }
                .chunked(SQL_CHUNK)
                .flatMap { cacheDao.getByIds(it) }
                .associateBy { it.mediaId }
        }

    private suspend fun persistCache(entries: List<PhotoCacheEntity>) {
        if (entries.isEmpty()) return
        withContext(Dispatchers.IO) {
            entries.chunked(SQL_CHUNK).forEach { cacheDao.upsert(it) }
        }
    }

    companion object {
        private const val PARALLELISM = 4
        private const val HASH_THUMB_SIZE = 256
        private const val SCORE_THUMB_SIZE = 640
        private const val SQL_CHUNK = 500
    }
}
