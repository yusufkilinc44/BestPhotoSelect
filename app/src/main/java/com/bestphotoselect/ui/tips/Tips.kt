package com.bestphotoselect.ui.tips

import com.bestphotoselect.R

/** Uygulama içi ipucu: kısa başlık, açıklama ve renkli kart için emoji. */
data class Tip(val id: String, val emoji: String, val titleRes: Int, val bodyRes: Int)

/**
 * İpuçları önem sırasına göre dizilidir. İlk açılışta en önemli birkaçı art arda,
 * sonra uygulama her açıldığında (en az [MIN_GAP_MS] arayla) çoğunlukla bir, bazen
 * iki yeni ipucu gösterilir. Bir kez gösterilen ipucu bir daha gösterilmez.
 */
object Tips {

    const val FIRST_RUN_COUNT = 4
    const val MIN_GAP_MS = 20L * 60 * 60 * 1000

    val ALL = listOf(
        Tip("scan", "🔍", R.string.tip_scan_title, R.string.tip_scan_body),
        Tip("review", "⭐", R.string.tip_review_title, R.string.tip_review_body),
        Tip("trash", "♻️", R.string.tip_trash_title, R.string.tip_trash_body),
        Tip("group_nav", "⏭️", R.string.tip_group_nav_title, R.string.tip_group_nav_body),
        Tip("manage_media", "🔓", R.string.tip_manage_media_title, R.string.tip_manage_media_body),
        Tip("autoscan", "🤖", R.string.tip_autoscan_title, R.string.tip_autoscan_body),
        Tip("sensitivity", "🎚️", R.string.tip_sensitivity_title, R.string.tip_sensitivity_body),
        Tip("time_window", "⏱️", R.string.tip_time_window_title, R.string.tip_time_window_body),
        Tip("weights", "⚖️", R.string.tip_weights_title, R.string.tip_weights_body),
        Tip("composition", "🚢", R.string.tip_composition_title, R.string.tip_composition_body),
        Tip("ignore", "🙈", R.string.tip_ignore_title, R.string.tip_ignore_body),
        Tip("theme", "🎨", R.string.tip_theme_title, R.string.tip_theme_body),
        Tip("language", "🌍", R.string.tip_language_title, R.string.tip_language_body),
        Tip("grid_list", "🔲", R.string.tip_grid_list_title, R.string.tip_grid_list_body),
        Tip("whatsapp", "💬", R.string.tip_whatsapp_title, R.string.tip_whatsapp_body),
        Tip("screenshots", "📱", R.string.tip_screenshots_title, R.string.tip_screenshots_body),
        Tip("history", "🕘", R.string.tip_history_title, R.string.tip_history_body),
        Tip("how_it_works", "🧠", R.string.tip_how_it_works_title, R.string.tip_how_it_works_body)
    )

    /**
     * Bu açılışta gösterilecek ipuçlarının kimlikleri (önem sırasıyla).
     * [lastShownAt] 0 ise hiç ipucu gösterilmemiştir (ilk açılış).
     */
    fun selectIds(orderedIds: List<String>, seen: Set<String>, lastShownAt: Long, now: Long): List<String> {
        val unseen = orderedIds.filter { it !in seen }
        if (unseen.isEmpty()) return emptyList()
        if (lastShownAt == 0L) return unseen.take(FIRST_RUN_COUNT)
        if (now - lastShownAt < MIN_GAP_MS) return emptyList()
        // Çoğunlukla tek ipucu; her üç gösterimden birinde art arda iki.
        val count = if (seen.size % 3 == 2) 2 else 1
        return unseen.take(count)
    }
}
