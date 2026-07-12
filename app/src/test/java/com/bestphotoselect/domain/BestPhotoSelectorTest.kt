package com.bestphotoselect.domain

import com.bestphotoselect.data.model.FaceMetrics
import com.bestphotoselect.data.model.PhotoAnalysis
import com.bestphotoselect.data.model.PhotoItem
import com.bestphotoselect.data.model.ScoringWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saf JVM testleri. PhotoItem.uri türetilmiş alan olduğundan ve puanlama uri'ye
 * dokunmadığından Android çalışma zamanı gerekmez.
 */
class BestPhotoSelectorTest {

    private fun photo(id: Long, width: Int = 4000, height: Int = 3000) = PhotoItem(
        id = id,
        bucketId = 1L,
        dateTakenMs = id * 1000,
        dateModified = id,
        sizeBytes = 1_000_000,
        width = width,
        height = height,
        displayName = "IMG_$id.jpg"
    )

    private fun analysis(
        id: Long,
        sharpness: Double = 500.0,
        exposure: Float = 0.8f,
        face: FaceMetrics? = null,
        width: Int = 4000,
        height: Int = 3000
    ) = PhotoAnalysis(
        photo = photo(id, width, height),
        hash = 0L,
        sharpness = sharpness,
        exposure = exposure,
        face = face
    )

    @Test
    fun `gozleri acik olan kazanir`() {
        val eyesOpen = analysis(
            1, face = FaceMetrics(1, eyesOpen = 0.95f, frontal = 0.9f, smile = 0.5f, faceAreaRatio = 0.2f)
        )
        val eyesClosed = analysis(
            2, face = FaceMetrics(1, eyesOpen = 0.05f, frontal = 0.9f, smile = 0.5f, faceAreaRatio = 0.2f)
        )
        val group = BestPhotoSelector.buildGroup(0, listOf(eyesClosed, eyesOpen))
        assertEquals(1L, group.bestPhotoId)
    }

    @Test
    fun `kameraya donuk yuz kazanir`() {
        val frontal = analysis(
            1, face = FaceMetrics(1, eyesOpen = 0.9f, frontal = 0.95f, smile = 0.5f, faceAreaRatio = 0.2f)
        )
        val turnedAway = analysis(
            2, face = FaceMetrics(1, eyesOpen = 0.9f, frontal = 0.1f, smile = 0.5f, faceAreaRatio = 0.2f)
        )
        val group = BestPhotoSelector.buildGroup(0, listOf(turnedAway, frontal))
        assertEquals(1L, group.bestPhotoId)
    }

    @Test
    fun `yuz yokken en net fotograf kazanir`() {
        val sharp = analysis(1, sharpness = 900.0)
        val blurry = analysis(2, sharpness = 50.0)
        val group = BestPhotoSelector.buildGroup(0, listOf(blurry, sharp))
        assertEquals(1L, group.bestPhotoId)
    }

    @Test
    fun `grupta yuz varken yuzsuz kare cezalandirilir`() {
        val withFace = analysis(
            1, sharpness = 300.0,
            face = FaceMetrics(1, eyesOpen = 0.9f, frontal = 0.9f, smile = 0.6f, faceAreaRatio = 0.2f)
        )
        val noFace = analysis(2, sharpness = 400.0, face = null)
        val group = BestPhotoSelector.buildGroup(0, listOf(noFace, withFace))
        assertEquals(1L, group.bestPhotoId)
    }

    @Test
    fun `en iyi disindakiler silinmeye isaretlenir`() {
        val group = BestPhotoSelector.buildGroup(
            0,
            listOf(
                analysis(1, sharpness = 900.0),
                analysis(2, sharpness = 100.0),
                analysis(3, sharpness = 200.0)
            )
        )
        val best = group.photos.single { it.photo.id == group.bestPhotoId }
        assertFalse(best.markedForDeletion)
        assertTrue(group.photos.filter { it.photo.id != group.bestPhotoId }.all { it.markedForDeletion })
        assertEquals(2, group.deletionCandidates.size)
    }

    @Test
    fun `puanlar 0 ile 1 arasindadir`() {
        val scored = BestPhotoSelector.score(
            listOf(
                analysis(1, sharpness = 900.0, exposure = 1f),
                analysis(2, sharpness = 0.0, exposure = 0f)
            )
        )
        scored.forEach { (_, score) -> assertTrue(score in 0f..1f) }
    }

    @Test
    fun `esit puanlarda yuksek cozunurluk kazanir`() {
        val small = analysis(1, sharpness = 500.0, width = 1000, height = 750)
        val large = analysis(2, sharpness = 500.0, width = 4000, height = 3000)
        val group = BestPhotoSelector.buildGroup(0, listOf(small, large))
        assertEquals(2L, group.bestPhotoId)
    }

    @Test
    fun `ozel agirliklarla yuze donuklugun etkisi degistirilebilir`() {
        // A: yuze donuklukte iyi ama gulumsemede zayif. B: tam tersi.
        val a = analysis(1, face = FaceMetrics(1, eyesOpen = 0.9f, frontal = 0.9f, smile = 0.3f, faceAreaRatio = 0.2f))
        val b = analysis(2, face = FaceMetrics(1, eyesOpen = 0.9f, frontal = 0.5f, smile = 0.9f, faceAreaRatio = 0.2f))

        // Varsayilan agirliklarla (goz %55, yuz donuklugu %20, gulumseme %25) B kazanir.
        val default = BestPhotoSelector.buildGroup(0, listOf(a, b))
        assertEquals(2L, default.bestPhotoId)

        // Ayarlardan yuz donukluguyu tek belirleyici yapinca A kazanmali.
        val frontalFocused = ScoringWeights(eyesOpen = 0, frontal = 100, smile = 0)
        val custom = BestPhotoSelector.buildGroup(0, listOf(a, b), frontalFocused)
        assertEquals(1L, custom.bestPhotoId)
    }

    @Test
    fun `ust duzey agirlik toplami sifir olsa bile cokme olmaz`() {
        val a = analysis(1, sharpness = 900.0)
        val b = analysis(2, sharpness = 100.0)
        val zeroWeights = ScoringWeights(faceQuality = 0, sharpness = 0, exposure = 0, resolution = 0)
        val group = BestPhotoSelector.buildGroup(0, listOf(a, b), zeroWeights)
        group.photos.forEach { assertTrue(it.score in 0f..1f) }
    }

    @Test
    fun `yuzsuz grupta ust duzey agirliklar yeniden normalize edilir`() {
        // Yuz kalitesi agirligi cok yuksek olsa da grupta yuz yoksa etkisi olmamali;
        // netlik/pozlama/cozunurluk kendi aralarinda normalize edilip kullanilir.
        val sharp = analysis(1, sharpness = 900.0, face = null)
        val blurry = analysis(2, sharpness = 50.0, face = null)
        val weights = ScoringWeights(faceQuality = 1000, sharpness = 35, exposure = 15, resolution = 10)
        val group = BestPhotoSelector.buildGroup(0, listOf(blurry, sharp), weights)
        assertEquals(1L, group.bestPhotoId)
    }
}
