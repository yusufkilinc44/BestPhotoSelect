package com.bestphotoselect.lite

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.bestphotoselect.R
import com.bestphotoselect.lite.Ui.dp
import kotlin.math.roundToInt

class GroupDetailActivity : Activity() {

    private var groupId = -1
    private lateinit var adapter: MemberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = intent.getIntExtra("groupId", -1)

        val root = Ui.screenRoot(this, statusBarColor = Ui.Screens.GROUP.dark)

        val header = Ui.hbox(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Ui.Screens.GROUP.main, Ui.Screens.GROUP.dark)
            )
            val p = dp(this@GroupDetailActivity, 16)
            setPadding(p, dp(this@GroupDetailActivity, 12), p, dp(this@GroupDetailActivity, 12))
        }
        val titles = Ui.vbox(this)
        titles.addView(TextView(this).apply {
            text = "🔍 " + getString(R.string.group_title)
            textSize = 20f
            setTextColor(Ui.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        titles.addView(TextView(this).apply {
            text = getString(R.string.group_tap_hint)
            textSize = 12f
            setTextColor(0xE6FFFFFF.toInt())
        })
        header.addView(Ui.weight(titles, 1f))
        header.addView(Ui.smallButton(this, getString(R.string.group_skip), 0x33FFFFFF, Ui.WHITE) {
            ScanSession.skipGroup(groupId)
            finish()
        })
        root.addView(header)

        val list = ListView(this).apply {
            divider = null
            setSelector(android.R.color.transparent)
        }
        adapter = MemberAdapter()
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (ScanSession.group(groupId) == null) {
            finish()
        } else {
            adapter.notifyDataSetChanged()
        }
    }

    private fun openViewer(index: Int) {
        startActivity(
            Intent(this, PhotoViewerActivity::class.java)
                .putExtra("groupId", groupId)
                .putExtra("index", index)
        )
    }

    private inner class MemberAdapter : BaseAdapter() {
        private fun members() = ScanSession.group(groupId)?.photos ?: emptyList()
        override fun getCount() = members().size
        override fun getItem(position: Int) = members()[position]
        override fun getItemId(position: Int) = members()[position].photo.id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = this@GroupDetailActivity
            val group = ScanSession.group(groupId) ?: return View(ctx)
            val scored = group.photos[position]
            val isBest = scored.photo.id == group.bestPhotoId

            val card = Ui.vbox(ctx)
            Ui.cardify(
                card, Ui.card(ctx), radiusDp = 18,
                strokeColor = if (isBest) Ui.AMBER else 0,
                strokeDp = if (isBest) 3 else 0,
                elevationDp = 2
            )

            // Fotoğraf (dokununca tam ekran inceleme)
            val imageFrame = FrameLayout(ctx)
            val img = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 280)
                )
            }
            Thumbs.load(ctx, img, scored.photo.uri, 640)
            imageFrame.addView(img)

            // Rozet: sol üst
            val badge = if (isBest) {
                Ui.chip(ctx, "★ " + getString(R.string.results_best_badge), Ui.AMBER)
            } else if (scored.markedForDeletion) {
                Ui.chip(ctx, getString(R.string.viewer_will_delete), Ui.CORAL)
            } else {
                Ui.chip(ctx, getString(R.string.group_keep), Ui.GREEN)
            }
            imageFrame.addView(badge.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply { setMargins(dp(ctx, 10), dp(ctx, 10), 0, 0) }
            })
            // Büyüteç ipucu: sağ alt
            imageFrame.addView(TextView(ctx).apply {
                text = "🔍"
                textSize = 16f
                gravity = Gravity.CENTER
                val s = dp(ctx, 34)
                layoutParams = FrameLayout.LayoutParams(s, s, Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, dp(ctx, 10), dp(ctx, 10))
                }
                background = Ui.roundedRect(0x66000000, 17f, ctx)
            })
            imageFrame.isClickable = true
            imageFrame.setOnClickListener { openViewer(position) }
            card.addView(imageFrame)

            // Bilgi + aksiyon satırı
            val info = Ui.hbox(ctx).apply {
                val p = dp(ctx, 12)
                setPadding(p, dp(ctx, 10), p, dp(ctx, 12))
            }
            val chips = Ui.hbox(ctx)
            chips.addView(
                Ui.chip(
                    ctx,
                    getString(R.string.group_score, (scored.score * 100).roundToInt()),
                    Ui.cardAlt(ctx), Ui.Screens.GROUP.dark
                )
            )
            scored.analysis.face?.let { face ->
                chips.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 6), 1)
                })
                chips.addView(
                    Ui.chip(ctx, "👁 %" + (face.eyesOpen * 100).roundToInt(), Ui.cardAlt(ctx), Ui.Screens.GROUP.dark)
                )
                chips.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 6), 1)
                })
                chips.addView(
                    Ui.chip(ctx, "🙂 %" + (face.frontal * 100).roundToInt(), Ui.cardAlt(ctx), Ui.Screens.GROUP.dark)
                )
            }
            info.addView(Ui.weight(chips, 1f))

            if (!isBest) {
                info.addView(Ui.smallButton(
                    ctx,
                    if (scored.markedForDeletion) getString(R.string.viewer_unmark_delete)
                    else getString(R.string.viewer_mark_delete),
                    if (scored.markedForDeletion) Ui.cardAlt(ctx) else Ui.CORAL,
                    if (scored.markedForDeletion) Ui.Screens.GROUP.dark else Ui.WHITE
                ) {
                    ScanSession.toggleDeletion(groupId, scored.photo.id)
                    notifyDataSetChanged()
                })
                info.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 8), 1)
                })
                info.addView(Ui.smallButton(ctx, "⭐", Ui.AMBER, Ui.WHITE) {
                    ScanSession.setBest(groupId, scored.photo.id)
                    notifyDataSetChanged()
                })
            }
            card.addView(info)

            val wrapper = FrameLayout(ctx)
            val m = dp(ctx, 16)
            wrapper.setPadding(m, dp(ctx, 6), m, dp(ctx, 6))
            wrapper.addView(card)
            return wrapper
        }
    }
}
