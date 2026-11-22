package com.bestphotoselect.domain

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Ekran görüntüleri için sıkı "neredeyse birebir aynı mı?" kontrolü.
 *
 * 64 bitlik dHash ekran görüntülerinde çok kabadır: aynı uygulamanın farklı
 * içerikli iki ekranı (ör. iki farklı sohbet) neredeyse aynı hash'i üretebilir.
 * Bu yüzden dHash yalnızca ön eleme yapar; aday çiftler burada çok daha yüksek
 * çözünürlüklü gri tonlama imzasıyla piksel piksel karşılaştırılır. Saat/bildirim
 * değişen durum çubuğu ve gezinme çubuğu karşılaştırmaya dahil edilmez.
 *
 * Çekirdek saf Kotlin'dir (ARGB dizisi üzerinde); JVM'de birim test edilebilir.
 */
object ScreenshotMatcher {

    const val SIG_W = 48
    const val SIG_H = 96

    private const val CROP_TOP = 0.06
    private const val CROP_BOTTOM = 0.08
    private const val MAX_ASPECT_DIFF = 0.01
    private const val PIXEL_DIFF_THRESHOLD = 24
    private const val MAX_DIFFERING_PIXEL_RATIO = 0.003
    private const val MAX_MEAN_ABS_DIFF = 3.0

    class Signature(val aspect: Double, val luma: IntArray)

    fun signature(bitmap: Bitmap): Signature {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return signature(pixels, bitmap.width, bitmap.height)
    }

    fun signature(pixels: IntArray, width: Int, height: Int): Signature {
        require(width > 0 && height > 0 && pixels.size >= width * height)
        val top = (height * CROP_TOP).toInt()
        val bottom = (height * (1 - CROP_BOTTOM)).toInt().coerceAtLeast(top + 1)
        val cropH = bottom - top
        val out = IntArray(SIG_W * SIG_H)
        for (sy in 0 until SIG_H) {
            val y0 = top + sy * cropH / SIG_H
            val y1 = (top + (sy + 1) * cropH / SIG_H).coerceAtLeast(y0 + 1).coerceAtMost(bottom)
            for (sx in 0 until SIG_W) {
                val x0 = sx * width / SIG_W
                val x1 = ((sx + 1) * width / SIG_W).coerceAtLeast(x0 + 1).coerceAtMost(width)
                var sum = 0.0
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) {
                        val p = pixels[row + x]
                        sum += 0.299 * ((p shr 16) and 0xFF) +
                            0.587 * ((p shr 8) and 0xFF) +
                            0.114 * (p and 0xFF)
                    }
                }
                out[sy * SIG_W + sx] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        return Signature(width.toDouble() / height, out)
    }

    fun isNearDuplicate(a: Signature, b: Signature): Boolean {
        if (abs(a.aspect - b.aspect) > MAX_ASPECT_DIFF * a.aspect) return false
        var differing = 0
        var totalDiff = 0L
        for (i in a.luma.indices) {
            val d = abs(a.luma[i] - b.luma[i])
            totalDiff += d
            if (d > PIXEL_DIFF_THRESHOLD) differing++
        }
        val n = a.luma.size
        return differing <= n * MAX_DIFFERING_PIXEL_RATIO &&
            totalDiff.toDouble() / n <= MAX_MEAN_ABS_DIFF
    }
}
