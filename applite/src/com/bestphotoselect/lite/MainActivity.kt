package com.bestphotoselect.lite

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.bestphotoselect.R
import com.bestphotoselect.data.model.Album
import com.bestphotoselect.lite.Ui.dp
import com.bestphotoselect.util.formatBytes
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bilerek EN BASİT mümkün yapı: TEK bir dikey LinearLayout, TEK bir ScrollView
 * içinde, hiçbir yerde "weight" (ağırlıklı alan paylaşımı) kullanılmadan.
 * Her çocuk WRAP_CONTENT'tir ve doğal boyutuyla alt alta dizilir; tarama butonu
 * da bu sütunun EN ALTINDA, kaydırmayla birlikte gelir. Bu, ekranın herhangi
 * bir kısmının "kaybolması" ihtimalini ortadan kaldırır.
 */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var albums: List<Album> = emptyList()
    private var loading = true
    private var selected: MutableSet<Long> = mutableSetOf()

    private lateinit var infoText: TextView
    private lateinit var statusText: TextView
    private lateinit var gridContainer: LinearLayout
    private lateinit var scanButton: TextView
    private lateinit var topScanButton: TextView

    private var scanCancelled = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        selected = prefs.selectedBuckets.toMutableSet()
        buildUi()
        if (hasPermission()) loadAlbums() else requestPermission()
    }

    // ---------- İzin ----------

    private fun permissionName(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPermission(): Boolean {
        if (checkSelfPermission(permissionName()) == PackageManager.PERMISSION_GRANTED) return true
        // Android 14+ "sınırlı erişim" (yalnızca seçili fotoğraflar) verildiyse de
        // MediaStore sorguları o fotoğraflar için çalışır; bunu da izin say.
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    private fun requestPermission() {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(perms, 100)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (hasPermission()) {
            loadAlbums()
        } else {
            loading = false
            renderGrid()
        }
    }

    // ---------- Arayüz iskeleti (tek sütun, weight yok) ----------

    private fun buildUi() {
        // setContentView'e verilen kök, MATCH_PARENT/MATCH_PARENT olarak
        // Activity tarafından otomatik sarılır; kendi layoutParams'ını
        // belirtmemize gerek yok.
        window.statusBarColor = Ui.Screens.ALBUMS.dark
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Ui.bg(this@MainActivity))
        }
        val column = Ui.vbox(this)
        scroll.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        // Başlık
        val header = Ui.hbox(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.Screens.ALBUMS.main, Ui.Screens.ALBUMS.dark)
            )
            val p = dp(this@MainActivity, 16)
            setPadding(p, dp(this@MainActivity, 12), p, dp(this@MainActivity, 12))
        }
        val titles = Ui.vbox(this)
        titles.addView(TextView(this).apply {
            text = "📸 " + getString(R.string.app_name)
            textSize = 21f
            setTextColor(Ui.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        infoText = TextView(this).apply {
            text = getString(R.string.albums_select_hint)
            textSize = 12f
            setTextColor(0xE6FFFFFF.toInt())
        }
        titles.addView(infoText)
        header.addView(titles, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))
        header.addView(headerIcon("🕘") { startActivity(Intent(this, HistoryActivity::class.java)) })
        header.addView(headerIcon("⚙️") { startActivity(Intent(this, SettingsActivity::class.java)) })
        column.addView(header)

        // Teşhis satırı: her zaman görünür, sorun anında ekran görüntüsünden anlaşılsın
        statusText = TextView(this).apply {
            textSize = 11f
            setTextColor(Ui.textDim(this@MainActivity))
            val p = dp(this@MainActivity, 16)
            setPadding(p, dp(this@MainActivity, 4), p, 0)
        }
        column.addView(statusText)

        // Seçim araç çubuğu: solda "Tara" kısayolu, sağda albüm seçim butonları.
        // Tarama tuşu hem burada (üstte) hem sütunun en altında bulunur.
        val selectRow = Ui.hbox(this).apply {
            val p = dp(this@MainActivity, 16)
            setPadding(p, dp(this@MainActivity, 10), p, dp(this@MainActivity, 4))
        }
        topScanButton = Ui.smallButton(this, "🔍 " + getString(R.string.albums_scan_short), Ui.Screens.ALBUMS.main, Ui.WHITE) {
            if (selected.isNotEmpty()) startScan()
            else Ui.toast(this, getString(R.string.albums_select_hint))
        }
        selectRow.addView(topScanButton)
        selectRow.addView(Ui.weight(View(this), 1f))
        selectRow.addView(Ui.smallButton(this, getString(R.string.albums_select_all), Ui.cardAlt(this), Ui.Screens.ALBUMS.dark) {
            selected = albums.map { it.bucketId }.toMutableSet()
            persistSelection(); renderGrid(); refreshInfo()
        })
        selectRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(this@MainActivity, 8), dp(this@MainActivity, 1))
        })
        selectRow.addView(Ui.smallButton(this, getString(R.string.albums_clear), Ui.cardAlt(this), Ui.Screens.ALBUMS.dark) {
            selected.clear(); persistSelection(); renderGrid(); refreshInfo()
        })
        column.addView(selectRow)

        // Albüm ızgarası (içerik kadar yer kaplar, sütun onu doğal olarak kaydırır)
        gridContainer = Ui.vbox(this).apply {
            val p = dp(this@MainActivity, 12)
            setPadding(p, dp(this@MainActivity, 6), p, dp(this@MainActivity, 6))
        }
        column.addView(gridContainer)

        // Tarama butonu — sütunun EN ALTINDA, her zaman içerikle birlikte görünür
        scanButton = Ui.pillButton(this, "✨ " + getString(R.string.albums_scan), Ui.Screens.ALBUMS.main) {
            if (selected.isNotEmpty()) startScan()
        }
        val buttonWrap = FrameLayout(this).apply {
            val p = dp(this@MainActivity, 16)
            setPadding(p, dp(this@MainActivity, 10), p, dp(this@MainActivity, 28))
        }
        buttonWrap.addView(scanButton)
        column.addView(buttonWrap)

        setContentView(scroll)
        renderGrid()
        refreshInfo()
    }

    private fun headerIcon(emoji: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = emoji
            textSize = 20f
            gravity = Gravity.CENTER
            val s = dp(this@MainActivity, 42)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginStart = dp(this@MainActivity, 8)
            }
            background = Ui.roundedRect(0x33FFFFFF, 21f, this@MainActivity)
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun loadAlbums() {
        loading = true
        renderGrid()
        executor.execute {
            val list = try {
                MediaQuery(this).queryAlbums()
            } catch (t: Throwable) {
                emptyList()
            }
            main.post {
                albums = list
                loading = false
                selected.retainAll(list.map { it.bucketId }.toSet())
                renderGrid()
                refreshInfo()
            }
        }
    }

    private fun persistSelection() {
        prefs.selectedBuckets = selected
    }

    private fun refreshInfo() {
        infoText.text = if (selected.isEmpty()) getString(R.string.albums_select_hint)
        else getString(R.string.albums_selected_count, selected.size)
        scanButton.alpha = if (selected.isEmpty()) 0.45f else 1f
        topScanButton.alpha = if (selected.isEmpty()) 0.45f else 1f
        statusText.text = "v${BuildInfo.VERSION_NAME} · ${albums.size} albüm bulundu · izin: ${if (hasPermission()) "var" else "yok"}"
    }

    // ---------- Grid oluşturma (elle, öngörülebilir, weight yok) ----------

    private fun renderGrid() {
        if (!::gridContainer.isInitialized) return
        gridContainer.removeAllViews()

        if (loading) {
            gridContainer.addView(centerNotice(loadingView()))
            return
        }
        if (!hasPermission()) {
            gridContainer.addView(centerNotice(TextView(this).apply {
                text = getString(R.string.permission_message)
                setTextColor(Ui.text(this@MainActivity))
                gravity = Gravity.CENTER
            }))
            return
        }
        if (albums.isEmpty()) {
            gridContainer.addView(centerNotice(TextView(this).apply {
                text = getString(R.string.albums_empty)
                setTextColor(Ui.textDim(this@MainActivity))
                gravity = Gravity.CENTER
            }))
            return
        }

        var i = 0
        while (i < albums.size) {
            val rowLayout = Ui.hbox(this)
            for (col in 0 until 2) {
                if (i < albums.size) {
                    rowLayout.addView(albumCard(albums[i]), columnParams())
                } else {
                    rowLayout.addView(View(this), columnParams())
                }
                i++
            }
            gridContainer.addView(rowLayout)
        }
    }

    private fun columnParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            val m = dp(this@MainActivity, 6)
            setMargins(m, m, m, m)
        }

    private fun loadingView(): View {
        val box = Ui.vbox(this).apply { gravity = Gravity.CENTER }
        box.addView(ProgressBar(this).apply {
            indeterminateTintList = ColorStateList.valueOf(Ui.Screens.ALBUMS.main)
        })
        box.addView(TextView(this).apply {
            text = "Albümler yükleniyor…"
            setTextColor(Ui.textDim(this@MainActivity))
            gravity = Gravity.CENTER
            setPadding(0, dp(this@MainActivity, 8), 0, 0)
        })
        return box
    }

    private fun centerNotice(child: View): View {
        val frame = FrameLayout(this).apply {
            val p = dp(this@MainActivity, 24)
            setPadding(p, dp(this@MainActivity, 60), p, dp(this@MainActivity, 60))
        }
        child.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        )
        frame.addView(child)
        return frame
    }

    private fun albumCard(album: Album): View {
        val ctx = this
        val isSelected = album.bucketId in selected

        val card = FrameLayout(ctx)
        Ui.cardify(
            card, Ui.card(ctx), radiusDp = 18,
            strokeColor = if (isSelected) Ui.Screens.ALBUMS.main else 0,
            strokeDp = if (isSelected) 3 else 0,
            elevationDp = 3
        )

        val image = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 150)
            )
            alpha = if (isSelected) 0.85f else 1f
        }
        Thumbs.load(ctx, image, album.coverUri, 320)
        card.addView(image)

        val scrim = Ui.vbox(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
            gravity = Gravity.BOTTOM
            val p = dp(ctx, 10)
            setPadding(p, dp(ctx, 26), p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 150), Gravity.BOTTOM
            )
        }
        scrim.addView(TextView(ctx).apply {
            text = album.name
            textSize = 14f
            setTextColor(Ui.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        scrim.addView(TextView(ctx).apply {
            text = getString(R.string.photos_count, album.photoCount) + " · " + formatBytes(album.totalBytes)
            textSize = 11f
            setTextColor(0xE6FFFFFF.toInt())
        })
        card.addView(scrim)

        card.addView(TextView(ctx).apply {
            text = if (isSelected) "✓" else ""
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.WHITE)
            gravity = Gravity.CENTER
            val s = dp(ctx, 28)
            layoutParams = FrameLayout.LayoutParams(s, s, Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(ctx, 8), dp(ctx, 8), 0)
            }
            background = if (isSelected) Ui.roundedRect(Ui.Screens.ALBUMS.main, 14f, ctx)
            else Ui.roundedRect(0x66000000, 14f, ctx, Ui.WHITE, 2)
        })

        card.isClickable = true
        card.setOnClickListener {
            if (album.bucketId in selected) selected.remove(album.bucketId)
            else selected.add(album.bucketId)
            persistSelection(); renderGrid(); refreshInfo()
        }
        return card
    }

    // ---------- Tarama ----------

    private fun startScan() {
        scanCancelled = AtomicBoolean(false)

        val box = Ui.vbox(this).apply {
            val p = dp(this@MainActivity, 24)
            setPadding(p, p, p, p)
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
            progressTintList = ColorStateList.valueOf(Ui.Screens.ALBUMS.main)
            indeterminateTintList = ColorStateList.valueOf(Ui.Screens.ALBUMS.main)
        }
        val msg = TextView(this).apply {
            text = getString(R.string.scan_phase_reading)
            setTextColor(Ui.text(this@MainActivity))
            setPadding(0, dp(this@MainActivity, 12), 0, 0)
        }
        box.addView(bar)
        box.addView(msg)

        val dialog = AlertDialog.Builder(this)
            .setTitle("🔍 " + getString(R.string.scan_title))
            .setView(box)
            .setNegativeButton(R.string.scan_cancel) { _, _ -> scanCancelled.set(true) }
            .setCancelable(false)
            .create()
        dialog.show()

        val buckets = selected.toSet()
        val settings = prefs.toAppSettings()
        executor.execute {
            var failure: Throwable? = null
            val groups = try {
                LiteScanEngine(this).scan(buckets, settings, scanCancelled) { phase, done, total ->
                    main.post {
                        if (total > 0) {
                            bar.isIndeterminate = false
                            bar.progress = done * 100 / total
                        } else {
                            bar.isIndeterminate = true
                        }
                        msg.text = when (phase) {
                            com.bestphotoselect.data.model.ScanPhase.READING -> getString(R.string.scan_phase_reading)
                            com.bestphotoselect.data.model.ScanPhase.HASHING -> getString(R.string.scan_phase_hashing, done, total)
                            com.bestphotoselect.data.model.ScanPhase.GROUPING -> getString(R.string.scan_phase_grouping)
                            com.bestphotoselect.data.model.ScanPhase.SCORING -> getString(R.string.scan_phase_scoring, done, total)
                        }
                    }
                }
            } catch (t: Throwable) {
                failure = t
                emptyList()
            }
            main.post {
                dialog.dismiss()
                failure?.let { t -> Ui.toast(this, getString(R.string.scan_failed, t.javaClass.simpleName)) }
                if (!scanCancelled.get() && failure == null) {
                    ScanSession.groups = groups
                    startActivity(Intent(this, ResultsActivity::class.java))
                }
            }
        }
    }
}
