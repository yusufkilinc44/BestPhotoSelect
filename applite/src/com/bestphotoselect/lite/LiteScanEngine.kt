package com.bestphotoselect.lite

import android.content.Context
import com.bestphotoselect.data.model.AppSettings
import com.bestphotoselect.data.model.PhotoAnalysis
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScanPhase
import com.bestphotoselect.domain.BestPhotoSelector
import com.bestphotoselect.domain.DHash
import com.bestphotoselect.domain.PhotoGrouper
import com.bestphotoselect.domain.QualityScorer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tarama hattı (lite): MediaStore -> dHash -> gruplama -> puanlama.
 * Arka plan iş parçacığından çağrılır; ilerleme geri çağrılarla bildirilir.
 */
class LiteScanEngine(context: Context) {
    private val media = MediaQuery(context)
    private val faces = FaceAnalyzerLite(context)

    fun interface ProgressListener {
        fun onProgress(phase: ScanPhase, done: Int, total: Int)
    }

    fun scan(
        bucketIds: Set<Long>,
        settings: AppSettings,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        listener: ProgressListener = ProgressListener { _, _, _ -> }
    ): List<PhotoGroup> {
        listener.onProgress(ScanPhase.READING, 0, 0)
        val photos = media.queryPhotos(bucketIds)
        if (photos.size < 2 || cancelled.get()) return emptyList()

        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            // 1) Hash
            val hashes = ConcurrentHashMap<Long, Long>()
            val hashDone = AtomicInteger(0)
            photos.map { photo ->
                pool.submit {
                    if (!cancelled.get()) {
                        media.loadThumb(photo.uri, HASH_THUMB)?.let { bmp ->
                            try {
                                hashes[photo.id] = DHash.compute(bmp)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                    listener.onProgress(ScanPhase.HASHING, hashDone.incrementAndGet(), photos.size)
                }
            }.forEach { it.get() }
            if (cancelled.get()) return emptyList()

            // 2) Gruplama
            listener.onProgress(ScanPhase.GROUPING, 0, 0)
            val inputs = photos.mapNotNull { p ->
                hashes[p.id]?.let { PhotoGrouper.Input(p.id, p.dateTakenMs, it) }
            }
            val idGroups = PhotoGrouper.group(
                photos = inputs,
                timeWindowMs = settings.timeWindowSec * 1000L,
                maxHammingDistance = settings.hammingThreshold
            )
            if (idGroups.isEmpty() || cancelled.get()) return emptyList()

            // 3) Puanlama (yalnızca grup üyeleri)
            val photoById = photos.associateBy { it.id }
            val memberIds = idGroups.flatten()
            val analyses = ConcurrentHashMap<Long, PhotoAnalysis>()
            val scoreDone = AtomicInteger(0)
            memberIds.map { id ->
                pool.submit {
                    if (!cancelled.get()) {
                        val photo = photoById.getValue(id)
                        media.loadThumb(photo.uri, SCORE_THUMB)?.let { bmp ->
                            try {
                                analyses[id] = PhotoAnalysis(
                                    photo = photo,
                                    hash = hashes.getValue(id),
                                    sharpness = QualityScorer.sharpness(bmp),
                                    exposure = QualityScorer.exposure(bmp),
                                    face = try {
                                        faces.analyze(bmp)
                                    } catch (t: Throwable) {
                                        null
                                    }
                                )
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                    listener.onProgress(ScanPhase.SCORING, scoreDone.incrementAndGet(), memberIds.size)
                }
            }.forEach { it.get() }
            if (cancelled.get()) return emptyList()

            // 4) En iyiyi seç
            return idGroups.mapIndexedNotNull { index, ids ->
                val members = ids.mapNotNull { analyses[it] }
                if (members.size < 2) null
                else BestPhotoSelector.buildGroup(groupId = index, members = members)
            }.sortedByDescending { it.bytesToFree }
        } finally {
            pool.shutdown()
        }
    }

    companion object {
        private const val THREADS = 3
        private const val HASH_THUMB = 256
        private const val SCORE_THUMB = 640
    }
}
