package com.bestphotoselect.lite

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
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

        val root = Ui.vbox(this)
        root.addView(Ui.title(this, getString(R.string.settings_title)))

        val content = Ui.vbox(this).apply {
            val p = dp(this@SettingsActivity, 16)
            setPadding(p, 0, p, p)
        }

        // Benzerlik hassasiyeti (düşük Hamming eşiği = yüksek hassasiyet)
        content.addView(sectionTitle(getString(R.string.settings_similarity)))
        content.addView(sectionDesc(getString(R.string.settings_similarity_desc)))
        val hammingValue = TextView(this)
        val hammingBar = SeekBar(this).apply {
            max = 12 // 4..16
            progress = prefs.hammingThreshold - 4
            setOnSeekBarChangeListener(onSeek { v ->
                prefs.hammingThreshold = v + 4
                hammingValue.text = labelForHamming(v + 4)
            })
        }
        hammingValue.text = labelForHamming(prefs.hammingThreshold)
        content.addView(hammingBar)
        content.addView(hammingValue)

        // Zaman penceresi
        content.addView(sectionTitle(getString(R.string.settings_time_window)))
        content.addView(sectionDesc(getString(R.string.settings_time_window_desc)))
        val timeValue = TextView(this)
        val timeBar = SeekBar(this).apply {
            max = 590 // 10..600 sn
            progress = prefs.timeWindowSec - 10
            setOnSeekBarChangeListener(onSeek { v ->
                prefs.timeWindowSec = v + 10
                timeValue.text = getString(R.string.settings_time_window_value, v + 10)
            })
        }
        timeValue.text = getString(R.string.settings_time_window_value, prefs.timeWindowSec)
        content.addView(timeBar)
        content.addView(timeValue)

        // Çöp kutusu modu
        content.addView(switchRow(
            getString(R.string.settings_trash_mode),
            getString(R.string.settings_trash_mode_desc),
            prefs.trashMode
        ) { checked -> prefs.trashMode = checked })

        // Otomatik pilot
        content.addView(switchRow(
            getString(R.string.settings_autopilot),
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
        content.addView(autopilotSection)
        renderAutopilotSection()

        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        renderAutopilotSection()
    }

    private fun labelForHamming(v: Int): String =
        "${getString(R.string.settings_similarity_high)} 4 ← $v → 16 ${getString(R.string.settings_similarity_low)}"

    private fun renderAutopilotSection() {
        autopilotSection.removeAllViews()
        if (!prefs.autopilotEnabled) return

        autopilotSection.addView(sectionDesc(getString(R.string.settings_autopilot_warning)).apply {
            setTextColor(0xFFC82828.toInt())
        })

        autopilotSection.addView(sectionTitle(getString(R.string.settings_autopilot_schedule)))
        val radios = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val daily = RadioButton(this).apply {
            text = getString(R.string.settings_schedule_daily)
            isChecked = prefs.autopilotDaily
            setOnClickListener {
                prefs.autopilotDaily = true
                AutoPilotJobService.schedule(this@SettingsActivity, true)
            }
        }
        val weekly = RadioButton(this).apply {
            text = getString(R.string.settings_schedule_weekly)
            isChecked = !prefs.autopilotDaily
            setOnClickListener {
                prefs.autopilotDaily = false
                AutoPilotJobService.schedule(this@SettingsActivity, false)
            }
        }
        radios.addView(daily)
        radios.addView(weekly)
        autopilotSection.addView(radios)

        val canManage = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(this)
        if (!canManage) {
            autopilotSection.addView(sectionTitle(getString(R.string.settings_manage_media_title)))
            autopilotSection.addView(sectionDesc(getString(R.string.settings_manage_media_desc)))
            autopilotSection.addView(Button(this).apply {
                text = getString(R.string.settings_manage_media_open)
                setOnClickListener {
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
                }
            })
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(this@SettingsActivity, 20), 0, dp(this@SettingsActivity, 2))
    }

    private fun sectionDesc(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, 0, 0, dp(this@SettingsActivity, 6))
    }

    private fun switchRow(
        title: String,
        desc: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = Ui.hbox(this).apply { setPadding(0, dp(this@SettingsActivity, 20), 0, 0) }
        val texts = Ui.vbox(this)
        texts.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 13f
        })
        row.addView(Ui.weight(texts, 1f))
        @Suppress("UseSwitchCompatOrMaterialCode")
        val sw = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(sw)
        return row
    }

    private fun onSeek(onValue: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onValue(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
