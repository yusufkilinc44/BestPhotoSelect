package com.bestphotoselect.lite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.bestphotoselect.R
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.PhotoItem
import com.bestphotoselect.lite.Ui.dp
import com.bestphotoselect.util.formatBytes

class ResultsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var hero: TextView
    private lateinit var heroCard: LinearLayout
    private lateinit var deleteButton: TextView
    private lateinit var adapter: GroupAdapter

    private var pendingDeletion: List<PhotoItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = Ui.screenRoot(this)
        root.addView(Ui.gradientHeader(this, "🖼 " + getString(R.string.results_title),
            getString(R.string.group_tap_hint)))

        // Kazanım "hero" kartı: mercan -> amber gradyan
        heroCard = Ui.vbox(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.CORAL, Ui.AMBER)
            ).apply { cornerRadius = dp(this@ResultsActivity, 18).toFloat() }
            elevation = dp(this@ResultsActivity, 3).toFloat()
            val p = dp(this@ResultsActivity, 14)
            setPadding(p, p, p, p)
        }
        heroCard.addView(TextView(this).apply {
            text = getString(R.string.results_hero_title)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.WHITE)
        })
        hero = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.WHITE)
            setPadding(0, dp(this@ResultsActivity, 2), 0, 0)
        }
        heroCard.addView(hero)
        root.addView(heroCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(this@ResultsActivity, 16), dp(this@ResultsActivity, 12), dp(this@ResultsActivity, 16), dp(this@ResultsActivity, 4))
        })

        val list = ListView(this).apply {
            divider = null
            setSelector(android.R.color.transparent)
        }
        adapter = GroupAdapter()
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        deleteButton = Ui.pillButton(this, "", Ui.CORAL) { confirmDelete() }
        root.addView(deleteButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(this@ResultsActivity, 16), dp(this@ResultsActivity, 6), dp(this@ResultsActivity, 16), dp(this@ResultsActivity, 16))
        })

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
        if (groups.isEmpty()) {
            hero.text = getString(R.string.results_empty)
        } else {
            hero.text = getString(R.string.results_summary, groups.size, candidates, formatBytes(bytes))
        }
        deleteButton.text = "🗑 " + getString(R.string.results_delete_selected, candidates)
        deleteButton.alpha = if (candidates > 0) 1f else 0.45f
        adapter.notifyDataSetChanged()
    }

    private fun openGroup(group: PhotoGroup) {
        startActivity(
            Intent(this, GroupDetailActivity::class.java).putExtra("groupId", group.id)
        )
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
            Ui.toast(this, "✅ " + getString(R.string.delete_success, photos.size))
            refresh()
        } else if (resultCode != RESULT_OK) {
            Ui.toast(this, getString(R.string.delete_failed))
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
            val open = { openGroup(group) }

            val card = Ui.vbox(ctx)
            Ui.cardify(card, Ui.card(ctx), radiusDp = 18, elevationDp = 2)
            card.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))

            val titleRow = Ui.hbox(ctx)
            titleRow.addView(Ui.weight(TextView(ctx).apply {
                text = "🖼 " + getString(R.string.results_group_title, group.photos.size)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(ctx))
            }, 1f))
            titleRow.addView(Ui.chip(ctx, formatBytes(group.bytesToFree), Ui.cardAlt(ctx), Ui.TEAL_DARK))
            card.addView(titleRow)

            val scroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
            }
            val row = Ui.hbox(ctx)
            group.photos.take(10).forEach { scored ->
                val isBest = scored.photo.id == group.bestPhotoId
                val cell = FrameLayout(ctx).apply {
                    val s = dp(ctx, 104)
                    layoutParams = LinearLayout.LayoutParams(s, s).apply {
                        setMargins(0, dp(ctx, 10), dp(ctx, 8), 0)
                    }
                }
                Ui.cardify(
                    cell, Ui.cardAlt(ctx), radiusDp = 14,
                    strokeColor = if (isBest) Ui.TEAL else 0,
                    strokeDp = if (isBest) 3 else 0,
                    elevationDp = 0
                )
                val img = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                Thumbs.load(ctx, img, scored.photo.uri, 208)
                cell.addView(img)
                if (isBest) {
                    cell.addView(Ui.chip(ctx, "★ " + getString(R.string.results_best_badge), Ui.TEAL).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        ).apply { bottomMargin = dp(ctx, 6) }
                    })
                } else if (scored.markedForDeletion) {
                    cell.addView(View(ctx).apply {
                        setBackgroundColor(0x40E05252)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    })
                    cell.addView(Ui.chip(ctx, "🗑", Ui.CORAL).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        ).apply { bottomMargin = dp(ctx, 6) }
                    })
                }
                // Küçük resme dokununca da grup detayına gir
                cell.isClickable = true
                cell.setOnClickListener { open() }
                row.addView(cell)
            }
            scroll.addView(row)
            card.addView(scroll)

            // KÖK NEDEN DÜZELTMESİ: HorizontalScrollView, ListView öğe tıklamasını
            // engellediğinden kartın kendisine tıklama dinleyicisi bağlanır.
            card.isClickable = true
            card.setOnClickListener { open() }

            val wrapper = FrameLayout(ctx)
            val m = dp(ctx, 16)
            wrapper.setPadding(m, dp(ctx, 6), m, dp(ctx, 6))
            wrapper.addView(card)
            return wrapper
        }
    }

    companion object {
        private const val REQ_DELETE = 200
    }
}
