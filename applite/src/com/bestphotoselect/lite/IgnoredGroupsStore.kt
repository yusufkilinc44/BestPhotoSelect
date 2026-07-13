package com.bestphotoselect.lite

import android.content.Context

/**
 * Kullanıcının "bir daha gösterme" dediği grupların kalıcı kaydı. Gruplar
 * kalıcı bir kimliğe sahip değildir (her taramada sıfırdan hesaplanır); bu
 * yüzden grup üyesi fotoğrafların MediaStore ID kümesi imza olarak kullanılır.
 * Aynı fotoğraf kümesi bir sonraki taramada yine aynı grup olarak bulunursa
 * sonuçlardan tamamen çıkarılır (silinmesi gereken grup olarak ASLA gösterilmez).
 */
object IgnoredGroupsStore {
    private const val PREFS = "ignored_groups"
    private const val KEY = "signatures"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun signature(memberIds: List<Long>): String = memberIds.sorted().joinToString(",")

    fun isIgnored(context: Context, memberIds: List<Long>): Boolean =
        signature(memberIds) in prefs(context).getStringSet(KEY, emptySet())!!

    fun ignore(context: Context, memberIds: List<Long>) {
        val p = prefs(context)
        val updated = HashSet(p.getStringSet(KEY, emptySet())!!)
        updated += signature(memberIds)
        p.edit().putStringSet(KEY, updated).apply()
    }

    fun count(context: Context): Int = prefs(context).getStringSet(KEY, emptySet())!!.size

    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}
