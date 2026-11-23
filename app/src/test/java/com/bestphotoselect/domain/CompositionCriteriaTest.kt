package com.bestphotoselect.domain

import com.bestphotoselect.data.model.FaceMetrics
import com.bestphotoselect.data.model.PhotoAnalysis
import com.bestphotoselect.data.model.PhotoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionCriteriaTest {

    // ---------- GroupCohesion ----------

    private fun face(x: Float, y: Float = 100f, w: Float = 40f) = GroupCohesion.FaceBox(x, y, w)

    @Test
    fun `omuz omuza duran kisiler tam puan alir`() {
        // Merkezler arası 1.5 yüz genişliği
        val score = GroupCohesion.score(listOf(face(100f), face(160f), face(220f)))!!
        assertEquals(1f, score, 0.001f)
    }

    @Test
    fun `bir kisi gruptan kopuksa puan sifira iner`() {
        // Son kişi 7 yüz genişliği uzakta
        val score = GroupCohesion.score(listOf(face(100f), face(160f), face(440f)))!!
        assertEquals(0f, score, 0.001f)
    }

    @Test
    fun `biraz araliklilar ara puan alir`() {
        // 3.35 yüz genişliği: tam bir arada (2.2) ile kopuk (4.5) arasının ortası
        val score = GroupCohesion.score(listOf(face(100f), face(234f)))!!
        assertTrue(score in 0.4f..0.6f)
    }

    @Test
    fun `tek kisi ya da yuzsuz karede olcut uygulanmaz`() {
        assertNull(GroupCohesion.score(listOf(face(100f))))
        assertNull(GroupCohesion.score(emptyList()))
    }

    // ---------- BestPhotoSelector: bir aradalık ----------

    private fun photo(id: Long) = PhotoItem(
        id = id, bucketId = 1, dateTakenMs = id * 1000, dateModified = id,
        sizeBytes = 1_000_000, width = 4000, height = 3000, displayName = "IMG_$id.jpg"
    )

    private fun groupPhoto(id: Long, togetherness: Float?) = PhotoAnalysis(
        photo = photo(id), hash = 0L, sharpness = 500.0, exposure = 0.8f,
        face = FaceMetrics(
            faceCount = 4, eyesOpen = 0.9f, frontal = 0.9f, smile = 0.5f,
            mouthClosed = 0.8f, faceAreaRatio = 0.1f, togetherness = togetherness
        )
    )

    @Test
    fun `kisilerin bir arada oldugu kare kazanir`() {
        val together = groupPhoto(1, togetherness = 1f)
        val apart = groupPhoto(2, togetherness = 0.1f)
        val group = BestPhotoSelector.buildGroup(0, listOf(apart, together))
        assertEquals(1L, group.bestPhotoId)
    }

    @Test
    fun `tek kisilik gruplarda bir aradalik puani etkilemez`() {
        val a = PhotoAnalysis(photo(1), 0L, 500.0, 0.8f, FaceMetrics(1, 0.9f, 0.9f, 0.5f, 0.8f, 0.2f))
        val b = PhotoAnalysis(photo(2), 0L, 500.0, 0.8f, FaceMetrics(1, 0.9f, 0.9f, 0.5f, 0.8f, 0.2f))
        val scores = BestPhotoSelector.score(listOf(a, b)).map { it.second }
        assertEquals(scores[0], scores[1], 0.0001f)
    }

    // ---------- CompositionAnalyzer ----------

    private fun labeled(id: Long, vararg labels: Pair<String, Float>) = PhotoAnalysis(
        photo = photo(id), hash = 0L, sharpness = 500.0, exposure = 0.8f, face = null,
        labels = labels.toMap()
    )

    @Test
    fun `yabanci oge listesindeki her ad modelin etiket listesinde birebir var`() {
        val modelLabels = javaClass.classLoader!!
            .getResourceAsStream("mlkit_image_labeling_labels.txt")!!
            .bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .toSet()
        assertEquals(447, modelLabels.size)
        val missing = CompositionAnalyzer.INTRUDER_LABELS - modelLabels
        assertTrue("Modelde olmayan etiketler: $missing", missing.isEmpty())
    }

    @Test
    fun `etiketler onbellege yazilip aynen geri okunur`() {
        val labels = mapOf("Boat" to 0.912f, "Sky" to 0.5f, "Two words" to 0.3f)
        val decoded = CompositionAnalyzer.decode(CompositionAnalyzer.encode(labels))
        assertEquals(labels.keys, decoded.keys)
        labels.forEach { (k, v) -> assertEquals(v, decoded.getValue(k), 0.001f) }
        assertTrue(CompositionAnalyzer.decode(null).isEmpty())
        assertTrue(CompositionAnalyzer.decode("").isEmpty())
    }

    @Test
    fun `yabanci oge listesinde olmayan etiketler cezalandirilmaz`() {
        val a = labeled(1, "UnlistedAbstractLabel" to 0.95f)
        val b = labeled(2)
        assertEquals(1f, CompositionAnalyzer.cleanliness(a, listOf(b)), 0.0001f)
    }

    @Test
    fun `yalnizca bu karede olan yabanci oge puani dusurur, ortak oge dusurmez`() {
        val intruder = CompositionAnalyzer.INTRUDER_LABELS.firstOrNull() ?: return
        val withIntruder = labeled(1, intruder to 0.9f)
        val clean = labeled(2)
        assertEquals(0.1f, CompositionAnalyzer.cleanliness(withIntruder, listOf(clean)), 0.0001f)
        assertEquals(1f, CompositionAnalyzer.cleanliness(clean, listOf(withIntruder)), 0.0001f)

        // Öğe grubun çoğunda varsa sahnenin parçasıdır, yabancı sayılmaz.
        val alsoHas = labeled(3, intruder to 0.7f)
        assertEquals(1f, CompositionAnalyzer.cleanliness(withIntruder, listOf(alsoHas)), 0.0001f)
    }

    @Test
    fun `araya yabanci oge giren kare kaybeder`() {
        val intruder = CompositionAnalyzer.INTRUDER_LABELS.firstOrNull() ?: return
        val withIntruder = labeled(1, intruder to 0.9f)
        val clean = labeled(2)
        val group = BestPhotoSelector.buildGroup(0, listOf(withIntruder, clean))
        assertEquals(2L, group.bestPhotoId)
    }
}
