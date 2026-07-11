package com.bestphotoselect.lite

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Küçük resim yükleyici: LruCache + arka plan havuzu, görünüm geri dönüşümüne dayanıklı. */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, view: ImageView, uri: Uri, sizePx: Int) {
        val key = "$uri-$sizePx"
        view.tag = key
        cache.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        val appContext = context.applicationContext
        pool.execute {
            val bmp = try {
                appContext.contentResolver.loadThumbnail(
                    uri, android.util.Size(sizePx, sizePx), null
                )
            } catch (e: Exception) {
                null
            }
            if (bmp != null) {
                cache.put(key, bmp)
                main.post {
                    if (view.tag == key) view.setImageBitmap(bmp)
                }
            }
        }
    }
}
