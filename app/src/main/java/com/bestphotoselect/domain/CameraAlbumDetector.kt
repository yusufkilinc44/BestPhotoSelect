package com.bestphotoselect.domain

import java.util.Locale

/**
 * Telefonun kamerasıyla çekilen fotoğrafların düştüğü ana albümü bulur.
 * Neredeyse tüm üreticiler "DCIM/Camera/" kullanır; olmazsa DCIM altındaki en
 * kalabalık (ekran görüntüsü olmayan) albüm ana galeri sayılır.
 */
object CameraAlbumDetector {

    data class Candidate(
        val bucketId: Long,
        val name: String?,
        val relativePath: String?,
        val photoCount: Int,
        val isScreenshots: Boolean
    )

    fun isCameraPath(name: String?, relativePath: String?): Boolean {
        val path = relativePath?.trim('/')?.lowercase(Locale.ROOT)
        if (path == "dcim/camera") return true
        return path?.startsWith("dcim/") == true && name.equals("Camera", ignoreCase = true)
    }

    /** Ana galerinin bucket kimliği; bulunamazsa null. */
    fun find(candidates: List<Candidate>): Long? {
        candidates.firstOrNull { isCameraPath(it.name, it.relativePath) }?.let { return it.bucketId }
        return candidates
            .filter { !it.isScreenshots && it.relativePath?.trim('/')?.lowercase(Locale.ROOT)?.startsWith("dcim") == true }
            .maxByOrNull { it.photoCount }
            ?.bucketId
    }
}
