package com.bestphotoselect.domain

import android.graphics.Bitmap

/**
 * 64-bit fark hash'i (dHash): görüntü 9x8 gri tonlamaya indirgenir ve yatay
 * komşu pikseller karşılaştırılarak algısal bir parmak izi üretilir.
 * Yeniden boyutlama, hafif kırpma, pozlama ve sıkıştırma farklarına dayanıklıdır.
 *
 * Çekirdek, ARGB piksel dizileri üzerinde saf Kotlin olarak çalışır; böylece
 * birim testleri Android çalışma zamanı olmadan JVM'de koşabilir.
 */
object DHash {
    private const val W = 9
    private const val H = 8

    fun compute(bitmap: Bitmap): Long {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return compute(pixels, bitmap.width, bitmap.height)
    }

    fun compute(pixels: IntArray, width: Int, height: Int): Long {
        require(width > 0 && height > 0 && pixels.size >= width * height)
        val luma = downscaleLuma(pixels, width, height)
        var hash = 0L
        var bit = 0
        for (y in 0 until H) {
            for (x in 0 until W - 1) {
                if (luma[y * W + x] < luma[y * W + x + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Kutu (alan ortalaması) filtresiyle 9x8 gri tonlamaya indirger. */
    private fun downscaleLuma(pixels: IntArray, width: Int, height: Int): DoubleArray {
        val out = DoubleArray(W * H)
        for (cy in 0 until H) {
            val y0 = cy * height / H
            val y1 = ((cy + 1) * height / H).coerceAtLeast(y0 + 1).coerceAtMost(height)
            for (cx in 0 until W) {
                val x0 = cx * width / W
                val x1 = ((cx + 1) * width / W).coerceAtLeast(x0 + 1).coerceAtMost(width)
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
                out[cy * W + cx] = sum / ((y1 - y0) * (x1 - x0))
            }
        }
        return out
    }
}
