package com.bestphotoselect.domain

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit ile cihaz içi görüntü etiketleme (model APK içinde paketli). Kompozisyon
 * temizliği ölçütü için kullanılır (bkz. CompositionAnalyzer).
 */
@Singleton
class ImageLabeler @Inject constructor() {

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_CONFIDENCE)
                .build()
        )
    }

    /** Etiket -> güven (0..1); hata olursa boş. */
    suspend fun labels(bitmap: Bitmap): Map<String, Float> = try {
        labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
            .associate { it.text to it.confidence }
    } catch (e: Exception) {
        emptyMap()
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.3f
    }
}
