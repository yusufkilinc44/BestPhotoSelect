package com.bestphotoselect.domain

import com.bestphotoselect.domain.PhotoGrouper.Input
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateIdsTest {

    private val window = 180_000L

    @Test
    fun `yuz sayisina bakmadan benzer komsusu olanlar aday olur`() {
        val photos = listOf(
            Input(1, 0, 0L, faceCount = 3),
            Input(2, 10_000, 1L, faceCount = 6),   // kişi sayısı farklı ama görsel olarak benzer
            Input(3, 20_000, -1L)                   // tamamen farklı görüntü
        )
        assertEquals(setOf(1L, 2L), PhotoGrouper.candidateIds(photos, window, 10))
    }

    @Test
    fun `zaman penceresi disindakiler aday olmaz`() {
        val photos = listOf(Input(1, 0, 0L), Input(2, window + 1, 0L))
        assertTrue(PhotoGrouper.candidateIds(photos, window, 10).isEmpty())
    }

    @Test
    fun `adaylar yuz sayili gruplamanin ust kumesidir`() {
        val photos = listOf(
            Input(1, 0, 0L, faceCount = 1),
            Input(2, 5_000, 0L, faceCount = 1),
            Input(3, 10_000, 0L, faceCount = 5),
            Input(4, 15_000, 0L, faceCount = 5)
        )
        val grouped = PhotoGrouper.group(photos, window, 10).flatten().toSet()
        val candidates = PhotoGrouper.candidateIds(photos, window, 10)
        assertTrue(candidates.containsAll(grouped))
        // Yüz sayısıyla 1-1 ve 5-5 ayrı gruplara ayrılır, 1 ile 5 kişi asla birleşmez.
        assertEquals(2, PhotoGrouper.group(photos, window, 10).size)
    }
}
