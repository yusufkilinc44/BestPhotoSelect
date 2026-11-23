package com.bestphotoselect.domain

import com.bestphotoselect.domain.CameraAlbumDetector.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraAlbumDetectorTest {

    @Test
    fun `DCIM Camera ana galeridir`() {
        val albums = listOf(
            Candidate(1, "WhatsApp Images", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", 900, false),
            Candidate(2, "Camera", "DCIM/Camera/", 300, false),
            Candidate(3, "Screenshots", "DCIM/Screenshots/", 1200, true)
        )
        assertEquals(2L, CameraAlbumDetector.find(albums))
    }

    @Test
    fun `Camera yoksa DCIM altindaki en kalabalik albüm secilir`() {
        val albums = listOf(
            Candidate(1, "Screenshots", "DCIM/Screenshots/", 1200, true),
            Candidate(2, "100ANDRO", "DCIM/100ANDRO/", 500, false),
            Candidate(3, "Pictures", "Pictures/", 2000, false)
        )
        assertEquals(2L, CameraAlbumDetector.find(albums))
    }

    @Test
    fun `hicbiri uymuyorsa ana galeri yok`() {
        assertNull(CameraAlbumDetector.find(listOf(Candidate(1, "Download", "Download/", 10, false))))
    }
}
