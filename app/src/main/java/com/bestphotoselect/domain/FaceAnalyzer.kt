package com.bestphotoselect.domain

import android.graphics.Bitmap
import com.bestphotoselect.data.model.FaceMetrics
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * ML Kit ile cihaz içi yüz analizi: gözlerin açıklığı, yüzün kameraya dönüklüğü
 * ve gülümseme olasılığı. Model APK içinde paketlidir; internet gerektirmez.
 */
@Singleton
class FaceAnalyzer @Inject constructor() {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.08f)
                .build()
        )
    }

    // Yalnızca yüz sayısı için hafif bir dedektör: sınıflandırma/işaret noktası
    // yok, hızlı mod. Hash aşamasında TÜM fotoğraflarda çalıştırılabilecek kadar
    // ucuz; gruplamada "aynı arka plan, farklı kişi sayısı" yanlış eşleşmelerini
    // elemek için kullanılır (bkz. PhotoGrouper.faceCountsCompatible).
    private val countDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setMinFaceSize(0.08f)
                .build()
        )
    }

    suspend fun countFaces(bitmap: Bitmap): Int = try {
        countDetector.process(InputImage.fromBitmap(bitmap, 0)).await().size
    } catch (e: Exception) {
        -1
    }

    suspend fun analyze(bitmap: Bitmap): FaceMetrics? {
        val faces = try {
            detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        } catch (e: Exception) {
            return null
        }
        if (faces.isEmpty()) return null

        val imageArea = (bitmap.width * bitmap.height).toFloat()
        var totalWeight = 0f
        var eyesOpen = 0f
        var frontal = 0f
        var smile = 0f
        var mouthClosed = 0f
        var areaSum = 0f

        for (face in faces) {
            val area = (face.boundingBox.width() * face.boundingBox.height()).toFloat()
            val weight = area.coerceAtLeast(1f)
            areaSum += area
            eyesOpen += eyesOpenScore(face) * weight
            frontal += frontalScore(face) * weight
            smile += (face.smilingProbability ?: 0.5f) * weight
            mouthClosed += mouthClosedScore(face) * weight
            totalWeight += weight
        }

        return FaceMetrics(
            faceCount = faces.size,
            eyesOpen = (eyesOpen / totalWeight).coerceIn(0f, 1f),
            frontal = (frontal / totalWeight).coerceIn(0f, 1f),
            smile = (smile / totalWeight).coerceIn(0f, 1f),
            mouthClosed = (mouthClosed / totalWeight).coerceIn(0f, 1f),
            faceAreaRatio = min(1f, areaSum / imageArea)
        )
    }

    private fun eyesOpenScore(face: Face): Float {
        val left = face.leftEyeOpenProbability
        val right = face.rightEyeOpenProbability
        // Sınıflandırma başarısız olduysa nötr bir varsayım kullan.
        if (left == null && right == null) return 0.7f
        return ((left ?: right ?: 0.7f) + (right ?: left ?: 0.7f)) / 2f
    }

    private fun frontalScore(face: Face): Float {
        val yaw = abs(face.headEulerAngleY)   // sağa-sola dönüş
        val roll = abs(face.headEulerAngleZ)  // yatma
        val yawPenalty = min(1f, yaw / 45f)
        val rollPenalty = min(1f, roll / 45f)
        return (1f - (yawPenalty * 0.75f + rollPenalty * 0.25f)).coerceIn(0f, 1f)
    }

    /**
     * Ağız doğallığı: iç dudak hatlarının (üst dudak altı / alt dudak üstü)
     * dikey açıklığı ağız genişliğine oranlanır. Konuşma anı/esneme gibi geniş
     * açık ağızlarda oran yükselir (düşük skor); doğal kapalı ağızda düşük
     * kalır (yüksek skor). Kontur/işaret noktası bulunamazsa nötr değer döner.
     */
    private fun mouthClosedScore(face: Face): Float {
        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        if (upperLipBottom.isNullOrEmpty() || lowerLipTop.isNullOrEmpty() || mouthLeft == null || mouthRight == null) {
            return 0.7f
        }
        val upperY = upperLipBottom.map { it.y }.average()
        val lowerY = lowerLipTop.map { it.y }.average()
        val mouthWidth = hypot((mouthRight.x - mouthLeft.x).toDouble(), (mouthRight.y - mouthLeft.y).toDouble())
        if (mouthWidth < 1e-3) return 0.7f
        val ratio = ((lowerY - upperY) / mouthWidth).toFloat()
        return (1f - (ratio - MOUTH_RATIO_CLOSED) / (MOUTH_RATIO_OPEN - MOUTH_RATIO_CLOSED)).coerceIn(0f, 1f)
    }

    private companion object {
        const val MOUTH_RATIO_CLOSED = 0.03f
        const val MOUTH_RATIO_OPEN = 0.35f
    }
}
