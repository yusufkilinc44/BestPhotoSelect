package com.bestphotoselect.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGrouperTest {

    private fun input(id: Long, timeMs: Long, hash: Long, faceCount: Int = -1) =
        PhotoGrouper.Input(id = id, timeMs = timeMs, hash = hash, faceCount = faceCount)

    @Test
    fun `benzer ve yakin zamanli fotograflar gruplanir`() {
        val base = 0b1010_1010_1010L
        val photos = listOf(
            input(1, 1_000, base),
            input(2, 2_000, base or 1L),          // 1 bit fark
            input(3, 3_000, base xor 0b11L),      // 2 bit fark
            input(4, 500_000, base)               // ayni hash ama zaman penceresi disi
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 60_000, maxHammingDistance = 10)
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L, 3L), groups.single().toSet())
    }

    @Test
    fun `farkli hashler gruplanmaz`() {
        val photos = listOf(
            input(1, 1_000, 0x0000_0000_0000_0000L),
            input(2, 2_000, -0x1L) // tum bitler farkli (64 bit mesafe)
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 60_000, maxHammingDistance = 10)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `zaman penceresi kapaliyken uzak zamanli benzerler de gruplanir`() {
        val hash = 0xDEADBEEFL
        val photos = listOf(
            input(1, 0, hash),
            input(2, 999_999_999, hash)
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 0, maxHammingDistance = 4)
        assertEquals(1, groups.size)
    }

    @Test
    fun `gecisli benzerlik tek grup olusturur`() {
        // A~B ve B~C ise A, B, C ayni gruptadir (A~C dogrudan benzemese bile).
        val a = 0L
        val b = 0b1111_1111L            // A'ya 8 bit
        val c = 0b1111_1111_1111_1111L  // B'ye 8 bit, A'ya 16 bit
        val photos = listOf(
            input(1, 1_000, a),
            input(2, 2_000, b),
            input(3, 3_000, c)
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 60_000, maxHammingDistance = 8)
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L, 3L), groups.single().toSet())
    }

    @Test
    fun `tek fotograf grup olusturmaz`() {
        val groups = PhotoGrouper.group(
            listOf(input(1, 0, 42L)),
            timeWindowMs = 60_000,
            maxHammingDistance = 10
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `iki bagimsiz grup ayri kalir`() {
        val h1 = 0L
        val h2 = -0x1L
        val photos = listOf(
            input(1, 1_000, h1),
            input(2, 2_000, h1),
            input(3, 100_000_000, h2),
            input(4, 100_001_000, h2)
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 60_000, maxHammingDistance = 5)
        assertEquals(2, groups.size)
        assertEquals(setOf(setOf(1L, 2L), setOf(3L, 4L)), groups.map { it.toSet() }.toSet())
    }

    @Test
    fun `ayni arka plan ama farkli kisi sayisi gruplanmaz`() {
        // Kullanicidan gelen gercek vaka: ayni iskele/deniz manzarasi onunde
        // once 3 kisilik aile fotografi, sonra tek kisilik foto - dHash bu ikisini
        // benzer bulabilir (arka plan hakim) ama yuz sayisi 3 ile 1 cok farkli.
        val hash = 0b1010_1010_1010L
        val photos = listOf(
            input(1, 1_000, hash, faceCount = 3),
            input(2, 2_000, hash or 1L, faceCount = 1)
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 60_000, maxHammingDistance = 10)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `bir kisilik fark toleransla gruplanir`() {
        // Goz kirpma / arkaya donme gibi nedenlerle bir karede bir kisi daha az
        // tespit edilmis olabilir; +-1 fark hala ayni grupta sayilmali.
        val hash = 0b1010_1010_1010L
        val photos = listOf(
            input(1, 1_000, hash, faceCount = 2),
            input(2, 2_000, hash or 1L, faceCount = 3)
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 60_000, maxHammingDistance = 10)
        assertEquals(1, groups.size)
    }

    @Test
    fun `yuz sayisi bilinmiyorsa yalnizca hash'e gore gruplanir`() {
        // faceCount = -1 (varsayilan) = "bilinmiyor"; eski davranis korunur.
        val hash = 0b1010_1010_1010L
        val photos = listOf(
            input(1, 1_000, hash),
            input(2, 2_000, hash or 1L)
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 60_000, maxHammingDistance = 10)
        assertEquals(1, groups.size)
    }

    @Test
    fun `gecisli benzerlikte yuz sayisi uyumsuzlugu zinciri kirar`() {
        // A(2 kisi) ~ B(2 kisi) hash'e gore benzer, B ~ C(2 kisi) de benzer,
        // ama A ile C arasindaki dogrudan yuz sayisi farki onemli degil;
        // asil kontrol her komsu ciftte ayri ayri uygulanir.
        val a = 0L
        val b = 0b1111_1111L
        val c = 0b1111_1111_1111_1111L
        val photos = listOf(
            input(1, 1_000, a, faceCount = 2),
            input(2, 2_000, b, faceCount = 5), // A ile 2, B ile 3 kisi farki -> ikisiyle de uyumsuz
            input(3, 3_000, c, faceCount = 2)
        )
        val groups = PhotoGrouper.group(photos, timeWindowMs = 60_000, maxHammingDistance = 8)
        // 2 numarali foto hem A hem C ile yuz sayisi uyumsuz oldugundan izole kalir;
        // A ve C da birbirine dogrudan (16 bit mesafe > 8 esik) gruplanamaz.
        assertTrue(groups.isEmpty())
    }
}
