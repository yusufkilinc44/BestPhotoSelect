package com.bestphotoselect.domain

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.min

/**
 * Teknik kalite metrikleri: netlik (Laplacian varyansı) ve pozlama (histogram).
 * Küçültülmüş görüntüler üzerinde çalışır; OpenCV gerektirmez. Çekirdek, ARGB
 * piksel dizileri üzerinde saf Kotlin'dir ve JVM'de test edilebilir.
 */
object QualityScorer {

    fun sharpness(bitmap: Bitmap): Double {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return sharpness(pixels, bitmap.width, bitmap.height)
    }

    fun exposure(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return exposure(pixels, bitmap.width, bitmap.height)
    }

    /**
     * Gri tonlamalı görüntüde 4-komşulu Laplacian filtresinin varyansı.
     * Bulanık görüntülerde kenar enerjisi düşük olduğundan varyans küçüktür.
     * Değer mutlak değil görecelidir: aynı gruptaki fotoğraflar arasında karşılaştırılmalıdır.
     */
    fun sharpness(pixels: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        val luma = IntArray(w * h)
        for (i in 0 until w * h) {
            val p = pixels[i]
            luma[i] = (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000
        }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val lap = (4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - w] - luma[i + w]).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return sumSq / count - mean * mean
    }

    /**
     * 0..1 pozlama puanı. Kırpılmış gölgeler/parlak alanlar ve ortalama parlaklığın
     * ideal orta değerden sapması cezalandırılır.
     */
    fun exposure(pixels: IntArray, w: Int, h: Int): Float {
        val n = w * h
        if (n == 0) return 0f
        var dark = 0
        var bright = 0
        var total = 0L
        for (i in 0 until n) {
            val p = pixels[i]
            val l = (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000
            if (l <= 10) dark++
            if (l >= 245) bright++
            total += l
        }
        val darkFrac = dark.toFloat() / n
        val brightFrac = bright.toFloat() / n
        val mean = total.toFloat() / n

        val clipPenalty = min(1f, darkFrac * 1.5f + brightFrac * 2f)
        val meanPenalty = min(1f, abs(mean - 118f) / 118f)
        val score = 1f - min(1f, clipPenalty * 0.7f + meanPenalty * 0.5f)
        return score.coerceIn(0f, 1f)
    }
}
