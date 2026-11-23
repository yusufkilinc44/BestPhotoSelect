package com.bestphotoselect.domain

import com.bestphotoselect.data.model.PhotoAnalysis

/**
 * Kompozisyon temizliği (0..1): grubun diğer karelerinde olmayan ama bu karede
 * beliren yabancı öğe (araya giren gemi, araba, kuş vb.) puanı düşürür.
 *
 * Kareler aynı sahnenin art arda çekimleri olduğundan, bir öğe yalnızca bir
 * karede belirgin şekilde görünüyor ve diğerlerinin çoğunda yoksa "araya girmiş"
 * sayılır. Karşılaştırma piksel değil nesne etiketi üzerinden yapıldığı için
 * kadraj kayması sonucu etkilemez.
 *
 * Yalnızca [INTRUDER_LABELS] içindeki etiketler dikkate alınır: "gülümseme",
 * "gökyüzü" gibi soyut/sahne etiketlerinin bir karede fazladan çıkması o kareyi
 * cezalandırmamalı.
 *
 * Saf Kotlin; JVM'de birim test edilir.
 */
object CompositionAnalyzer {

    /** Bu güvenin üstünde bir öğe karede "belirgin şekilde var" sayılır. */
    private const val PRESENT = 0.6f

    /** Diğer karede bu güvenin üstündeyse o karede de "var" sayılır. */
    private const val ALSO_PRESENT = 0.4f

    /**
     * Sahneye sonradan girebilen öğeler: ML Kit görüntü etiketleme modelinin
     * (image-labeling 17.0.9) gerçek etiket adları, büyük/küçük harf aynen. Tam
     * liste test kaynaklarında: mlkit_image_labeling_labels.txt. "Person" bilerek
     * yok: kişiler yüz analizi ve bir aradalık ölçütüyle değerlendirilir.
     */
    val INTRUDER_LABELS: Set<String> = setOf(
        // Su taşıtları
        "Boat", "Brig", "Clipper", "Frigate", "Speedboat", "Skiff", "Sailboat", "Kayak", "Canoe",
        // Kara taşıtları
        "Car", "Bus", "Van", "Motorcycle", "Bicycle", "Vehicle", "Tractor", "Rickshaw",
        // Hava ve raylı taşıtlar
        "Airplane", "Airliner", "Aircraft", "Helicopter", "Train",
        // Kadraja girebilen hayvanlar
        "Bird", "Duck", "Waterfowl", "Dog", "Cat", "Horse", "Cattle", "Bull", "Insect", "Butterfly",
        // Nesneler
        "Umbrella", "Balloon"
    )

    /** Önbellek için "ad:güven;ad:güven" biçimi (etiket adlarında ':' ve ';' yok). */
    fun encode(labels: Map<String, Float>): String =
        labels.entries.joinToString(";") { "${it.key}:${"%.3f".format(java.util.Locale.ROOT, it.value)}" }

    fun decode(encoded: String?): Map<String, Float> {
        if (encoded.isNullOrEmpty()) return emptyMap()
        return encoded.split(';').mapNotNull { part ->
            val i = part.lastIndexOf(':')
            if (i <= 0) null else part.substring(i + 1).toFloatOrNull()?.let { part.substring(0, i) to it }
        }.toMap()
    }

    /** 1 = temiz, 0 = belirgin bir yabancı öğe var. */
    fun cleanliness(photo: PhotoAnalysis, others: List<PhotoAnalysis>): Float {
        if (others.isEmpty()) return 1f
        var worst = 0f
        for ((label, confidence) in photo.labels) {
            if (label !in INTRUDER_LABELS || confidence < PRESENT) continue
            val sharedBy = others.count { (it.labels[label] ?: 0f) >= ALSO_PRESENT }
            if (sharedBy * 2 < others.size) {
                worst = maxOf(worst, confidence)
            }
        }
        return 1f - worst
    }
}
