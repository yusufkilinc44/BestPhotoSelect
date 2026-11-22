package com.bestphotoselect.domain

import com.bestphotoselect.data.model.PhotoAnalysis
import com.bestphotoselect.data.model.PhotoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotTest {

    // ---------- ScreenshotDetector ----------

    @Test
    fun `yaygin ekran goruntusu klasor ve dosya adlari taninir`() {
        assertTrue(ScreenshotDetector.isScreenshot("Screenshots", "Pictures/Screenshots/"))
        assertTrue(ScreenshotDetector.isScreenshot("Camera", "DCIM/Screenshots/"))
        assertTrue(ScreenshotDetector.isScreenshot("Ekran görüntüleri", null))
        assertTrue(ScreenshotDetector.isScreenshot("Camera", "DCIM/Camera/", "Screenshot_20241122-101500.png"))
        assertTrue(ScreenshotDetector.isScreenshot("ScreenCapture", null))
    }

    @Test
    fun `normal albumler ekran goruntusu sayilmaz`() {
        assertFalse(ScreenshotDetector.isScreenshot("Camera", "DCIM/Camera/", "IMG_20241122_101500.jpg"))
        assertFalse(ScreenshotDetector.isScreenshot("WhatsApp Images", "Pictures/WhatsApp Images/"))
        assertFalse(ScreenshotDetector.isScreenshot(null, null, null))
    }

    // ---------- ScreenshotMatcher ----------

    private val w = 1080
    private val h = 2400

    /** Sahte "sohbet ekranı": beyaz zemin, verilen satırlarda koyu metin blokları. */
    private fun screen(textRows: List<Int>, statusBarShade: Int = 0x20): IntArray {
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        // Durum çubuğu (üst ~%4): saat vb. değişebilir
        for (y in 0 until 90) for (x in 0 until w) {
            px[y * w + x] = (0xFF shl 24) or (statusBarShade shl 16) or (statusBarShade shl 8) or statusBarShade
        }
        for (row in textRows) {
            val y0 = 200 + row * 60
            for (y in y0 until y0 + 36) for (x in 60 until 900) {
                if ((x / 12) % 2 == 0) px[y * w + x] = 0xFF202020.toInt()
            }
        }
        return px
    }

    @Test
    fun `birebir ayni ekran goruntusu esles`() {
        val a = ScreenshotMatcher.signature(screen(listOf(1, 3, 5)), w, h)
        val b = ScreenshotMatcher.signature(screen(listOf(1, 3, 5)), w, h)
        assertTrue(ScreenshotMatcher.isNearDuplicate(a, b))
    }

    @Test
    fun `yalnizca durum cubugu farkliysa yine esles`() {
        val a = ScreenshotMatcher.signature(screen(listOf(1, 3, 5), statusBarShade = 0x20), w, h)
        val b = ScreenshotMatcher.signature(screen(listOf(1, 3, 5), statusBarShade = 0xE0), w, h)
        assertTrue(ScreenshotMatcher.isNearDuplicate(a, b))
    }

    @Test
    fun `ayni uygulama farkli icerik eslesmez`() {
        // Aynı düzen, tek bir mesaj satırı farklı: kaba dHash bunu kaçırabilir,
        // sıkı karşılaştırma yakalamalı (yanlış silmeyi önler).
        val a = ScreenshotMatcher.signature(screen(listOf(1, 3, 5, 7)), w, h)
        val b = ScreenshotMatcher.signature(screen(listOf(1, 3, 5, 8)), w, h)
        assertFalse(ScreenshotMatcher.isNearDuplicate(a, b))
    }

    @Test
    fun `farkli en boy orani eslesmez`() {
        val a = ScreenshotMatcher.signature(screen(listOf(1)), w, h)
        val wide = IntArray(2400 * 1080) { 0xFFFFFFFF.toInt() }
        val b = ScreenshotMatcher.signature(wide, 2400, 1080)
        assertFalse(ScreenshotMatcher.isNearDuplicate(a, b))
    }

    // ---------- PhotoGrouper isMatch + BestPhotoSelector.buildNewestKeptGroup ----------

    @Test
    fun `ek dogrulama reddederse hash ayni olsa da gruplanmaz`() {
        val photos = listOf(
            PhotoGrouper.Input(1, 1_000, 0L),
            PhotoGrouper.Input(2, 2_000, 0L)
        )
        val groups = PhotoGrouper.group(photos, 60_000, 4) { _, _ -> false }
        assertTrue(groups.isEmpty())
    }

    private fun analysis(id: Long, takenMs: Long) = PhotoAnalysis(
        photo = PhotoItem(
            id = id, bucketId = 1, dateTakenMs = takenMs, dateModified = takenMs,
            sizeBytes = 500_000, width = 1080, height = 2400,
            displayName = "Screenshot_$id.png", isScreenshot = true
        ),
        hash = 0L, sharpness = 0.0, exposure = 0f, face = null
    )

    @Test
    fun `ekran goruntusu grubunda en yeni korunur digerleri silinir`() {
        val group = BestPhotoSelector.buildNewestKeptGroup(
            groupId = 7,
            members = listOf(analysis(1, 1_000), analysis(2, 5_000), analysis(3, 3_000))
        )
        assertTrue(group.isScreenshots)
        assertEquals(2L, group.bestPhotoId)
        assertEquals(listOf(2L, 3L, 1L), group.photos.map { it.photo.id })
        assertEquals(setOf(1L, 3L), group.deletionCandidates.map { it.photo.id }.toSet())
    }
}
