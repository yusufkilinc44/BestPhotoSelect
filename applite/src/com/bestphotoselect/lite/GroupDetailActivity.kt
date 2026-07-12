package com.bestphotoselect.lite

import android.app.Activity
import android.app.AlertDialog
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
import com.bestphotoselect.data.model.PhotoItem
import com.bestphotoselect.lite.Ui.dp
import com.bestphotoselect.util.formatBytes
import kotlin.math.roundToInt

/**
 * Grup incelemesi: kullanıcı her fotoğrafı (EN İYİ dahil) silinmeye
 * işaretleyebilir/işaretini kaldırabilir, "en iyi"yi değiştirebilir ve bu
 * grubu tek başına temizleyip (sistem onayıyla) sonra bir sonraki gruba
 * geçebilir — bulk silme için Sonuçlar ekranına dönmek zorunda değildir.
 */
class GroupDetailActivity : Activity() {

    private lateinit var prefs: Prefs
    private var groupId = -1
    private lateinit var adapter: MemberAdapter
    private lateinit var deleteButton: TextView
    private var pendingDeletion: List<PhotoItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
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
        header.addView(Ui.smallButton(this, getString(R.string.group_skip), Ui.WHITE, Ui.Screens.GROUP.dark) {
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

        // Grup içi temizlik: bu grupta işaretlenenleri hemen sil, sonra
        // (grup biterse otomatik, bitmezse geri tuşuyla) diğer gruba geçilebilir.
        deleteButton = Ui.pillButton(this, "", Ui.CORAL, Ui.TEXT_ON_BRIGHT) { confirmDelete() }
        root.addView(deleteButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(this@GroupDetailActivity, 16), dp(this@GroupDetailActivity, 6), dp(this@GroupDetailActivity, 16), dp(this@GroupDetailActivity, 16))
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val group = ScanSession.group(groupId)
        if (group == null) {
            finish()
            return
        }
        val count = group.deletionCandidates.size
        deleteButton.text = "🗑 " + getString(R.string.results_delete_selected, count)
        deleteButton.alpha = if (count > 0) 1f else 0.45f
        adapter.notifyDataSetChanged()
    }

    private fun openViewer(index: Int) {
        startActivity(
            Intent(this, PhotoViewerActivity::class.java)
                .putExtra("groupId", groupId)
                .putExtra("index", index)
        )
    }

    // ---------- Grup içi silme akışı ----------

    private fun confirmDelete() {
        val group = ScanSession.group(groupId) ?: return
        val photos = group.deletionCandidates.map { it.photo }
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
        // MANAGE_MEDIA izni verilmişse sistemin her seferinde sorduğu onay
        // diyaloğu tamamen atlanır; izin yoksa eski onaylı akışa düşülür.
        val startedSilently = Deleter.trySilentDelete(this, photos, prefs.trashMode) { success ->
            onDeleteFinished(photos, success)
        }
        if (!startedSilently) {
            val sender = Deleter.buildRequest(this, photos, prefs.trashMode).intentSender
            startIntentSenderForResult(sender, REQ_DELETE, null, 0, 0, 0)
        }
    }

    private fun onDeleteFinished(photos: List<PhotoItem>, success: Boolean) {
        pendingDeletion = emptyList()
        if (success && photos.isNotEmpty()) {
            HistoryStore.addAll(this, photos, auto = false, trashed = prefs.trashMode)
            ScanSession.onPhotosDeleted(photos.map { it.id }.toSet())
            Ui.toast(this, "✅ " + getString(R.string.delete_success, photos.size))
            // Grup tamamen bittiyse (< 2 kaldıysa) refresh() otomatik finish() çağırır;
            // hâlâ 2+ fotoğraf varsa liste güncellenir, kullanıcı incelemeye devam edebilir.
            refresh()
        } else if (!success) {
            Ui.toast(this, getString(R.string.delete_failed))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_DELETE) return
        onDeleteFinished(pendingDeletion, resultCode == RESULT_OK)
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
            val marked = scored.markedForDeletion

            val card = Ui.vbox(ctx)
            Ui.cardify(
                card, Ui.card(ctx), radiusDp = 18,
                strokeColor = if (marked) Ui.CORAL else if (isBest) Ui.AMBER else 0,
                strokeDp = if (marked || isBest) 3 else 0,
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

            // Rozet: sol üst — silinecek işareti her zaman öncelikli gösterilir,
            // "en iyi" fotoğraf da silinmeye işaretlenmiş olabilir.
            val badge = if (marked) {
                Ui.chip(ctx, getString(R.string.viewer_will_delete), Ui.CORAL, Ui.TEXT_ON_BRIGHT)
            } else if (isBest) {
                Ui.chip(ctx, "★ " + getString(R.string.results_best_badge), Ui.AMBER, Ui.TEXT_ON_BRIGHT)
            } else {
                Ui.chip(ctx, getString(R.string.group_keep), Ui.GREEN, Ui.TEXT_ON_BRIGHT)
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
            val groupTextColor = Ui.Screens.GROUP.onSurface(ctx)
            chips.addView(
                Ui.chip(
                    ctx,
                    getString(R.string.group_score, (scored.score * 100).roundToInt()),
                    Ui.cardAlt(ctx), groupTextColor
                )
            )
            scored.analysis.face?.let { face ->
                chips.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 6), 1)
                })
                chips.addView(
                    Ui.chip(ctx, "👁 %" + (face.eyesOpen * 100).roundToInt(), Ui.cardAlt(ctx), groupTextColor)
                )
                chips.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 6), 1)
                })
                chips.addView(
                    Ui.chip(ctx, "🙂 %" + (face.frontal * 100).roundToInt(), Ui.cardAlt(ctx), groupTextColor)
                )
            }
            info.addView(Ui.weight(chips, 1f))

            // Silinme durumu HER fotoğrafta değiştirilebilir — "en iyi" işaretli
            // fotoğraf da dahil (kullanıcı yapay zekanın seçimine katılmayabilir).
            info.addView(Ui.smallButton(
                ctx,
                if (marked) getString(R.string.viewer_unmark_delete) else getString(R.string.viewer_mark_delete),
                if (marked) Ui.cardAlt(ctx) else Ui.CORAL,
                if (marked) groupTextColor else Ui.TEXT_ON_BRIGHT
            ) {
                ScanSession.toggleDeletion(groupId, scored.photo.id)
                refresh()
            })
            if (!isBest) {
                info.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 8), 1)
                })
                info.addView(Ui.smallButton(ctx, "⭐", Ui.AMBER, Ui.TEXT_ON_BRIGHT) {
                    ScanSession.setBest(groupId, scored.photo.id)
                    refresh()
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

    companion object {
        private const val REQ_DELETE = 210
    }
}
