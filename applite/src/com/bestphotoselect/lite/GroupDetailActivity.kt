package com.bestphotoselect.lite

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
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

        val root = Ui.vbox(this)
        val header = Ui.hbox(this)
        header.addView(Ui.weight(Ui.title(this, getString(R.string.group_title)), 1f))
        header.addView(Button(this).apply {
            text = getString(R.string.group_skip)
            setOnClickListener {
                ScanSession.skipGroup(groupId)
                finish()
            }
        })
        root.addView(header)

        val list = ListView(this).apply { divider = null }
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

            val card = Ui.vbox(ctx).apply {
                setBackgroundColor(0x14808080)
                val m = dp(ctx, 8)
                setPadding(m, m, m, m)
            }

            val img = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 260)
                )
            }
            Thumbs.load(ctx, img, scored.photo.uri, 640)
            card.addView(img)

            val info = TextView(ctx).apply {
                setPadding(0, dp(ctx, 6), 0, 0)
                val parts = mutableListOf(
                    getString(R.string.group_score, (scored.score * 100).roundToInt())
                )
                scored.analysis.face?.let { face ->
                    parts += getString(R.string.group_eyes) + ": %" + (face.eyesOpen * 100).roundToInt()
                    parts += getString(R.string.group_face) + ": %" + (face.frontal * 100).roundToInt()
                }
                text = parts.joinToString("   ")
            }
            card.addView(info)

            val actions = Ui.hbox(ctx)
            if (isBest) {
                actions.addView(TextView(ctx).apply {
                    text = "★ " + getString(R.string.results_best_badge)
                    setTextColor(Color.rgb(15, 118, 110))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
            } else {
                val check = CheckBox(ctx).apply {
                    text = getString(R.string.group_delete_marked)
                    isChecked = scored.markedForDeletion
                    setOnClickListener {
                        ScanSession.toggleDeletion(groupId, scored.photo.id)
                        notifyDataSetChanged()
                    }
                }
                actions.addView(Ui.weight(check, 1f))
                actions.addView(Button(ctx).apply {
                    text = getString(R.string.group_set_best)
                    setOnClickListener {
                        ScanSession.setBest(groupId, scored.photo.id)
                        notifyDataSetChanged()
                    }
                })
            }
            card.addView(actions)

            val wrapper = Ui.vbox(ctx)
            val m = dp(ctx, 8)
            wrapper.setPadding(m, m / 2, m, m / 2)
            wrapper.addView(card)
            return wrapper
        }
    }
}
