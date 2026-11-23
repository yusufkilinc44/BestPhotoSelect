package com.bestphotoselect.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Galeri okuma izninin durumu (tam, Android 14+ kısmi, yok). */
object GalleryAccess {

    enum class Access { FULL, PARTIAL, NONE }

    /**
     * İstenecek izinler. Android 14+'ta iki izin birlikte istenir; böylece sistem
     * penceresi "Tümüne izin ver / Fotoğrafları seç / İzin verme" seçeneklerini gösterir.
     */
    fun requestPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun current(context: Context): Access {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when {
                granted(Manifest.permission.READ_MEDIA_IMAGES) -> Access.FULL
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
                else -> Access.NONE
            }
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.FULL
            else -> Access.NONE
        }
    }
}
