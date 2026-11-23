package com.bestphotoselect.domain

import kotlin.math.hypot
import kotlin.math.min

/**
 * Birden fazla kişili karelerde kişilerin bir arada durup durmadığı (0..1).
 *
 * Yüzler soldan sağa sıralanır; komşu iki yüzün merkezleri arasındaki mesafe,
 * ortanca yüz genişliği cinsinden ölçülür. Omuz omuza duran kişilerde bu mesafe
 * yaklaşık 2 yüz genişliğidir. Kare puanını en zayıf halka belirler: geri kalanı
 * ne kadar sıkı olursa olsun tek bir kişinin kopuk durması puanı düşürür.
 *
 * Saf Kotlin; JVM'de birim test edilir.
 */
object GroupCohesion {

    data class FaceBox(val centerX: Float, val centerY: Float, val width: Float)

    /** Bu mesafeye (yüz genişliği) kadar "bir arada" sayılır. */
    private const val TOGETHER = 2.2f

    /** Bu mesafeden sonrası tamamen kopuk sayılır. */
    private const val APART = 4.5f

    /** Tek kişilik ya da yüzsüz karelerde ölçüt uygulanmaz: null. */
    fun score(faces: List<FaceBox>): Float? {
        if (faces.size < 2) return null
        val medianWidth = faces.map { it.width }.sorted()[faces.size / 2].coerceAtLeast(1f)
        val sorted = faces.sortedBy { it.centerX }
        var weakest = 1f
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val distance = hypot(b.centerX - a.centerX, b.centerY - a.centerY) / medianWidth
            val link = (1f - (distance - TOGETHER) / (APART - TOGETHER)).coerceIn(0f, 1f)
            weakest = min(weakest, link)
        }
        return weakest
    }
}
