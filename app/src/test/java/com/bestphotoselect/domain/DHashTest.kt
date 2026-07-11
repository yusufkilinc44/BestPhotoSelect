package com.bestphotoselect.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saf JVM testleri: ARGB piksel dizileri üzerinde çalışır, Android gerektirmez. */
class DHashTest {

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun gradient(size: Int = 64, shift: Int = 0): IntArray {
        val px = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = ((x + shift) * 255 / (size - 1)).coerceIn(0, 255)
                px[y * size + x] = rgb(v, v, v)
            }
        }
        return px
    }

    private fun circle(size: Int = 64): IntArray {
        val px = IntArray(size * size)
        val c = size / 2f
        val r = size / 3f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - c
                val dy = y - c
                val inside = dx * dx + dy * dy <= r * r
                px[y * size + x] = if (inside) rgb(0, 0, 0) else rgb(255, 255, 255)
            }
        }
        return px
    }

    @Test
    fun `ayni goruntu ayni hashi verir`() {
        assertEquals(
            DHash.compute(gradient(), 64, 64),
            DHash.compute(gradient(), 64, 64)
        )
    }

    @Test
    fun `ayni hashin hamming mesafesi sifirdir`() {
        val h = DHash.compute(gradient(), 64, 64)
        assertEquals(0, DHash.hammingDistance(h, h))
    }

    @Test
    fun `hafif kaydirilmis goruntu dusuk mesafe verir`() {
        val h1 = DHash.compute(gradient(shift = 0), 64, 64)
        val h2 = DHash.compute(gradient(shift = 2), 64, 64)
        val d = DHash.hammingDistance(h1, h2)
        assertTrue("beklenen kucuk mesafe, bulunan: $d", d <= 10)
    }

    @Test
    fun `farkli gorunumler yuksek mesafe verir`() {
        val h1 = DHash.compute(gradient(), 64, 64)
        val h2 = DHash.compute(circle(), 64, 64)
        val d = DHash.hammingDistance(h1, h2)
        assertTrue("beklenen buyuk mesafe, bulunan: $d", d > 10)
    }

    @Test
    fun `farkli cozunurluklerde ayni icerik benzer hash verir`() {
        val h1 = DHash.compute(gradient(size = 32), 32, 32)
        val h2 = DHash.compute(gradient(size = 256), 256, 256)
        val d = DHash.hammingDistance(h1, h2)
        assertTrue("boyut degisimi hash'i bozmamali, mesafe: $d", d <= 4)
    }

    @Test
    fun `parlaklik kaymasi hashi degistirmez`() {
        // dHash komsu karsilastirmasina dayandigi icin sabit parlaklik ofsetine dayaniklidir.
        // Degrade 0..200 araliginda tutulur ki +40 ofset kirpilmadan uygulanabilsin.
        val size = 64
        val base = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = x * 200 / (size - 1)
                base[y * size + x] = rgb(v, v, v)
            }
        }
        val brighter = IntArray(base.size) { i ->
            val v = ((base[i] shr 16) and 0xFF) + 40
            rgb(v, v, v)
        }
        val d = DHash.hammingDistance(
            DHash.compute(base, size, size),
            DHash.compute(brighter, size, size)
        )
        assertTrue("parlaklik ofseti mesafeyi bozmamali: $d", d <= 4)
    }
}
