package com.bestphotoselect.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Saf JVM testleri: ARGB piksel dizileri üzerinde çalışır, Android gerektirmez. */
class QualityScorerTest {

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun solid(v: Int, size: Int = 64): IntArray =
        IntArray(size * size) { rgb(v, v, v) }

    private fun checkerboard(size: Int = 64, cell: Int = 2): IntArray {
        val px = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val on = ((x / cell) + (y / cell)) % 2 == 0
                px[y * size + x] = if (on) rgb(255, 255, 255) else rgb(0, 0, 0)
            }
        }
        return px
    }

    /** Basit kutu bulaniklastirma: keskin görüntünün yumuşatılmış karşılığı. */
    private fun blurred(src: IntArray, size: Int, radius: Int = 3): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                var r = 0; var g = 0; var b = 0; var n = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, size - 1)
                        val ny = (y + dy).coerceIn(0, size - 1)
                        val p = src[ny * size + nx]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        n++
                    }
                }
                out[y * size + x] = rgb(r / n, g / n, b / n)
            }
        }
        return out
    }

    @Test
    fun `keskin goruntunun netlik puani bulanik olandan yuksektir`() {
        val sharp = checkerboard()
        val blurry = blurred(checkerboard(), 64)
        val sharpScore = QualityScorer.sharpness(sharp, 64, 64)
        val blurryScore = QualityScorer.sharpness(blurry, 64, 64)
        assertTrue("keskin=$sharpScore bulanik=$blurryScore", sharpScore > blurryScore * 2)
    }

    @Test
    fun `duz renkli goruntunun netligi sifirdir`() {
        assertTrue(QualityScorer.sharpness(solid(128), 64, 64) < 1.0)
    }

    @Test
    fun `dengeli pozlama asiri pozlamadan yuksek puan alir`() {
        val balancedScore = QualityScorer.exposure(solid(118), 64, 64)
        val overexposed = QualityScorer.exposure(solid(255), 64, 64)
        val underexposed = QualityScorer.exposure(solid(0), 64, 64)
        assertTrue("dengeli=$balancedScore patlamis=$overexposed", balancedScore > overexposed)
        assertTrue("dengeli=$balancedScore karanlik=$underexposed", balancedScore > underexposed)
    }

    @Test
    fun `pozlama puani 0 ile 1 arasindadir`() {
        val rnd = Random(7)
        repeat(5) {
            val px = IntArray(16 * 16) {
                rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            }
            val score = QualityScorer.exposure(px, 16, 16)
            assertTrue(score in 0f..1f)
        }
    }

    @Test
    fun `netlik puani negatif olamaz`() {
        val rnd = Random(11)
        val px = IntArray(32 * 32) { rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) }
        assertTrue(QualityScorer.sharpness(px, 32, 32) >= 0.0)
    }
}
