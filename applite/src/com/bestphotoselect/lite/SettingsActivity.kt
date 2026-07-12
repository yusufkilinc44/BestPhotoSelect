package com.bestphotoselect.lite

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.bestphotoselect.R
import com.bestphotoselect.lite.Ui.dp

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var autopilotSection: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = Ui.screenRoot(this, statusBarColor = Ui.Screens.SETTINGS.dark)
        root.addView(Ui.gradientHeader(this, "⚙️ " + getString(R.string.settings_title), accent = Ui.Screens.SETTINGS))

        val content = Ui.vbox(this).apply {
            val p = dp(this@SettingsActivity, 16)
            setPadding(p, dp(this@SettingsActivity, 8), p, p)
        }

        // --- Tarama ayarları kartı ---
        val scanCard = card()
        scanCard.addView(Ui.sectionTitle(this, "🎯 " + getString(R.string.settings_similarity)))
        scanCard.addView(Ui.body(this, getString(R.string.settings_similarity_desc), dim = true))
        val hammingValue = Ui.body(this, labelForHamming(prefs.hammingThreshold))
        scanCard.addView(tintedSeekBar(12, prefs.hammingThreshold - 4) { v ->
            prefs.hammingThreshold = v + 4
            hammingValue.text = labelForHamming(v + 4)
        })
        scanCard.addView(hammingValue)

        scanCard.addView(Ui.sectionTitle(this, "⏱ " + getString(R.string.settings_time_window)))
        scanCard.addView(Ui.body(this, getString(R.string.settings_time_window_desc), dim = true))
        val timeValue = Ui.body(this, getString(R.string.settings_time_window_value, prefs.timeWindowSec))
        scanCard.addView(tintedSeekBar(590, prefs.timeWindowSec - 10) { v ->
            prefs.timeWindowSec = v + 10
            timeValue.text = getString(R.string.settings_time_window_value, v + 10)
        })
        scanCard.addView(timeValue)
        content.addView(scanCard)

        // --- Silme ayarları kartı ---
        val deleteCard = card()
        deleteCard.addView(switchRow(
            "🛟 " + getString(R.string.settings_trash_mode),
            getString(R.string.settings_trash_mode_desc),
            prefs.trashMode
        ) { checked -> prefs.trashMode = checked })
        content.addView(deleteCard)

        // --- Otomatik pilot kartı ---
        val autoCard = card()
        autoCard.addView(switchRow(
            "🤖 " + getString(R.string.settings_autopilot),
            getString(R.string.settings_autopilot_desc),
            prefs.autopilotEnabled
        ) { checked ->
            prefs.autopilotEnabled = checked
            if (checked) {
                AutoPilotJobService.schedule(this, prefs.autopilotDaily)
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 300)
                }
            } else {
                AutoPilotJobService.cancel(this)
            }
            renderAutopilotSection()
        })
        autopilotSection = Ui.vbox(this)
        autoCard.addView(autopilotSection)
        content.addView(autoCard)
        renderAutopilotSection()

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        renderAutopilotSection()
    }

    private fun card(): LinearLayout = Ui.vbox(this).also { c ->
        Ui.cardify(c, Ui.card(this), radiusDp = 18, elevationDp = 2)
        val p = dp(this, 14)
        c.setPadding(p, dp(this, 4), p, p)
        c.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(this@SettingsActivity, 10), 0, 0) }
    }

    private fun tintedSeekBar(max: Int, progress: Int, onValue: (Int) -> Unit): SeekBar =
        SeekBar(this).apply {
            this.max = max
            this.progress = progress
            progressTintList = ColorStateList.valueOf(Ui.Screens.SETTINGS.main)
            thumbTintList = ColorStateList.valueOf(Ui.Screens.SETTINGS.main)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onValue(p)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

    private fun labelForHamming(v: Int): String =
        "${getString(R.string.settings_similarity_high)} 4 ← $v → 16 ${getString(R.string.settings_similarity_low)}"

    private fun renderAutopilotSection() {
        autopilotSection.removeAllViews()
        if (!prefs.autopilotEnabled) return

        autopilotSection.addView(Ui.body(this, "⚠️ " + getString(R.string.settings_autopilot_warning)).apply {
            setTextColor(Ui.CORAL_DARK)
            setPadding(0, dp(this@SettingsActivity, 8), 0, 0)
        })

        autopilotSection.addView(Ui.sectionTitle(this, getString(R.string.settings_autopilot_schedule)))
        val radios = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        radios.addView(RadioButton(this).apply {
            text = getString(R.string.settings_schedule_daily)
            isChecked = prefs.autopilotDaily
            buttonTintList = ColorStateList.valueOf(Ui.Screens.SETTINGS.main)
            setTextColor(Ui.text(this@SettingsActivity))
            setOnClickListener {
                prefs.autopilotDaily = true
                AutoPilotJobService.schedule(this@SettingsActivity, true)
            }
        })
        radios.addView(RadioButton(this).apply {
            text = getString(R.string.settings_schedule_weekly)
            isChecked = !prefs.autopilotDaily
            buttonTintList = ColorStateList.valueOf(Ui.Screens.SETTINGS.main)
            setTextColor(Ui.text(this@SettingsActivity))
            setOnClickListener {
                prefs.autopilotDaily = false
                AutoPilotJobService.schedule(this@SettingsActivity, false)
            }
        })
        autopilotSection.addView(radios)

        val canManage = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(this)
        if (!canManage) {
            autopilotSection.addView(Ui.sectionTitle(this, "🔑 " + getString(R.string.settings_manage_media_title)))
            autopilotSection.addView(Ui.body(this, getString(R.string.settings_manage_media_desc), dim = true))
            autopilotSection.addView(
                Ui.smallButton(this, getString(R.string.settings_manage_media_open), Ui.Screens.SETTINGS.main) {
                    try {
                        startActivity(
                            Intent(
                                "android.settings.REQUEST_MANAGE_MEDIA",
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (e: Exception) {
                        // Ayar sayfası yoksa (Android 11) sessizce yoksay
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(this@SettingsActivity, 8) }
                }
            )
        }
    }

    private fun switchRow(
        title: String,
        desc: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = Ui.hbox(this).apply { setPadding(0, dp(this@SettingsActivity, 14), 0, 0) }
        val texts = Ui.vbox(this)
        texts.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(this@SettingsActivity))
        })
        texts.addView(Ui.body(this, desc, dim = true))
        row.addView(Ui.weight(texts, 1f))
        @Suppress("UseSwitchCompatOrMaterialCode")
        val sw = Switch(this).apply {
            isChecked = initial
            thumbTintList = ColorStateList.valueOf(Ui.Screens.SETTINGS.main)
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(sw)
        return row
    }
}
