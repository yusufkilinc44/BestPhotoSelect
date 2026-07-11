package com.bestphotoselect.lite

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
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
        val root = Ui.vbox(this)
        val header = Ui.hbox(this)
        header.addView(Ui.weight(Ui.title(this, getString(R.string.history_title)), 1f))
        header.addView(Button(this).apply {
            text = getString(R.string.history_clear)
            setOnClickListener {
                HistoryStore.clear(this@HistoryActivity)
                refresh()
            }
        })
        root.addView(header)

        empty = TextView(this).apply {
            text = getString(R.string.history_empty)
            setPadding(dp(this@HistoryActivity, 16), dp(this@HistoryActivity, 16), 0, 0)
        }
        root.addView(empty)

        val list = ListView(this)
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
            val row = Ui.vbox(ctx).apply {
                val p = dp(ctx, 12)
                setPadding(p, dp(ctx, 8), p, dp(ctx, 8))
            }
            row.addView(TextView(ctx).apply {
                text = e.name
                maxLines = 1
            })
            row.addView(TextView(ctx).apply {
                textSize = 12f
                val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                text = df.format(Date(e.atMs)) +
                    " · " + formatBytes(e.sizeBytes) +
                    " · " + getString(if (e.trashed) R.string.history_trashed else R.string.history_deleted) +
                    " · " + getString(if (e.auto) R.string.history_auto_badge else R.string.history_manual_badge)
            })
            return row
        }
    }
}
