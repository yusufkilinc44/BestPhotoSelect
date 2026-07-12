package com.bestphotoselect.lite

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bestphotoselect.R
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScoredPhoto
import com.bestphotoselect.lite.Ui.dp
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tam ekran fotoğraf incelemesi: yüksek çözünürlük, pinch/çift-dokunuş zoom,
 * grup içinde gezinme ve "en iyi yap / sil işaretle" onay aksiyonları.
 * Kullanıcı, yapay zekanın seçimini burada büyüterek doğrular.
 */
class PhotoViewerActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var groupId = -1
    private var index = 0

    private lateinit var zoomView: ZoomImageView
    private lateinit var badgeRow: LinearLayout
    private lateinit var counter: TextView
    private lateinit var bestButton: TextView
    private lateinit var deleteButton: TextView
    private var loadToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = intent.getIntExtra("groupId", -1)
        index = intent.getIntExtra("index", 0)
        window.statusBarColor = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }

        zoomView = ZoomImageView(this) { direction -> show(index + direction) }
        root.addView(zoomView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Üst çubuk: kapat + rozetler
        val top = Ui.hbox(this).apply {
            val p = dp(this@PhotoViewerActivity, 12)
            setPadding(p, p, p, p)
        }
        top.addView(TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Ui.WHITE)
            gravity = Gravity.CENTER
            val s = dp(this@PhotoViewerActivity, 40)
            layoutParams = LinearLayout.LayoutParams(s, s)
            background = Ui.roundedRect(0x66000000, 20f, this@PhotoViewerActivity)
            isClickable = true
            setOnClickListener { finish() }
        })
        badgeRow = Ui.hbox(this).apply {
            setPadding(dp(this@PhotoViewerActivity, 10), 0, 0, 0)
        }
        top.addView(badgeRow)
        root.addView(top, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))

        // Alt panel: gezinme + aksiyonlar
        val bottom = Ui.vbox(this).apply {
            val p = dp(this@PhotoViewerActivity, 12)
            setPadding(p, p, p, dp(this@PhotoViewerActivity, 20))
        }
        val nav = Ui.hbox(this).apply { gravity = Gravity.CENTER }
        nav.addView(navButton("‹") { show(index - 1) })
        counter = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(this@PhotoViewerActivity, 18), 0, dp(this@PhotoViewerActivity, 18), 0)
        }
        nav.addView(counter)
        nav.addView(navButton("›") { show(index + 1) })
        bottom.addView(nav)

        val actions = Ui.hbox(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(this@PhotoViewerActivity, 10), 0, 0)
        }
        bestButton = Ui.smallButton(this, getString(R.string.viewer_make_best), Ui.AMBER) {
            currentGroup()?.let { g ->
                g.photos.getOrNull(index)?.let { sp ->
                    ScanSession.setBest(groupId, sp.photo.id)
                    refreshOverlay()
                }
            }
        }
        deleteButton = Ui.smallButton(this, getString(R.string.viewer_mark_delete), Ui.CORAL) {
            currentGroup()?.let { g ->
                g.photos.getOrNull(index)?.let { sp ->
                    ScanSession.toggleDeletion(groupId, sp.photo.id)
                    refreshOverlay()
                }
            }
        }
        actions.addView(bestButton)
        actions.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(this@PhotoViewerActivity, 10), 1)
        })
        actions.addView(deleteButton)
        bottom.addView(actions)

        root.addView(bottom, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))

        setContentView(root)
        show(index)
    }

    private fun navButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 26f
            setTextColor(Ui.WHITE)
            gravity = Gravity.CENTER
            val s = dp(this@PhotoViewerActivity, 46)
            layoutParams = LinearLayout.LayoutParams(s, s)
            background = Ui.roundedRect(0x66000000, 23f, this@PhotoViewerActivity)
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun currentGroup(): PhotoGroup? = ScanSession.group(groupId)

    private fun show(newIndex: Int) {
        val group = currentGroup() ?: run { finish(); return }
        if (newIndex < 0 || newIndex >= group.photos.size) return
        index = newIndex
        refreshOverlay()
        loadImage(group.photos[index])
    }

    private fun refreshOverlay() {
        val group = currentGroup() ?: run { finish(); return }
        if (index >= group.photos.size) index = group.photos.size - 1
        val scored = group.photos[index]
        val isBest = scored.photo.id == group.bestPhotoId

        counter.text = getString(R.string.viewer_counter, index + 1, group.photos.size)

        badgeRow.removeAllViews()
        badgeRow.addView(
            Ui.chip(
                this,
                getString(R.string.group_score, (scored.score * 100).roundToInt()),
                0x66000000, Ui.WHITE
            )
        )
        badgeRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(this@PhotoViewerActivity, 6), 1)
        })
        // Silinecek işareti her zaman öncelikli gösterilir — "en iyi" fotoğraf da
        // silinmeye işaretlenebilir (kullanıcı yapay zekanın seçimine katılmayabilir).
        if (scored.markedForDeletion) {
            badgeRow.addView(Ui.chip(this, getString(R.string.viewer_will_delete), Ui.CORAL))
        } else if (isBest) {
            badgeRow.addView(Ui.chip(this, "★ " + getString(R.string.results_best_badge), Ui.AMBER))
        } else {
            badgeRow.addView(Ui.chip(this, getString(R.string.group_keep), Ui.GREEN))
        }
        scored.analysis.face?.let { face ->
            badgeRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(this@PhotoViewerActivity, 6), 1)
            })
            badgeRow.addView(
                Ui.chip(
                    this,
                    "👁 %" + (face.eyesOpen * 100).roundToInt(),
                    0x66000000, Ui.WHITE
                )
            )
        }

        // "En iyi"yi tekrar en iyi yapmanın anlamı yok, o buton gizlenir; ama
        // silme işareti "en iyi" fotoğrafta da her zaman değiştirilebilir olmalı.
        bestButton.visibility = if (isBest) View.GONE else View.VISIBLE
        deleteButton.text = if (scored.markedForDeletion) {
            getString(R.string.viewer_unmark_delete)
        } else {
            getString(R.string.viewer_mark_delete)
        }
    }

    private fun loadImage(scored: ScoredPhoto) {
        val token = ++loadToken
        val uri = scored.photo.uri
        executor.execute {
            val bmp = try {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Ekranı dolduracak kadar çözünürlük yeterli (bellek dostu)
                    val maxSide = 2048
                    val w = info.size.width
                    val h = info.size.height
                    val scale = min(1f, maxSide.toFloat() / max(w, h))
                    if (scale < 1f) {
                        decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                    }
                }
            } catch (t: Throwable) {
                null
            }
            main.post {
                if (token == loadToken && bmp != null) {
                    zoomView.setBitmap(bmp)
                }
            }
        }
    }

    /**
     * Matrix tabanlı zoom/pan görünümü: pinch (1x..6x), çift dokunuş (1x <-> 2.5x),
     * zoom yokken yatay fling ile önceki/sonraki fotoğraf.
     *
     * Model: içerik ekranda (tx, ty) sol-üst konumunda, toplam ölçek
     * s = fitScale * userScale ile çizilir; sınırlar her uygulamada kısıtlanır.
     */
    private class ZoomImageView(
        context: Context,
        private val onSwipe: (Int) -> Unit
    ) : ImageView(context) {

        private val drawMatrix = Matrix()
        private var userScale = 1f
        private var tx = 0f
        private var ty = 0f
        private var bmpW = 0f
        private var bmpH = 0f

        init {
            scaleType = ScaleType.MATRIX
        }

        private fun fitScale(): Float =
            if (bmpW <= 0f || width == 0) 1f else min(width / bmpW, height / bmpH)

        private fun contentW() = bmpW * fitScale() * userScale
        private fun contentH() = bmpH * fitScale() * userScale

        private val scaleDetector = android.view.ScaleGestureDetector(
            context,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                    val newScale = (userScale * d.scaleFactor).coerceIn(1f, 6f)
                    val applied = newScale / userScale
                    userScale = newScale
                    tx = (tx - d.focusX) * applied + d.focusX
                    ty = (ty - d.focusY) * applied + d.focusY
                    applyMatrix()
                    return true
                }
            }
        )

        private val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (userScale > 1.05f) {
                        userScale = 1f
                    } else {
                        val applied = 2.5f
                        userScale = applied
                        tx = (tx - e.x) * applied + e.x
                        ty = (ty - e.y) * applied + e.y
                    }
                    applyMatrix()
                    return true
                }

                override fun onScroll(
                    e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
                ): Boolean {
                    if (userScale > 1.05f) {
                        tx -= dx
                        ty -= dy
                        applyMatrix()
                    }
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
                ): Boolean {
                    if (userScale <= 1.05f && e1 != null) {
                        val dx = e2.x - e1.x
                        if (dx < -150 && vx < -400) onSwipe(1)
                        else if (dx > 150 && vx > 400) onSwipe(-1)
                    }
                    return true
                }
            }
        )

        fun setBitmap(bmp: Bitmap) {
            bmpW = bmp.width.toFloat()
            bmpH = bmp.height.toFloat()
            userScale = 1f
            setImageBitmap(bmp)
            applyMatrix()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            applyMatrix()
        }

        private fun applyMatrix() {
            if (bmpW <= 0f || width == 0) return
            val cw = contentW()
            val ch = contentH()
            // Sınırlar: içerik ekrandan küçükse ortala, büyükse boşluk bırakma
            tx = if (cw <= width) (width - cw) / 2f else tx.coerceIn(width - cw, 0f)
            ty = if (ch <= height) (height - ch) / 2f else ty.coerceIn(height - ch, 0f)

            val s = fitScale() * userScale
            drawMatrix.reset()
            drawMatrix.postScale(s, s)
            drawMatrix.postTranslate(tx, ty)
            imageMatrix = drawMatrix
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            return true
        }
    }
}
