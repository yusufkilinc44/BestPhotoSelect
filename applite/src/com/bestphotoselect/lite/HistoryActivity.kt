package com.bestphotoselect.lite

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.bestphotoselect.R
import com.bestphotoselect.lite.Ui.dp
import com.bestphotoselect.util.formatBytes
import java.text.DateFormat
import java.util.Date

class HistoryActivity : Activity() {

    private var entries: List<HistoryStore.Entry> = emptyList()
    private lateinit var adapter: HistoryAdapter
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.screenRoot(this, statusBarColor = Ui.Screens.HISTORY.dark)

        val header = Ui.hbox(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Ui.Screens.HISTORY.main, Ui.Screens.HISTORY.dark)
            )
            val p = dp(this@HistoryActivity, 16)
            setPadding(p, dp(this@HistoryActivity, 12), p, dp(this@HistoryActivity, 12))
        }
        header.addView(Ui.weight(TextView(this).apply {
            text = "🕘 " + getString(R.string.history_title)
            textSize = 20f
            setTextColor(Ui.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, 1f))
        header.addView(Ui.smallButton(this, getString(R.string.history_clear), 0x33FFFFFF, Ui.WHITE) {
            HistoryStore.clear(this)
            refresh()
        })
        root.addView(header)

        empty = Ui.body(this, getString(R.string.history_empty), dim = true).apply {
            setPadding(dp(this@HistoryActivity, 16), dp(this@HistoryActivity, 16), 0, 0)
        }
        root.addView(empty)

        val list = ListView(this).apply {
            divider = null
            setSelector(android.R.color.transparent)
        }
        adapter = HistoryAdapter()
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        entries = HistoryStore.all(this)
        empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = this@HistoryActivity
            val e = entries[position]

            val card = Ui.hbox(ctx)
            Ui.cardify(card, Ui.card(ctx), radiusDp = 14, elevationDp = 1)
            val p = dp(ctx, 12)
            card.setPadding(p, dp(ctx, 10), p, dp(ctx, 10))

            val texts = Ui.vbox(ctx)
            texts.addView(TextView(ctx).apply {
                text = e.name
                textSize = 14f
                setTextColor(Ui.text(ctx))
                maxLines = 1
            })
            val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            texts.addView(Ui.body(ctx,
                df.format(Date(e.atMs)) + " · " + formatBytes(e.sizeBytes) + " · " +
                    getString(if (e.trashed) R.string.history_trashed else R.string.history_deleted),
                dim = true
            ))
            card.addView(Ui.weight(texts, 1f))
            card.addView(
                Ui.chip(
                    ctx,
                    getString(if (e.auto) R.string.history_auto_badge else R.string.history_manual_badge),
                    if (e.auto) Ui.AMBER else Ui.Screens.HISTORY.main
                )
            )

            val wrapper = FrameLayout(ctx)
            wrapper.setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 4))
            wrapper.addView(card)
            return wrapper
        }
    }
}
