package com.bestphotoselect.domain

import java.util.Locale

/**
 * Ekran görüntülerini klasör adı, MediaStore göreli yolu (ör. "Pictures/Screenshots/",
 * "DCIM/Screenshots/") ya da dosya adından (ör. "Screenshot_20241122-...png") tanır.
 * Üreticiden üreticiye değişen adlar için birkaç yaygın işaret aranır.
 */
object ScreenshotDetector {

    private val MARKERS = listOf(
        "screenshot",
        "screen_shot",
        "screen shot",
        "screencapture",
        "screen_capture",
        "screen capture",
        "ekran görüntü",
        "ekran goruntu",
        "bildschirmfoto"
    )

    fun isScreenshot(vararg names: String?): Boolean =
        names.any { name ->
            if (name.isNullOrBlank()) return@any false
            val lower = name.lowercase(Locale.ROOT)
            MARKERS.any { it in lower }
        }
}
