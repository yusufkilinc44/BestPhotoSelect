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

    fun score(
        members: List<PhotoAnalysis>,
        weights: ScoringWeights = ScoringWeights()
    ): List<Pair<PhotoAnalysis, Float>> {
        require(members.isNotEmpty())
        val maxSharpness = members.maxOf { it.sharpness }.coerceAtLeast(1e-6)
        val maxPixels = members.maxOf { it.photo.width.toLong() * it.photo.height }.coerceAtLeast(1L)
        val anyFace = members.any { it.face != null }

        // Yüz kalitesi alt-ağırlıkları kendi içinde normalize edilir.
        val faceSubSum = (weights.eyesOpen + weights.frontal + weights.smile + weights.mouthClosed).coerceAtLeast(1)
        val wEyes = weights.eyesOpen.toFloat() / faceSubSum
        val wFrontal = weights.frontal.toFloat() / faceSubSum
        val wSmile = weights.smile.toFloat() / faceSubSum
        val wMouth = weights.mouthClosed.toFloat() / faceSubSum

        // Üst düzey ağırlıklar: yüzlü grupta dördü birden, yüzsüz grupta yüz
        // kalitesi hariç kalan üçü yeniden normalize edilerek kullanılır.
        val topSum = if (anyFace) {
            (weights.faceQuality + weights.sharpness + weights.exposure + weights.resolution).coerceAtLeast(1)
        } else {
            (weights.sharpness + weights.exposure + weights.resolution).coerceAtLeast(1)
        }
        val wFace = if (anyFace) weights.faceQuality.toFloat() / topSum else 0f
        val wSharp = weights.sharpness.toFloat() / topSum
        val wExposure = weights.exposure.toFloat() / topSum
        val wRes = weights.resolution.toFloat() / topSum

        return members.map { m ->
            val relSharpness = (m.sharpness / maxSharpness).toFloat()
            val relResolution = (m.photo.width.toLong() * m.photo.height).toFloat() / maxPixels
            val score = if (anyFace) {
                val faceScore = m.face?.let {
                    wEyes * it.eyesOpen + wFrontal * it.frontal + wSmile * it.smile + wMouth * it.mouthClosed
                } ?: MISSING_FACE_SCORE
                wFace * faceScore + wSharp * relSharpness + wExposure * m.exposure + wRes * relResolution
            } else {
                wSharp * relSharpness + wExposure * m.exposure + wRes * relResolution
            }
            m to score.coerceIn(0f, 1f)
        }
    }

    /**
     * Grupta yüz varken bu karede yüz bulunamadıysa (ör. herkes arkasını dönmüş
     * ya da yüz kadraj dışı kalmış) düşük ama sıfır olmayan bir taban puan.
     */
    private const val MISSING_FACE_SCORE = 0.15f
}
