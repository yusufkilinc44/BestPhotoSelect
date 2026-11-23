package com.bestphotoselect.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppDetectorTest {

    @Test
    fun `whatsapp klasorleri tanınır`() {
        assertTrue(WhatsAppDetector.isWhatsApp("WhatsApp Images", null))
        assertTrue(WhatsAppDetector.isWhatsApp("WhatsApp Business Images", null))
        assertTrue(WhatsAppDetector.isWhatsApp("Sent", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/"))
        assertTrue(WhatsAppDetector.isWhatsApp("Images", "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/"))
    }

    @Test
    fun `diger klasorler whatsapp sayılmaz`() {
        assertFalse(WhatsAppDetector.isWhatsApp("Camera", "DCIM/Camera/"))
        assertFalse(WhatsAppDetector.isWhatsApp("Screenshots", "Pictures/Screenshots/"))
        assertFalse(WhatsAppDetector.isWhatsApp(null, null))
    }
}
