package com.bestphotoselect.domain

import java.util.Locale

/**
 * WhatsApp (ve WhatsApp Business) klasörlerini albüm adı ya da MediaStore göreli
 * yolundan tanır, ör. "WhatsApp Images" ya da
 * "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/".
 */
object WhatsAppDetector {

    private val MARKERS = listOf("whatsapp", "com.whatsapp")

    fun isWhatsApp(vararg names: String?): Boolean =
        names.any { name ->
            if (name.isNullOrBlank()) return@any false
            val lower = name.lowercase(Locale.ROOT)
            MARKERS.any { it in lower }
        }
}
