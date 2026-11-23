package com.bestphotoselect.ui.settings

import com.bestphotoselect.util.rememberAppVersion
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import com.bestphotoselect.ui.theme.accentFor
import androidx.compose.material3.CardDefaults
import com.bestphotoselect.ui.theme.appTopBarColors
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestphotoselect.R
import com.bestphotoselect.data.IgnoredGroupsRepository
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.AppSettings
import com.bestphotoselect.data.model.AutoScanMode
import com.bestphotoselect.data.model.AutopilotSchedule
import com.bestphotoselect.data.model.ScoringWeights
import com.bestphotoselect.work.AutoPilotScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val autoPilotScheduler: AutoPilotScheduler,
    private val ignoredGroupsRepository: IgnoredGroupsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ignoredGroupsCount: StateFlow<Int> = ignoredGroupsRepository.ignoredCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Tüm ayarları varsayılana döndürür; otomatik tarama da kapanır. */
    fun resetAllSettings() = viewModelScope.launch {
        settingsRepository.resetToDefaults()
        autoPilotScheduler.cancel()
    }

    fun resetIgnoredGroups() = viewModelScope.launch {
        ignoredGroupsRepository.clearAll()
    }

    fun setScoringWeights(weights: ScoringWeights) = viewModelScope.launch {
        settingsRepository.setScoringWeights(weights)
    }

    fun setHamming(value: Int) = viewModelScope.launch {
        settingsRepository.setHammingThreshold(value)
    }

    fun setTimeWindow(value: Int) = viewModelScope.launch {
        settingsRepository.setTimeWindowSec(value)
    }

    fun setTrashMode(value: Boolean) = viewModelScope.launch {
        settingsRepository.setTrashMode(value)
    }

    fun setAutoScanMode(mode: AutoScanMode) = viewModelScope.launch {
        settingsRepository.setAutoScanMode(mode)
        if (mode == AutoScanMode.OFF) {
            autoPilotScheduler.cancel()
        } else {
            autoPilotScheduler.schedule(
                settings.value?.autopilotSchedule ?: AutopilotSchedule.DAILY
            )
        }
    }

    fun setSchedule(schedule: AutopilotSchedule) = viewModelScope.launch {
        settingsRepository.setAutopilotSchedule(schedule)
        if (settings.value?.autoScanMode != AutoScanMode.OFF) {
            autoPilotScheduler.schedule(schedule)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val ignoredCount by viewModel.ignoredGroupsCount.collectAsState()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sonuç bilgilendirme amaçlı; reddedilirse bildirimler sessizce gösterilmez */ }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appTopBarColors(),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Benzerlik hassasiyeti: düşük Hamming eşiği = yüksek hassasiyet
            SettingHeader(
                title = stringResource(R.string.settings_similarity),
                description = stringResource(R.string.settings_similarity_desc)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_similarity_high),
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = s.hammingThreshold.toFloat(),
                    onValueChange = { viewModel.setHamming(it.toInt()) },
                    valueRange = 4f..16f,
                    steps = 11,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    stringResource(R.string.settings_similarity_low),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Zaman penceresi
            SettingHeader(
                title = stringResource(R.string.settings_time_window),
                description = stringResource(R.string.settings_time_window_desc)
            )
            Column {
                Slider(
                    value = s.timeWindowSec.toFloat(),
                    onValueChange = { viewModel.setTimeWindow(it.toInt()) },
                    valueRange = 10f..600f
                )
                Text(
                    text = stringResource(R.string.settings_time_window_value, s.timeWindowSec),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Çöp kutusu modu
            SwitchRow(
                title = stringResource(R.string.settings_trash_mode),
                description = stringResource(R.string.settings_trash_mode_desc),
                checked = s.trashMode,
                onCheckedChange = { viewModel.setTrashMode(it) }
            )

            // Otomatik tarama: kapalı / tara ve bildir / tara ve otomatik sil
            SettingHeader(
                title = stringResource(R.string.settings_autopilot),
                description = stringResource(R.string.settings_autopilot_desc)
            )
            listOf(
                Triple(AutoScanMode.OFF, R.string.settings_autoscan_off, null),
                Triple(AutoScanMode.NOTIFY_ONLY, R.string.settings_autoscan_notify, R.string.settings_autoscan_notify_desc),
                Triple(AutoScanMode.AUTO_DELETE, R.string.settings_autoscan_delete, R.string.settings_autoscan_delete_desc)
            ).forEach { (mode, titleRes, descRes) ->
                RadioRow(
                    title = stringResource(titleRes),
                    description = descRes?.let { stringResource(it) },
                    selected = s.autoScanMode == mode,
                    onClick = {
                        if (s.autoScanMode == AutoScanMode.OFF && mode != AutoScanMode.OFF &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.setAutoScanMode(mode)
                    }
                )
            }

            if (s.autoScanMode == AutoScanMode.AUTO_DELETE) {
                Text(
                    text = stringResource(R.string.settings_autopilot_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (s.autoScanMode != AutoScanMode.OFF) {
                // Tarama sıklığı
                Text(
                    text = stringResource(R.string.settings_autopilot_schedule),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    listOf(
                        AutopilotSchedule.DAILY to R.string.settings_schedule_daily,
                        AutopilotSchedule.EVERY_3_DAYS to R.string.settings_schedule_3days,
                        AutopilotSchedule.WEEKLY to R.string.settings_schedule_weekly
                    ).forEach { (schedule, labelRes) ->
                        FilterChip(
                            selected = s.autopilotSchedule == schedule,
                            onClick = { viewModel.setSchedule(schedule) },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            // MANAGE_MEDIA izni (Android 12+): hem elle hem otomatik pilotta
            // sistem onay diyaloğunu kaldırır.
            val canManage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                MediaStore.canManageMedia(context)
            SectionCard(accentKey = 4) {
                Text(
                    text = stringResource(R.string.settings_manage_media_title),
                    style = MaterialTheme.typography.titleSmall
                )
                if (canManage) {
                    Text(
                        text = stringResource(R.string.settings_manage_media_granted),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_manage_media_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(
                                    Settings_ACTION_REQUEST_MANAGE_MEDIA,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }) {
                        Text(stringResource(R.string.settings_manage_media_open))
                    }
                }
            }

            WeightsSection(
                weights = s.scoringWeights,
                onChange = viewModel::setScoringWeights
            )

            IgnoredGroupsSection(
                count = ignoredCount,
                onReset = viewModel::resetIgnoredGroups
            )

            SectionCard(accentKey = 5) {
                Text(
                    text = stringResource(R.string.settings_how_it_works_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onOpenAbout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.settings_how_it_works))
                }
            }

            ResetAllSection(onConfirm = viewModel::resetAllSettings)

            DeveloperFooter()
        }
    }
}

/** Sayfanın en altında geliştirici bilgisi. */
@Composable
private fun DeveloperFooter() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.app_version, rememberAppVersion()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.settings_developer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.settings_developer_info),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        // Dokununca e-posta uygulaması açılır; yoksa sessizce yok sayılır.
        val context = LocalContext.current
        val email = stringResource(R.string.settings_developer_email)
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                    }
                }
                .padding(4.dp)
        )
    }
}

/** En altta: tüm ayarları varsayılana döndürme (onaylı). */
@Composable
private fun ResetAllSection(onConfirm: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp)
    ) {
        Text(stringResource(R.string.settings_reset_all))
    }
    if (done) {
        Text(
            text = stringResource(R.string.settings_reset_all_done),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_reset_all_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_all_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    done = true
                    onConfirm()
                }) {
                    Text(stringResource(R.string.settings_reset_all_confirm_positive))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SectionCard(accentKey: Long, content: @Composable ColumnScope.() -> Unit) {
    val accent = accentFor(accentKey)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = accent.container, contentColor = accent.content)
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun WeightsSection(
    weights: ScoringWeights,
    onChange: (ScoringWeights) -> Unit
) {
    SectionCard(accentKey = 3) {
        Text(stringResource(R.string.settings_weights_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.settings_weights_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.settings_weights_top_section),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        val topSum = weights.faceQuality + weights.sharpness + weights.exposure + weights.resolution +
            weights.togetherness + weights.composition
        WeightSlider(R.string.settings_weights_face_quality, weights.faceQuality, topSum) {
            onChange(weights.copy(faceQuality = it))
        }
        WeightSlider(R.string.settings_weights_sharpness, weights.sharpness, topSum) {
            onChange(weights.copy(sharpness = it))
        }
        WeightSlider(R.string.settings_weights_exposure, weights.exposure, topSum) {
            onChange(weights.copy(exposure = it))
        }
        WeightSlider(R.string.settings_weights_resolution, weights.resolution, topSum) {
            onChange(weights.copy(resolution = it))
        }
        WeightSlider(R.string.settings_weights_togetherness, weights.togetherness, topSum) {
            onChange(weights.copy(togetherness = it))
        }
        WeightSlider(R.string.settings_weights_composition, weights.composition, topSum) {
            onChange(weights.copy(composition = it))
        }

        Text(
            text = stringResource(R.string.settings_weights_face_section),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        val faceSum = weights.eyesOpen + weights.frontal + weights.smile + weights.mouthClosed
        WeightSlider(R.string.settings_weights_eyes_open, weights.eyesOpen, faceSum) {
            onChange(weights.copy(eyesOpen = it))
        }
        WeightSlider(R.string.settings_weights_mouth_closed, weights.mouthClosed, faceSum) {
            onChange(weights.copy(mouthClosed = it))
        }
        WeightSlider(R.string.settings_weights_smile, weights.smile, faceSum) {
            onChange(weights.copy(smile = it))
        }
        WeightSlider(R.string.settings_weights_frontal, weights.frontal, faceSum) {
            onChange(weights.copy(frontal = it))
        }

        OutlinedButton(
            onClick = { onChange(ScoringWeights()) },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.settings_weights_reset))
        }
    }
}

@Composable
private fun WeightSlider(labelRes: Int, value: Int, groupSum: Int, onValueChange: (Int) -> Unit) {
    val percent = if (groupSum > 0) value * 100 / groupSum else 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(0.35f)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "%$percent",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun IgnoredGroupsSection(count: Int, onReset: () -> Unit) {
    var resetDone by remember { mutableStateOf(false) }
    SectionCard(accentKey = 1) {
        Text(stringResource(R.string.settings_ignored_groups_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.settings_ignored_groups_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_ignored_groups_count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    onReset()
                    resetDone = true
                },
                enabled = count > 0
            ) {
                Text(stringResource(R.string.settings_ignored_groups_reset))
            }
        }
        if (resetDone && count == 0) {
            Text(
                text = stringResource(R.string.settings_ignored_groups_reset_done),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// android.provider.Settings.ACTION_REQUEST_MANAGE_MEDIA (API 31+) sabiti;
// derleme hedefi eski platformlarda da çözülebilsin diye düz metin kullanılıyor.
private const val Settings_ACTION_REQUEST_MANAGE_MEDIA =
    "android.settings.REQUEST_MANAGE_MEDIA"

@Composable
private fun SettingHeader(title: String, description: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp)
    )
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun RadioRow(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
