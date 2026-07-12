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
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.bestphotoselect.R
import com.bestphotoselect.data.model.Album
import com.bestphotoselect.data.model.ScanPhase
import com.bestphotoselect.lite.Ui.dp
import com.bestphotoselect.util.formatBytes
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var albums: List<Album> = emptyList()
    private var selected: MutableSet<Long> = mutableSetOf()

    private lateinit var infoText: TextView
    private lateinit var grid: GridView
    private lateinit var scanButton: TextView
    private lateinit var adapter: AlbumAdapter

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

    private fun hasPermission(): Boolean =
        checkSelfPermission(permissionName()) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        requestPermissions(arrayOf(permissionName()), 100)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (hasPermission()) loadAlbums()
        else infoText.text = getString(R.string.permission_message)
    }

    // ---------- Arayüz ----------

    private fun buildUi() {
        val root = Ui.screenRoot(this)

        // Gradyan başlık: uygulama adı + Geçmiş/Ayarlar kısayolları
        val header = Ui.hbox(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.TEAL, Ui.TEAL_DARK)
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
        header.addView(Ui.weight(titles, 1f))
        header.addView(headerIcon("🕘") {
            startActivity(Intent(this, HistoryActivity::class.java))
        })
        header.addView(headerIcon("⚙️") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        root.addView(header)

        // Seçim araç çubuğu
        val selectRow = Ui.hbox(this).apply {
            val p = dp(this@MainActivity, 16)
            setPadding(p, dp(this@MainActivity, 10), p, dp(this@MainActivity, 4))
        }
        selectRow.addView(Ui.weight(View(this), 1f))
        selectRow.addView(Ui.smallButton(this, getString(R.string.albums_select_all), Ui.cardAlt(this), Ui.TEAL_DARK) {
            selected = albums.map { it.bucketId }.toMutableSet()
            persistSelection(); refresh()
        })
        selectRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(this@MainActivity, 8), 1)
        })
        selectRow.addView(Ui.smallButton(this, getString(R.string.albums_clear), Ui.cardAlt(this), Ui.TEAL_DARK) {
            selected.clear(); persistSelection(); refresh()
        })
        root.addView(selectRow)

        grid = GridView(this).apply {
            numColumns = 2
            horizontalSpacing = dp(this@MainActivity, 12)
            verticalSpacing = dp(this@MainActivity, 12)
            val p = dp(this@MainActivity, 16)
            setPadding(p, dp(this@MainActivity, 8), p, dp(this@MainActivity, 8))
            clipToPadding = false
            setSelector(android.R.color.transparent)
        }
        adapter = AlbumAdapter()
        grid.adapter = adapter
        root.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        scanButton = Ui.pillButton(this, "✨ " + getString(R.string.albums_scan), Ui.TEAL) {
            if (selected.isNotEmpty()) startScan()
        }
        root.addView(scanButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(this@MainActivity, 16), dp(this@MainActivity, 6), dp(this@MainActivity, 16), dp(this@MainActivity, 16))
        })

        setContentView(root)
        refresh()
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
        executor.execute {
            val list = MediaQuery(this).queryAlbums()
            main.post {
                albums = list
                selected.retainAll(list.map { it.bucketId }.toSet())
                if (list.isEmpty()) infoText.text = getString(R.string.albums_empty)
                refresh()
            }
        }
    }

    private fun persistSelection() {
        prefs.selectedBuckets = selected
    }

    private fun refresh() {
        infoText.text = if (selected.isEmpty()) getString(R.string.albums_select_hint)
        else getString(R.string.albums_selected_count, selected.size)
        scanButton.alpha = if (selected.isEmpty()) 0.45f else 1f
        adapter.notifyDataSetChanged()
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
            progressTintList = ColorStateList.valueOf(Ui.TEAL)
            indeterminateTintList = ColorStateList.valueOf(Ui.TEAL)
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
                            ScanPhase.READING -> getString(R.string.scan_phase_reading)
                            ScanPhase.HASHING -> getString(R.string.scan_phase_hashing, done, total)
                            ScanPhase.GROUPING -> getString(R.string.scan_phase_grouping)
                            ScanPhase.SCORING -> getString(R.string.scan_phase_scoring, done, total)
                        }
                    }
                }
            } catch (t: Throwable) {
                failure = t
                emptyList()
            }
            main.post {
                dialog.dismiss()
                failure?.let { t ->
                    Ui.toast(this, getString(R.string.scan_failed, t.javaClass.simpleName))
                }
                if (!scanCancelled.get() && failure == null) {
                    ScanSession.groups = groups
                    startActivity(Intent(this, ResultsActivity::class.java))
                }
            }
        }
    }

    // ---------- Albüm kartları ----------

    private inner class AlbumAdapter : BaseAdapter() {
        override fun getCount() = albums.size
        override fun getItem(position: Int) = albums[position]
        override fun getItemId(position: Int) = albums[position].bucketId

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = this@MainActivity
            val album = albums[position]
            val isSelected = album.bucketId in selected

            val card = FrameLayout(ctx)
            Ui.cardify(
                card, Ui.card(ctx),
                radiusDp = 18,
                strokeColor = if (isSelected) Ui.TEAL else 0,
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

            // Alttan koyu gradyan bindirme + isim/bilgi
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
                text = getString(R.string.photos_count, album.photoCount) +
                    " · " + formatBytes(album.totalBytes)
                textSize = 11f
                setTextColor(0xE6FFFFFF.toInt())
            })
            card.addView(scrim)

            // Sağ üstte seçim rozeti
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
                background = if (isSelected) {
                    Ui.roundedRect(Ui.TEAL, 14f, ctx)
                } else {
                    Ui.roundedRect(0x66000000, 14f, ctx, Ui.WHITE, 2)
                }
            })

            card.isClickable = true
            card.foreground = android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x3314B8A6), null, null
            )
            card.setOnClickListener {
                if (album.bucketId in selected) selected.remove(album.bucketId)
                else selected.add(album.bucketId)
                persistSelection(); refresh()
            }
            return card
        }
    }
}
