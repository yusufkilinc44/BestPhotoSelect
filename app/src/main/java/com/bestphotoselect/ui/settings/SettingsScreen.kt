package com.bestphotoselect.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestphotoselect.R
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.AppSettings
import com.bestphotoselect.data.model.AutopilotSchedule
import com.bestphotoselect.work.AutoPilotScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val autoPilotScheduler: AutoPilotScheduler
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setHamming(value: Int) = viewModelScope.launch {
        settingsRepository.setHammingThreshold(value)
    }

    fun setTimeWindow(value: Int) = viewModelScope.launch {
        settingsRepository.setTimeWindowSec(value)
    }

    fun setTrashMode(value: Boolean) = viewModelScope.launch {
        settingsRepository.setTrashMode(value)
    }

    fun setAutopilot(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutopilotEnabled(enabled)
        if (enabled) {
            autoPilotScheduler.schedule(
                settings.value?.autopilotSchedule ?: AutopilotSchedule.DAILY
            )
        } else {
            autoPilotScheduler.cancel()
        }
    }

    fun setSchedule(schedule: AutopilotSchedule) = viewModelScope.launch {
        settingsRepository.setAutopilotSchedule(schedule)
        if (settings.value?.autopilotEnabled == true) {
            autoPilotScheduler.schedule(schedule)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sonuç bilgilendirme amaçlı; reddedilirse bildirimler sessizce gösterilmez */ }

    Scaffold(
        topBar = {
            TopAppBar(
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

            // Otomatik pilot
            SwitchRow(
                title = stringResource(R.string.settings_autopilot),
                description = stringResource(R.string.settings_autopilot_desc),
                checked = s.autopilotEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setAutopilot(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            if (s.autopilotEnabled) {
                Text(
                    text = stringResource(R.string.settings_autopilot_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Tarama sıklığı
                Text(
                    text = stringResource(R.string.settings_autopilot_schedule),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    FilterChip(
                        selected = s.autopilotSchedule == AutopilotSchedule.DAILY,
                        onClick = { viewModel.setSchedule(AutopilotSchedule.DAILY) },
                        label = { Text(stringResource(R.string.settings_schedule_daily)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = s.autopilotSchedule == AutopilotSchedule.WEEKLY,
                        onClick = { viewModel.setSchedule(AutopilotSchedule.WEEKLY) },
                        label = { Text(stringResource(R.string.settings_schedule_weekly)) }
                    )
                }

                // MANAGE_MEDIA izni (Android 12+)
                val canManage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    MediaStore.canManageMedia(context)
                if (!canManage) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_manage_media_title),
                                style = MaterialTheme.typography.titleSmall
                            )
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
                }
            }
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
