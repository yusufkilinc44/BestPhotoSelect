package com.bestphotoselect.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.bestphotoselect.data.model.Album
import com.bestphotoselect.data.model.PhotoItem
import com.bestphotoselect.domain.ScreenshotDetector
import com.bestphotoselect.domain.WhatsAppDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH
    )

    suspend fun queryAlbums(): List<Album> = withContext(Dispatchers.IO) {
        data class Acc(
            var count: Int, var bytes: Long, var coverId: Long, var coverDate: Long,
            var name: String, var isScreenshots: Boolean, var isWhatsApp: Boolean
        )

        val albums = LinkedHashMap<Long, Acc>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val bucket = c.getLong(bucketCol)
                val id = c.getLong(idCol)
                val date = c.getLong(dateCol)
                val acc = albums.getOrPut(bucket) {
                    val name = c.getString(nameCol) ?: "?"
                    val path = c.getString(pathCol)
                    Acc(
                        0, 0, id, date, name,
                        ScreenshotDetector.isScreenshot(name, path),
                        WhatsAppDetector.isWhatsApp(name, path)
                    )
                }
                acc.count++
                acc.bytes += c.getLong(sizeCol)
                if (date > acc.coverDate) {
                    acc.coverDate = date
                    acc.coverId = id
                }
            }
        }
        albums.map { (bucketId, acc) ->
            Album(
                bucketId = bucketId,
                name = acc.name,
                photoCount = acc.count,
                coverUri = ContentUris.withAppendedId(collection, acc.coverId),
                totalBytes = acc.bytes,
                isScreenshots = acc.isScreenshots,
                isWhatsApp = acc.isWhatsApp
            )
        }.sortedByDescending { it.photoCount }
    }

    suspend fun queryPhotos(bucketIds: Set<Long>): List<PhotoItem> = withContext(Dispatchers.IO) {
        if (bucketIds.isEmpty()) return@withContext emptyList()
        val placeholders = bucketIds.joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
        val args = bucketIds.map { it.toString() }.toTypedArray()

        val result = mutableListOf<PhotoItem>()
        context.contentResolver.query(
            collection, projection, selection, args,
            "${MediaStore.Images.Media.DATE_TAKEN} ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = c.getLong(takenCol)
                val displayName = c.getString(nameCol) ?: id.toString()
                result += PhotoItem(
                    id = id,
                    bucketId = c.getLong(bucketCol),
                    dateTakenMs = if (taken > 0) taken else c.getLong(addedCol) * 1000,
                    dateModified = c.getLong(modifiedCol),
                    sizeBytes = c.getLong(sizeCol),
                    width = c.getInt(widthCol),
                    height = c.getInt(heightCol),
                    displayName = displayName,
                    isScreenshot = ScreenshotDetector.isScreenshot(
                        c.getString(bucketNameCol), c.getString(pathCol), displayName
                    )
                )
            }
        }
        result
    }

    /**
     * MediaStore'un kendi küçük resim üreticisi: hızlıdır ve EXIF yönünü uygular.
     * Android 11+ HARDWARE bitmap döndürebilir; piksel erişimi (getPixels, ML Kit)
     * için ARGB_8888 yazılım kopyasına çevrilir.
     */
    suspend fun loadThumbnail(uri: Uri, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bmp = context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            if (bmp.config == Bitmap.Config.HARDWARE) {
                val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
                if (copy != null) {
                    bmp.recycle()
                    copy
                } else {
                    bmp
                }
            } else {
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    fun createTrashRequest(uris: List<Uri>): PendingIntent =
        MediaStore.createTrashRequest(context.contentResolver, uris, true)

    fun createDeleteRequest(uris: List<Uri>): PendingIntent =
        MediaStore.createDeleteRequest(context.contentResolver, uris)

    /** MANAGE_MEDIA (Android 12+) verilmişse silme istekleri onay diyaloğu göstermez. */
    fun canManageMedia(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    /**
     * Otomatik pilot doğrulaması: verilen kayıtlardan kaçı hâlâ (çöpe taşınmamış
     * olarak) görünür durumda? 0 dönerse işlem başarıyla tamamlanmış demektir.
     */
    suspend fun countStillVisible(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        var visible = 0
        ids.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media._ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null
            )?.use { c -> visible += c.count }
        }
        visible
    }
}
