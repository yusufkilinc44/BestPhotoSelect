package com.bestphotoselect.domain

import com.bestphotoselect.data.model.PhotoAnalysis
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScoredPhoto

/**
 * Bir benzer-fotoğraf grubunun üyelerini puanlar ve en iyisini seçer.
 *
 * Netlik ve çözünürlük grup içinde göreli normalize edilir (sahneden sahneye
 * mutlak değerler karşılaştırılamaz). Yüz içeren gruplarda yüz metrikleri
 * (gözler açık, kameraya dönük, gülümseme) en yüksek ağırlığı alır.
 */
object BestPhotoSelector {

    fun buildGroup(groupId: Int, members: List<PhotoAnalysis>): PhotoGroup {
        val scored = score(members)
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

    fun score(members: List<PhotoAnalysis>): List<Pair<PhotoAnalysis, Float>> {
        require(members.isNotEmpty())
        val maxSharpness = members.maxOf { it.sharpness }.coerceAtLeast(1e-6)
        val maxPixels = members.maxOf { it.photo.width.toLong() * it.photo.height }.coerceAtLeast(1L)
        val anyFace = members.any { it.face != null }

        return members.map { m ->
            val relSharpness = (m.sharpness / maxSharpness).toFloat()
            val relResolution = (m.photo.width.toLong() * m.photo.height).toFloat() / maxPixels
            val score = if (anyFace) {
                val faceScore = m.face?.let {
                    0.45f * it.eyesOpen + 0.35f * it.frontal + 0.20f * it.smile
                } ?: MISSING_FACE_SCORE
                0.40f * faceScore + 0.35f * relSharpness + 0.15f * m.exposure + 0.10f * relResolution
            } else {
                0.55f * relSharpness + 0.30f * m.exposure + 0.15f * relResolution
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
