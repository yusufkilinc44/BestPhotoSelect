package com.bestphotoselect.lite

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Programatik tasarım sistemi (layout XML'i ve androidx kullanılmaz).
 * Tema: Turkuaz + Mercan — canlı, modern, açık/koyu moda duyarlı.
 * Paleti değiştirmek için yalnızca bu dosyadaki renkleri güncellemek yeterlidir.
 */
object Ui {

    // ---- Palet ----
    // TEAL yalnızca geriye dönük varsayılan değer olarak tutulur (ör. pillButton
    // varsayılan dolgusu); ekranların kendi markası artık Screens nesnesinden gelir.
    const val TEAL = 0xFF14B8A6.toInt()
    const val TEAL_DARK = 0xFF0F766E.toInt()
    const val TEAL_DEEP = 0xFF0B4F4A.toInt()

    // Anlamlı (semantik) renkler — hangi ekranda olursa olsun HER ZAMAN aynı anlamı taşır:
    const val CORAL = 0xFFFF6B6B.toInt()      // sil / tehlike
    const val CORAL_DARK = 0xFFE05252.toInt()
    const val AMBER = 0xFFFFC53D.toInt()      // en iyi / yıldız
    const val GREEN = 0xFF22C55E.toInt()      // korunacak / başarılı
    const val WHITE = 0xFFFFFFFF.toInt()

    /** Bir ekranın "markası": başlık gradyanı ve o ekrana özgü birincil buton rengi. */
    data class Accent(val main: Int, val dark: Int)

    /**
     * "Rengarenk" tema: her ekranın kendine özgü canlı bir rengi vardır —
     * marka tutarlılığı yerine oyunbaz/eğlenceli bir çeşitlilik hedeflenir.
     * Anlamlı renkler (AMBER=en iyi, CORAL=sil, GREEN=koru) bundan bağımsızdır.
     */
    object Screens {
        val ALBUMS = Accent(0xFF3B82F6.toInt(), 0xFF2563EB.toInt())   // mavi
        val RESULTS = Accent(0xFFF97316.toInt(), 0xFFEA580C.toInt())  // turuncu
        val GROUP = Accent(0xFF8B5CF6.toInt(), 0xFF7C3AED.toInt())    // mor
        val SETTINGS = Accent(0xFF22C55E.toInt(), 0xFF16A34A.toInt()) // yeşil
        val HISTORY = Accent(0xFFEC4899.toInt(), 0xFFDB2777.toInt())  // pembe
    }

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun bg(context: Context) = if (isDark(context)) 0xFF101816.toInt() else 0xFFF2FAF8.toInt()
    fun card(context: Context) = if (isDark(context)) 0xFF1B2724.toInt() else WHITE
    fun cardAlt(context: Context) = if (isDark(context)) 0xFF223330.toInt() else 0xFFE9F6F3.toInt()
    fun text(context: Context) = if (isDark(context)) 0xFFEAF4F1.toInt() else 0xFF122220.toInt()
    fun textDim(context: Context) = if (isDark(context)) 0xFF9AB0AB.toInt() else 0xFF5F7370.toInt()

    // ---- Ölçüler ----
    fun dp(context: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
        ).toInt()

    // ---- Kutular ----
    fun vbox(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun hbox(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun weight(view: View, w: Float): View {
        view.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, w
        )
        return view
    }

    // ---- Ekran kökü: arka plan + sistem çubuğu renkleri ----
    fun screenRoot(activity: Activity, statusBarColor: Int = TEAL_DARK): LinearLayout {
        activity.window.statusBarColor = statusBarColor
        activity.window.navigationBarColor = bg(activity)
        return vbox(activity).apply { setBackgroundColor(bg(activity)) }
    }

    // ---- Gradyan başlık ----
    fun gradientHeader(
        context: Context,
        title: String,
        subtitle: String? = null,
        accent: Accent = Accent(TEAL, TEAL_DARK)
    ): LinearLayout {
        val header = vbox(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(accent.main, accent.dark)
            )
            val p = dp(context, 16)
            setPadding(p, dp(context, 14), p, dp(context, 14))
        }
        header.addView(TextView(context).apply {
            text = title
            textSize = 22f
            setTextColor(WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        subtitle?.let {
            header.addView(TextView(context).apply {
                text = it
                textSize = 13f
                setTextColor(0xE6FFFFFF.toInt())
                setPadding(0, dp(context, 2), 0, 0)
            })
        }
        return header
    }

    // ---- Yuvarlak köşeli kart ----
    fun roundedRect(
        fill: Int,
        radiusDp: Float,
        context: Context,
        strokeColor: Int = 0,
        strokeDp: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(context, radiusDp.toInt()).toFloat()
        if (strokeDp > 0) setStroke(dp(context, strokeDp), strokeColor)
    }

    fun cardify(
        view: View,
        fill: Int,
        radiusDp: Int = 20,
        strokeColor: Int = 0,
        strokeDp: Int = 0,
        elevationDp: Int = 2
    ) {
        view.background = roundedRect(fill, radiusDp.toFloat(), view.context, strokeColor, strokeDp)
        view.elevation = dp(view.context, elevationDp).toFloat()
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
        view.clipToOutline = true
    }

    // ---- Hap (pill) buton ----
    fun pillButton(
        context: Context,
        label: String,
        fill: Int = TEAL,
        textColor: Int = WHITE,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = label
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        gravity = Gravity.CENTER
        val ph = dp(context, 20)
        val pv = dp(context, 13)
        setPadding(ph, pv, ph, pv)
        background = RippleDrawable(
            ColorStateList.valueOf(0x33000000),
            roundedRect(fill, 28f, context),
            null
        )
        elevation = dp(context, 3).toFloat()
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun smallButton(
        context: Context,
        label: String,
        fill: Int,
        textColor: Int = WHITE,
        onClick: () -> Unit
    ): TextView = pillButton(context, label, fill, textColor, onClick).apply {
        textSize = 13f
        val ph = dp(context, 14)
        val pv = dp(context, 8)
        setPadding(ph, pv, ph, pv)
        elevation = 0f
    }

    // ---- Rozet / çip ----
    fun chip(context: Context, label: String, bgColor: Int, fgColor: Int = WHITE): TextView =
        TextView(context).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fgColor)
            val ph = dp(context, 9)
            val pv = dp(context, 4)
            setPadding(ph, pv, ph, pv)
            background = roundedRect(bgColor, 12f, context)
        }

    // ---- Metinler ----
    fun sectionTitle(context: Context, textStr: String): TextView = TextView(context).apply {
        text = textStr
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(text(context))
        setPadding(0, dp(context, 18), 0, dp(context, 2))
    }

    fun body(context: Context, textStr: String, dim: Boolean = false): TextView =
        TextView(context).apply {
            text = textStr
            textSize = 13f
            setTextColor(if (dim) textDim(context) else text(context))
        }

    /** Kısa geçici mesaj. */
    fun toast(context: Context, message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
