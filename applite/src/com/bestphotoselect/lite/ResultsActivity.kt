package com.bestphotoselect.lite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.bestphotoselect.R
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.PhotoItem
import com.bestphotoselect.lite.Ui.dp
import com.bestphotoselect.util.formatBytes

class ResultsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var summary: TextView
    private lateinit var deleteButton: Button
    private lateinit var adapter: GroupAdapter

    private var pendingDeletion: List<PhotoItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = Ui.vbox(this)
        root.addView(Ui.title(this, getString(R.string.results_title)))
        summary = TextView(this).apply {
            setPadding(dp(this@ResultsActivity, 16), 0, dp(this@ResultsActivity, 16), dp(this@ResultsActivity, 8))
        }
        root.addView(summary)

        val list = ListView(this).apply { divider = null }
        adapter = GroupAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val group = ScanSession.groups.getOrNull(position) ?: return@setOnItemClickListener
            startActivity(
                Intent(this, GroupDetailActivity::class.java).putExtra("groupId", group.id)
            )
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        deleteButton = Button(this).apply { setOnClickListener { confirmDelete() } }
        root.addView(deleteButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(this@ResultsActivity, 16), 0, dp(this@ResultsActivity, 16), dp(this@ResultsActivity, 16)) })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val groups = ScanSession.groups
        val candidates = groups.sumOf { it.deletionCandidates.size }
        val bytes = groups.sumOf { it.bytesToFree }
        summary.text = if (groups.isEmpty()) getString(R.string.results_empty)
        else getString(R.string.results_summary, groups.size, candidates, formatBytes(bytes))
        deleteButton.text = getString(R.string.results_delete_selected, candidates)
        deleteButton.isEnabled = candidates > 0
        adapter.notifyDataSetChanged()
    }

    // ---------- Silme akışı ----------

    private fun confirmDelete() {
        val photos = ScanSession.groups.flatMap { it.deletionCandidates }.map { it.photo }
        if (photos.isEmpty()) return
        val bytes = photos.sumOf { it.sizeBytes }
        val note = if (prefs.trashMode) getString(R.string.confirm_delete_trash_note)
        else getString(R.string.confirm_delete_permanent_note)
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(
                getString(R.string.confirm_delete_message, photos.size, formatBytes(bytes)) +
                    "\n\n" + note
            )
            .setPositiveButton(R.string.delete) { _, _ -> launchSystemDelete(photos) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun launchSystemDelete(photos: List<PhotoItem>) {
        pendingDeletion = photos
        val sender = Deleter.buildRequest(this, photos, prefs.trashMode).intentSender
        startIntentSenderForResult(sender, REQ_DELETE, null, 0, 0, 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_DELETE) return
        val photos = pendingDeletion
        pendingDeletion = emptyList()
        if (resultCode == RESULT_OK && photos.isNotEmpty()) {
            HistoryStore.addAll(this, photos, auto = false, trashed = prefs.trashMode)
            ScanSession.onPhotosDeleted(photos.map { it.id }.toSet())
            Toast.makeText(this, getString(R.string.delete_success, photos.size), Toast.LENGTH_LONG).show()
            refresh()
        } else if (resultCode != RESULT_OK) {
            Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Grup kartları ----------

    private inner class GroupAdapter : BaseAdapter() {
        override fun getCount() = ScanSession.groups.size
        override fun getItem(position: Int) = ScanSession.groups[position]
        override fun getItemId(position: Int) = ScanSession.groups[position].id.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = this@ResultsActivity
            val group = ScanSession.groups[position]

            val card = Ui.vbox(ctx).apply {
                setBackgroundColor(0x14808080)
                val m = dp(ctx, 8)
                setPadding(m, m, m, m)
            }
            val titleRow = Ui.hbox(ctx)
            titleRow.addView(Ui.weight(TextView(ctx).apply {
                text = getString(R.string.results_group_title, group.photos.size)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, 1f))
            titleRow.addView(TextView(ctx).apply {
                text = formatBytes(group.bytesToFree)
            })
            card.addView(titleRow)

            val scroll = HorizontalScrollView(ctx)
            val row = Ui.hbox(ctx)
            group.photos.take(8).forEach { scored ->
                val cell = Ui.vbox(ctx).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, dp(ctx, 6), dp(ctx, 6), 0)
                }
                val img = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 96), dp(ctx, 96))
                }
                Thumbs.load(ctx, img, scored.photo.uri, 192)
                cell.addView(img)
                cell.addView(TextView(ctx).apply {
                    textSize = 11f
                    gravity = Gravity.CENTER_HORIZONTAL
                    if (scored.photo.id == group.bestPhotoId) {
                        text = "★ " + getString(R.string.results_best_badge)
                        setTextColor(Color.rgb(15, 118, 110))
                    } else if (scored.markedForDeletion) {
                        text = getString(R.string.group_delete_marked)
                        setTextColor(Color.rgb(200, 40, 40))
                    } else {
                        text = getString(R.string.group_keep)
                    }
                })
                row.addView(cell)
            }
            scroll.addView(row)
            card.addView(scroll)

            // ListView satır dolgusu
            val wrapper = Ui.vbox(ctx)
            val m = dp(ctx, 8)
            wrapper.setPadding(m, m / 2, m, m / 2)
            wrapper.addView(card)
            return wrapper
        }
    }

    companion object {
        private const val REQ_DELETE = 200
    }
}
