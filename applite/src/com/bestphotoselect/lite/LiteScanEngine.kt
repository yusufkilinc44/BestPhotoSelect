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
    private val appContext = context.applicationContext
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
            // 1) Hash + kaba yüz sayımı — tek fotoğrafın hatası taramayı düşürmemeli.
            // İkisi de aynı küçük resimden hesaplanır (tek yükleme): dHash arka plan
            // hakim sahnelerde farklı kişi sayısını ayırt edemeyebildiğinden, yüz
            // sayısı gruplama aşamasında ek bir "gerçekten benzer mi" kontrolü sağlar.
            val hashes = ConcurrentHashMap<Long, Long>()
            val faceCounts = ConcurrentHashMap<Long, Int>()
            val hashDone = AtomicInteger(0)
            photos.map { photo ->
                pool.submit {
                    try {
                        if (!cancelled.get()) {
                            media.loadThumb(photo.uri, HASH_THUMB)?.let { bmp ->
                                try {
                                    hashes[photo.id] = DHash.compute(bmp)
                                    faceCounts[photo.id] = try {
                                        faces.countFaces(bmp)
                                    } catch (t: Throwable) {
                                        -1
                                    }
                                } finally {
                                    bmp.recycle()
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        // bozuk/erişilemeyen fotoğraf: atla
                    } finally {
                        listener.onProgress(ScanPhase.HASHING, hashDone.incrementAndGet(), photos.size)
                    }
                }
            }.forEach { awaitQuietly(it) }
            if (cancelled.get()) return emptyList()

            // 2) Gruplama
            listener.onProgress(ScanPhase.GROUPING, 0, 0)
            val inputs = photos.mapNotNull { p ->
                hashes[p.id]?.let {
                    PhotoGrouper.Input(p.id, p.dateTakenMs, it, faceCounts[p.id] ?: -1)
                }
            }
            val rawGroups = PhotoGrouper.group(
                photos = inputs,
                timeWindowMs = settings.timeWindowSec * 1000L,
                maxHammingDistance = settings.hammingThreshold
            )
            // Kullanıcının daha önce "bir daha gösterme" dediği gruplar (aynı
            // fotoğraf kümesi) sonuçlardan tamamen çıkarılır.
            val idGroups = rawGroups.filterNot { ids -> IgnoredGroupsStore.isIgnored(appContext, ids) }
            if (idGroups.isEmpty() || cancelled.get()) return emptyList()

            // 3) Puanlama (yalnızca grup üyeleri)
            val photoById = photos.associateBy { it.id }
            val memberIds = idGroups.flatten()
            val analyses = ConcurrentHashMap<Long, PhotoAnalysis>()
            val scoreDone = AtomicInteger(0)
            memberIds.map { id ->
                pool.submit {
                    try {
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
                    } catch (t: Throwable) {
                        // bozuk/erişilemeyen fotoğraf: atla
                    } finally {
                        listener.onProgress(ScanPhase.SCORING, scoreDone.incrementAndGet(), memberIds.size)
                    }
                }
            }.forEach { awaitQuietly(it) }
            if (cancelled.get()) return emptyList()

            // 4) En iyiyi seç
            return idGroups.mapIndexedNotNull { index, ids ->
                val members = ids.mapNotNull { analyses[it] }
                if (members.size < 2) null
                else BestPhotoSelector.buildGroup(groupId = index, members = members, weights = settings.scoringWeights)
            }.sortedByDescending { it.bytesToFree }
        } finally {
            pool.shutdown()
        }
    }

    private fun awaitQuietly(future: java.util.concurrent.Future<*>) {
        try {
            future.get()
        } catch (e: Exception) {
            // görev içi hatalar zaten görev bazında yutuluyor; bekleme hatası taramayı bozmasın
        }
    }

    companion object {
        private const val THREADS = 3
        // 256px yüz sayımı için çoğu grup fotoğrafında yetersizdi (yüzler çok
        // küçük kalıyordu); 384'e çıkarmak dHash'i etkilemez (o zaten kendi
        // içinde 9x8'e küçültür) ama yüz tespiti güvenilirliğini belirgin artırır.
        private const val HASH_THUMB = 384
        private const val SCORE_THUMB = 640
    }
}
