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
import com.bestphotoselect.domain.ScreenshotMatcher
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
    private val faceAnalyzer: FaceAnalyzer,
    private val ignoredGroupsRepository: IgnoredGroupsRepository
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

        // 1) Hash + kaba yüz sayımı aşaması. dHash arka plan hakim sahnelerde
        // farklı kişi sayısını ayırt edemeyebildiğinden, yüz sayısı gruplama
        // aşamasında ek bir "gerçekten benzer mi" kontrolü sağlar (bkz.
        // PhotoGrouper.faceCountsCompatible). İkisi de aynı küçük resimden
        // hesaplanır; tek fotoğrafın hatası taramayı düşürmemeli.
        val hashes = HashMap<Long, Long>(photos.size)
        val faceCounts = HashMap<Long, Int>(photos.size)
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
                                try {
                                    val computed = DHash.compute(b)
                                    // Ekran görüntülerinde yüz sayısı anlamsız; analiz atlanır.
                                    val count = if (photo.isScreenshot) -1 else {
                                        try { faceAnalyzer.countFaces(b) } catch (t: Throwable) { -1 }
                                    }
                                    synchronized(faceCounts) { faceCounts[photo.id] = count }
                                    computed
                                } catch (t: Throwable) {
                                    null
                                } finally {
                                    b.recycle()
                                }
                            }
                            if (h != null) {
                                synchronized(newCacheEntries) {
                                    newCacheEntries += PhotoCacheEntity(
                                        mediaId = photo.id,
                                        dateModified = photo.dateModified,
                                        hash = h,
                                        sharpness = null, exposure = null,
                                        faceCount = null, eyesOpen = null,
                                        frontal = null, smile = null, mouthClosed = null,
                                        faceAreaRatio = null
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

        // 2) Gruplama — ekran görüntüleri normal fotoğraflarla asla karışmaz;
        // ayrı ve çok daha sıkı kurallarla gruplanır (bkz. groupScreenshots).
        onProgress(Progress.Grouping)
        val photoById = photos.associateBy { it.id }
        val inputs = photos.mapNotNull { p ->
            hashes[p.id]?.let { PhotoGrouper.Input(p.id, p.dateTakenMs, it, faceCounts[p.id] ?: -1) }
        }
        val (screenshotInputs, photoInputs) = inputs.partition { photoById.getValue(it.id).isScreenshot }
        val idGroups = PhotoGrouper.group(
            photos = photoInputs,
            timeWindowMs = settings.timeWindowSec * 1000L,
            maxHammingDistance = settings.hammingThreshold
        )
        val screenshotIdGroups = groupScreenshots(screenshotInputs, photoById, settings.timeWindowSec * 1000L)
        if (idGroups.isEmpty() && screenshotIdGroups.isEmpty()) return emptyList()

        // 3) Puanlama (yalnızca normal fotoğraf grubu üyeleri)
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
                        // Tek fotoğrafın hatası taramayı düşürmemeli
                        val analysis = try {
                            analyzeQuality(photo, hash, scoreCache[id])
                        } catch (t: Throwable) {
                            null
                        }
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

        // 4) En iyiyi seç, grupları kur — kullanıcının kalıcı olarak yoksaydığı
        // (bkz. IgnoredGroupsRepository) gruplar sonuçlara asla girmez.
        val ignoredSignatures = ignoredGroupsRepository.ignoredSignatures()
        val photoGroups = idGroups.mapIndexedNotNull { index, ids ->
            val members = ids.mapNotNull { analyses[it] }
            if (members.size < 2) return@mapIndexedNotNull null
            val memberIds = members.map { it.photo.id }
            if (ignoredGroupsRepository.isIgnored(memberIds, ignoredSignatures)) return@mapIndexedNotNull null
            BestPhotoSelector.buildGroup(groupId = index, members = members, weights = settings.scoringWeights)
        }
        // Grup kimlikleri tekil kalsın diye ekran görüntüsü grupları normal grupların ardından numaralanır.
        val screenshotGroups = screenshotIdGroups.mapIndexedNotNull { index, ids ->
            if (ignoredGroupsRepository.isIgnored(ids, ignoredSignatures)) return@mapIndexedNotNull null
            val members = ids.map { id ->
                PhotoAnalysis(photoById.getValue(id), hashes.getValue(id), sharpness = 0.0, exposure = 0f, face = null)
            }
            BestPhotoSelector.buildNewestKeptGroup(groupId = idGroups.size + index, members = members)
        }
        return (photoGroups + screenshotGroups).sortedByDescending { it.bytesToFree }
    }

    /**
     * Ekran görüntüleri: önce sıkı bir dHash eşiğiyle aday çiftler bulunur, sonra
     * her aday çift yüksek çözünürlüklü piksel karşılaştırmasıyla doğrulanır
     * (bkz. ScreenshotMatcher). Yalnızca neredeyse birebir aynı kopyalar gruplanır.
     */
    private suspend fun groupScreenshots(
        inputs: List<PhotoGrouper.Input>,
        photoById: Map<Long, PhotoItem>,
        timeWindowMs: Long
    ): List<List<Long>> {
        if (inputs.size < 2) return emptyList()
        val candidates = PhotoGrouper.group(inputs, timeWindowMs, SCREENSHOT_MAX_HAMMING).flatten().toSet()
        if (candidates.isEmpty()) return emptyList()

        val signatures = HashMap<Long, ScreenshotMatcher.Signature>(candidates.size)
        coroutineScope {
            val semaphore = Semaphore(PARALLELISM)
            candidates.map { id ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val bmp = mediaStore.loadThumbnail(photoById.getValue(id).uri, SCREENSHOT_THUMB_SIZE)
                            ?: return@withPermit
                        try {
                            val sig = ScreenshotMatcher.signature(bmp)
                            synchronized(signatures) { signatures[id] = sig }
                        } catch (t: Throwable) {
                            // İmzası çıkarılamayan kare hiçbir grupla eşleşmez.
                        } finally {
                            bmp.recycle()
                        }
                    }
                }
            }.awaitAll()
        }

        return PhotoGrouper.group(
            photos = inputs.filter { it.id in candidates },
            timeWindowMs = timeWindowMs,
            maxHammingDistance = SCREENSHOT_MAX_HAMMING
        ) { a, b ->
            val sa = signatures[a]
            val sb = signatures[b]
            sa != null && sb != null && ScreenshotMatcher.isNearDuplicate(sa, sb)
        }
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
                    mouthClosed = cached.mouthClosed ?: 0.7f,
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
        mouthClosed = face?.mouthClosed,
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
        // 256px yüz sayımı için çoğu grup fotoğrafında yetersizdi; 384 dHash'i
        // etkilemez (o zaten kendi içinde 9x8'e küçültür) ama yüz tespiti
        // güvenilirliğini belirgin artırır.
        private const val HASH_THUMB_SIZE = 384
        private const val SCORE_THUMB_SIZE = 640
        private const val SQL_CHUNK = 500
        // Ekran görüntüleri için ön eleme eşiği (64 bit üzerinden); asıl karar
        // ScreenshotMatcher'ın piksel karşılaştırmasındadır.
        private const val SCREENSHOT_MAX_HAMMING = 4
        private const val SCREENSHOT_THUMB_SIZE = 512
    }
}
