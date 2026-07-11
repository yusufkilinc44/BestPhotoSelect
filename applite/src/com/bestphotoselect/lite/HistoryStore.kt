package com.bestphotoselect.lite

import android.content.Context
import com.bestphotoselect.data.model.PhotoItem
import org.json.JSONArray
import org.json.JSONObject

/** Silme geçmişi: SharedPreferences içinde JSON dizisi (en yeni başta, en çok 300 kayıt). */
object HistoryStore {

    data class Entry(
        val name: String,
        val sizeBytes: Long,
        val atMs: Long,
        val auto: Boolean,
        val trashed: Boolean
    )

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun addAll(context: Context, photos: List<PhotoItem>, auto: Boolean, trashed: Boolean) {
        if (photos.isEmpty()) return
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        photos.forEach { p ->
            arr.put(JSONObject().apply {
                put("n", p.displayName)
                put("s", p.sizeBytes)
                put("t", now)
                put("a", auto)
                put("tr", trashed)
            })
        }
        val old = JSONArray(sp(context).getString(KEY, "[]"))
        for (i in 0 until minOf(old.length(), MAX - arr.length())) {
            arr.put(old.getJSONObject(i))
        }
        sp(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun all(context: Context): List<Entry> {
        val arr = JSONArray(sp(context).getString(KEY, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(
                name = o.optString("n"),
                sizeBytes = o.optLong("s"),
                atMs = o.optLong("t"),
                auto = o.optBoolean("a"),
                trashed = o.optBoolean("tr")
            )
        }
    }

    fun clear(context: Context) {
        sp(context).edit().remove(KEY).apply()
    }

    private const val KEY = "entries"
    private const val MAX = 300
}
