package com.bestphotoselect.ui.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Uygulama içi dil seçimi. Boş etiket = telefonun dili (varsayılan). Seçim
 * AppCompat tarafından saklanır (Android 13+'ta sistemin "Uygulama dilleri"
 * ayarıyla aynı yer); değişince etkinlik yeni dille yeniden oluşturulur.
 * Telefon dili desteklenmiyorsa varsayılan kaynaklar (İngilizce) kullanılır.
 */
object AppLanguage {

    data class Option(val tag: String, val flag: String, val name: String)

    /** Ad her dilin kendi yazımıyla; İngilizce için Birleşik Krallık bayrağı. */
    val options = listOf(
        Option("tr", "🇹🇷", "Türkçe"),
        Option("en", "🇬🇧", "English"),
        Option("zh-CN", "🇨🇳", "中文（简体）"),
        Option("hi", "🇮🇳", "हिन्दी"),
        Option("es", "🇪🇸", "Español"),
        Option("fr", "🇫🇷", "Français"),
        Option("ar", "🇸🇦", "العربية")
    )

    /** Seçili seçenek; telefonun dili kullanılıyorsa null. */
    fun current(): Option? {
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return null
        return options.firstOrNull { it.tag.substringBefore('-') == locale.language }
    }

    /** null = telefonun diline dön. */
    fun set(option: Option?) {
        AppCompatDelegate.setApplicationLocales(
            if (option == null) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(option.tag)
        )
    }
}
