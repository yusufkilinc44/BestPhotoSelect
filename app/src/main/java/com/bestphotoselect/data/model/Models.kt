package com.bestphotoselect.data.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Album(
    val bucketId: Long,
    val name: String,
    val photoCount: Int,
    val coverUri: Uri,
    val totalBytes: Long
)

data class PhotoItem(
    val id: Long,
    val bucketId: Long,
    val dateTakenMs: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val displayName: String
) {
    /** Türetilmiş alan: kurucu Android tipi almaz, saf JVM testleri kolaylaşır. */
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
}

/** ML Kit yüz analizinden türetilen, 0..1 aralığına indirgenmiş metrikler. */
data class FaceMetrics(
    val faceCount: Int,
    val eyesOpen: Float,
    val frontal: Float,
    val smile: Float,
    /** 1 = ağız doğal/kapalı duruyor, 0 = konuşma anı gibi tuhaf/geniş açık. */
    val mouthClosed: Float,
    val faceAreaRatio: Float
)

/** Bir fotoğrafın tüm analiz sonuçları. */
data class PhotoAnalysis(
    val photo: PhotoItem,
    val hash: Long,
    val sharpness: Double,
    val exposure: Float,
    val face: FaceMetrics?
)

data class ScoredPhoto(
    val analysis: PhotoAnalysis,
    val score: Float,
    val markedForDeletion: Boolean
) {
    val photo: PhotoItem get() = analysis.photo
}

data class PhotoGroup(
    val id: Int,
    val photos: List<ScoredPhoto>,
    val bestPhotoId: Long
) {
    val deletionCandidates: List<ScoredPhoto>
        get() = photos.filter { it.markedForDeletion }
    val bytesToFree: Long
        get() = deletionCandidates.sumOf { it.photo.sizeBytes }
}

enum class ScanPhase { READING, HASHING, GROUPING, SCORING }

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val phase: ScanPhase, val done: Int, val total: Int) : ScanState
    data class Done(val groups: List<PhotoGroup>) : ScanState
    data class Failed(val message: String) : ScanState
}

enum class AutopilotSchedule { DAILY, WEEKLY }

/**
 * "En iyi" seçim puanlamasındaki GÖRECELİ ağırlıklar (bkz. BestPhotoSelector).
 * Sabit toplamı 100 olmak ZORUNDA değildir — her grup kendi içinde toplamına
 * bölünerek normalize edilir; böylece Ayarlar'daki kaydırıcılar birbirinden
 * bağımsız hareket edebilir. Varsayılanlar mevcut davranışı yansıtır.
 */
data class ScoringWeights(
    // Üst düzey: yüzlü fotoğraflarda dördü birden, yüzsüz sahnelerde
    // faceQuality hariç kalan üçü yeniden normalize edilerek kullanılır.
    val faceQuality: Int = 40,
    val sharpness: Int = 35,
    val exposure: Int = 15,
    val resolution: Int = 10,
    // Yüz kalitesi içindeki alt ağırlıklar:
    val eyesOpen: Int = 45,
    val frontal: Int = 15,
    val smile: Int = 15,
    val mouthClosed: Int = 25
)

data class AppSettings(
    val hammingThreshold: Int,
    val timeWindowSec: Int,
    val trashMode: Boolean,
    val autopilotEnabled: Boolean,
    val autopilotSchedule: AutopilotSchedule,
    val selectedBucketIds: Set<Long>,
    val scoringWeights: ScoringWeights = ScoringWeights()
)
