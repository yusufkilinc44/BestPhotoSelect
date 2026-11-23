package com.bestphotoselect.domain

import com.bestphotoselect.data.model.PhotoAnalysis
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScoredPhoto
import com.bestphotoselect.data.model.ScoringWeights

/**
 * Bir benzer-fotoğraf grubunun üyelerini puanlar ve en iyisini seçer.
 *
 * Netlik ve çözünürlük grup içinde göreli normalize edilir (sahneden sahneye
 * mutlak değerler karşılaştırılamaz). Ağırlıklar [ScoringWeights] ile
 * dışarıdan verilir (Ayarlar ekranından kullanıcı tarafından ayarlanabilir);
 * her alt-grup kendi toplamına bölünerek normalize edildiğinden ağırlıkların
 * tam 100'e toplanması ZORUNLU değildir.
 */
object BestPhotoSelector {

    fun buildGroup(
        groupId: Int,
        members: List<PhotoAnalysis>,
        weights: ScoringWeights = ScoringWeights()
    ): PhotoGroup {
        val scored = score(members, weights)
        val best = scored.maxWith(
            compareBy({ it.second }, { it.first.photo.width.toLong() * it.first.photo.height }, { it.first.photo.dateTakenMs })
        ).first.photo.id

        val photos = scored
            .sortedByDescending { it.second }
            .map { (analysis, score) ->
                ScoredPhoto(
                    analysis = analysis,
                    score = score,
                    markedForDeletion = analysis.photo.id != best
                )
            }
        return PhotoGroup(id = groupId, photos = photos, bestPhotoId = best)
    }

    /**
     * Ekran görüntüsü grubu: üyeler zaten neredeyse birebir aynı olduğundan
     * kalite puanlaması anlamsızdır; en yeni kopya korunur, diğerleri silinmeye
     * işaretlenir.
     */
    fun buildNewestKeptGroup(groupId: Int, members: List<PhotoAnalysis>): PhotoGroup {
        require(members.isNotEmpty())
        val newest = members.maxWith(compareBy({ it.photo.dateTakenMs }, { it.photo.id })).photo.id
        val photos = members
            .sortedWith(compareByDescending<PhotoAnalysis> { it.photo.dateTakenMs }.thenByDescending { it.photo.id })
            .map { analysis ->
                ScoredPhoto(
                    analysis = analysis,
                    score = if (analysis.photo.id == newest) 1f else 0f,
                    markedForDeletion = analysis.photo.id != newest
                )
            }
        return PhotoGroup(id = groupId, photos = photos, bestPhotoId = newest, isScreenshots = true)
    }

    fun score(
        members: List<PhotoAnalysis>,
        weights: ScoringWeights = ScoringWeights()
    ): List<Pair<PhotoAnalysis, Float>> {
        require(members.isNotEmpty())
        val maxSharpness = members.maxOf { it.sharpness }.coerceAtLeast(1e-6)
        val maxPixels = members.maxOf { it.photo.width.toLong() * it.photo.height }.coerceAtLeast(1L)
        val anyFace = members.any { it.face != null }
        // Bir aradalık yalnızca grupta birden fazla kişili kare varsa anlamlıdır.
        val anyTogetherness = members.any { it.face?.togetherness != null }
        // Kompozisyon temizliği yalnızca nesne etiketleri varsa hesaplanabilir.
        val anyLabels = members.size > 1 && members.any { it.labels.isNotEmpty() }

        // Yüz kalitesi alt-ağırlıkları kendi içinde normalize edilir.
        val faceSubSum = (weights.eyesOpen + weights.frontal + weights.smile + weights.mouthClosed).coerceAtLeast(1)
        val wEyes = weights.eyesOpen.toFloat() / faceSubSum
        val wFrontal = weights.frontal.toFloat() / faceSubSum
        val wSmile = weights.smile.toFloat() / faceSubSum
        val wMouth = weights.mouthClosed.toFloat() / faceSubSum

        // Üst düzey ağırlıklar: grupta uygulanamayan ölçütler (yüz, bir aradalık,
        // kompozisyon) hesaptan çıkar, kalanlar kendi aralarında normalize edilir.
        val topSum = (
            (if (anyFace) weights.faceQuality else 0) +
                weights.sharpness + weights.exposure + weights.resolution +
                (if (anyTogetherness) weights.togetherness else 0) +
                (if (anyLabels) weights.composition else 0)
            ).coerceAtLeast(1)
        val wFace = if (anyFace) weights.faceQuality.toFloat() / topSum else 0f
        val wSharp = weights.sharpness.toFloat() / topSum
        val wExposure = weights.exposure.toFloat() / topSum
        val wRes = weights.resolution.toFloat() / topSum
        val wTogether = if (anyTogetherness) weights.togetherness.toFloat() / topSum else 0f
        val wComposition = if (anyLabels) weights.composition.toFloat() / topSum else 0f

        return members.map { m ->
            val relSharpness = (m.sharpness / maxSharpness).toFloat()
            val relResolution = (m.photo.width.toLong() * m.photo.height).toFloat() / maxPixels
            var score = wSharp * relSharpness + wExposure * m.exposure + wRes * relResolution
            if (anyFace) {
                val faceScore = m.face?.let {
                    wEyes * it.eyesOpen + wFrontal * it.frontal + wSmile * it.smile + wMouth * it.mouthClosed
                } ?: MISSING_FACE_SCORE
                score += wFace * faceScore
            }
            if (anyTogetherness) {
                // Bu karede ölçülemediyse (ör. bir kişi tespit edilemedi) nötr değer.
                score += wTogether * (m.face?.togetherness ?: NEUTRAL)
            }
            if (anyLabels) {
                score += wComposition * CompositionAnalyzer.cleanliness(m, members.filter { it !== m })
            }
            m to score.coerceIn(0f, 1f)
        }
    }

    private const val NEUTRAL = 0.5f

    /**
     * Grupta yüz varken bu karede yüz bulunamadıysa (ör. herkes arkasını dönmüş
     * ya da yüz kadraj dışı kalmış) düşük ama sıfır olmayan bir taban puan.
     */
    private const val MISSING_FACE_SCORE = 0.15f
}
