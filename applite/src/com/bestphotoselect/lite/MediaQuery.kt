package com.bestphotoselect.lite

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import com.bestphotoselect.data.model.Album
import com.bestphotoselect.data.model.PhotoItem

/** MediaStore erişimi (lite): tüm çağrılar arka plan iş parçacığından yapılmalıdır. */
class MediaQuery(context: Context) {
    private val resolver = context.applicationContext.contentResolver
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
        MediaStore.Images.Media.DISPLAY_NAME
    )

    fun queryAlbums(): List<Album> {
        class Acc(var count: Int, var bytes: Long, var coverId: Long, var coverDate: Long, var name: String)

        val albums = LinkedHashMap<Long, Acc>()
        resolver.query(
            collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val bucket = c.getLong(bucketCol)
                val id = c.getLong(idCol)
                val date = c.getLong(dateCol)
                val acc = albums.getOrPut(bucket) {
                    Acc(0, 0, id, date, c.getString(nameCol) ?: "?")
                }
                acc.count++
                acc.bytes += c.getLong(sizeCol)
                if (date > acc.coverDate) {
                    acc.coverDate = date
                    acc.coverId = id
                }
            }
        }
        return albums.map { (bucketId, acc) ->
            Album(
                bucketId = bucketId,
                name = acc.name,
                photoCount = acc.count,
                coverUri = ContentUris.withAppendedId(collection, acc.coverId),
                totalBytes = acc.bytes
            )
        }.sortedByDescending { it.photoCount }
    }

    fun queryPhotos(bucketIds: Set<Long>): List<PhotoItem> {
        if (bucketIds.isEmpty()) return emptyList()
        val placeholders = bucketIds.joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
        val args = bucketIds.map { it.toString() }.toTypedArray()

        val result = mutableListOf<PhotoItem>()
        resolver.query(
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
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = c.getLong(takenCol)
                result += PhotoItem(
                    id = id,
                    bucketId = c.getLong(bucketCol),
                    dateTakenMs = if (taken > 0) taken else c.getLong(addedCol) * 1000,
                    dateModified = c.getLong(modifiedCol),
                    sizeBytes = c.getLong(sizeCol),
                    width = c.getInt(widthCol),
                    height = c.getInt(heightCol),
                    displayName = c.getString(nameCol) ?: id.toString()
                )
            }
        }
        return result
    }

    fun loadThumb(uri: Uri, size: Int): Bitmap? = try {
        resolver.loadThumbnail(uri, Size(size, size), null)
    } catch (e: Exception) {
        null
    }

    fun countStillVisible(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        var visible = 0
        ids.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            resolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media._ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null
            )?.use { c -> visible += c.count }
        }
        return visible
    }
}
