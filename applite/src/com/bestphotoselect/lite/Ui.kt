package com.bestphotoselect.lite

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Programatik arayüz yardımcıları (layout XML'i kullanılmaz). */
object Ui {
    fun dp(context: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
        ).toInt()

    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 22f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        val p = dp(context, 16)
        setPadding(p, p, p, dp(context, 8))
    }

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
}
