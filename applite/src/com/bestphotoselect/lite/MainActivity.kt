package com.bestphotoselect.lite

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
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

    private lateinit var root: LinearLayout
    private lateinit var infoText: TextView
    private lateinit var grid: GridView
    private lateinit var scanButton: Button
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
        root = Ui.vbox(this)

        val header = Ui.hbox(this).apply { setPadding(dp(this@MainActivity, 8), dp(this@MainActivity, 8), dp(this@MainActivity, 8), 0) }
        header.addView(Ui.weight(Ui.title(this, getString(R.string.albums_title)), 1f))
        header.addView(Button(this).apply {
            text = getString(R.string.history)
            setOnClickListener { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) }
        })
        header.addView(Button(this).apply {
            text = getString(R.string.settings)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        root.addView(header)

        val selectRow = Ui.hbox(this).apply { setPadding(dp(this@MainActivity, 16), 0, dp(this@MainActivity, 8), 0) }
        infoText = TextView(this).apply { text = getString(R.string.albums_select_hint) }
        selectRow.addView(Ui.weight(infoText, 1f))
        selectRow.addView(Button(this).apply {
            text = getString(R.string.albums_select_all)
            setOnClickListener {
                selected = albums.map { it.bucketId }.toMutableSet()
                persistSelection(); refresh()
            }
        })
        selectRow.addView(Button(this).apply {
            text = getString(R.string.albums_clear)
            setOnClickListener { selected.clear(); persistSelection(); refresh() }
        })
        root.addView(selectRow)

        grid = GridView(this).apply {
            numColumns = 2
            horizontalSpacing = dp(this@MainActivity, 8)
            verticalSpacing = dp(this@MainActivity, 8)
            setPadding(dp(this@MainActivity, 12), dp(this@MainActivity, 8), dp(this@MainActivity, 12), dp(this@MainActivity, 8))
            clipToPadding = false
        }
        adapter = AlbumAdapter()
        grid.adapter = adapter
        root.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        scanButton = Button(this).apply {
            text = getString(R.string.albums_scan)
            isEnabled = false
            setOnClickListener { startScan() }
        }
        root.addView(scanButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(this@MainActivity, 16), 0, dp(this@MainActivity, 16), dp(this@MainActivity, 16)) })

        setContentView(root)
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
        scanButton.isEnabled = selected.isNotEmpty()
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
        }
        val msg = TextView(this).apply {
            text = getString(R.string.scan_phase_reading)
            setPadding(0, dp(this@MainActivity, 12), 0, 0)
        }
        box.addView(bar)
        box.addView(msg)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.scan_title)
            .setView(box)
            .setNegativeButton(R.string.scan_cancel) { _, _ -> scanCancelled.set(true) }
            .setCancelable(false)
            .create()
        dialog.show()

        val buckets = selected.toSet()
        val settings = prefs.toAppSettings()
        executor.execute {
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
                emptyList()
            }
            main.post {
                dialog.dismiss()
                if (!scanCancelled.get()) {
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

            val holder: AlbumHolder
            val view: View
            if (convertView?.tag is AlbumHolder) {
                view = convertView
                holder = convertView.tag as AlbumHolder
            } else {
                val card = Ui.vbox(ctx)
                card.setBackgroundColor(0x14808080)
                val frame = FrameLayout(ctx)
                val image = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 120)
                    )
                }
                val check = CheckBox(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.END
                    )
                    isClickable = false
                }
                frame.addView(image)
                frame.addView(check)
                card.addView(frame)
                val name = TextView(ctx).apply {
                    setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), 0)
                    maxLines = 1
                }
                val meta = TextView(ctx).apply {
                    setPadding(dp(ctx, 8), 0, dp(ctx, 8), dp(ctx, 6))
                    textSize = 12f
                }
                card.addView(name)
                card.addView(meta)
                holder = AlbumHolder(image, check, name, meta)
                card.tag = holder
                view = card
            }

            holder.name.text = album.name
            holder.meta.text = getString(R.string.photos_count, album.photoCount) +
                " · " + formatBytes(album.totalBytes)
            holder.check.isChecked = album.bucketId in selected
            Thumbs.load(ctx, holder.image, album.coverUri, 320)
            view.setOnClickListener {
                if (album.bucketId in selected) selected.remove(album.bucketId)
                else selected.add(album.bucketId)
                persistSelection(); refresh()
            }
            return view
        }
    }

    private class AlbumHolder(
        val image: ImageView,
        val check: CheckBox,
        val name: TextView,
        val meta: TextView
    )
}
