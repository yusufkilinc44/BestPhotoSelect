package com.bestphotoselect.domain

import android.graphics.Bitmap
import com.bestphotoselect.data.model.FaceMetrics
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
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
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.08f)
                .build()
        )
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
        var areaSum = 0f

        for (face in faces) {
            val area = (face.boundingBox.width() * face.boundingBox.height()).toFloat()
            val weight = area.coerceAtLeast(1f)
            areaSum += area
            eyesOpen += eyesOpenScore(face) * weight
            frontal += frontalScore(face) * weight
            smile += (face.smilingProbability ?: 0.5f) * weight
            totalWeight += weight
        }

        return FaceMetrics(
            faceCount = faces.size,
            eyesOpen = (eyesOpen / totalWeight).coerceIn(0f, 1f),
            frontal = (frontal / totalWeight).coerceIn(0f, 1f),
            smile = (smile / totalWeight).coerceIn(0f, 1f),
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
}
