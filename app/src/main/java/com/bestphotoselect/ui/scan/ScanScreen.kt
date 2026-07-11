package com.bestphotoselect.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.bestphotoselect.R
import com.bestphotoselect.data.ScanRepository
import com.bestphotoselect.data.model.ScanPhase
import com.bestphotoselect.data.model.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository
) : ViewModel() {
    val state = scanRepository.state
    fun cancel() = scanRepository.cancelScan()
}

@Composable
fun ScanScreen(
    onDone: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is ScanState.Done) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val s = state) {
            is ScanState.Running -> {
                Text(
                    text = stringResource(R.string.scan_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val label = when (s.phase) {
                        ScanPhase.READING -> stringResource(R.string.scan_phase_reading)
                        ScanPhase.HASHING -> stringResource(R.string.scan_phase_hashing, s.done, s.total)
                        ScanPhase.GROUPING -> stringResource(R.string.scan_phase_grouping)
                        ScanPhase.SCORING -> stringResource(R.string.scan_phase_scoring, s.done, s.total)
                    }
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { s.done.toFloat() / s.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                OutlinedButton(onClick = {
                    viewModel.cancel()
                    onCancelled()
                }) {
                    Text(stringResource(R.string.scan_cancel))
                }
            }
            is ScanState.Failed -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = onCancelled,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.back))
                }
            }
            else -> {
                // Idle: tarama başlatılmadan gelinmişse geri dön; Done: LaunchedEffect yönlendirir.
                CircularProgressIndicator()
            }
        }
    }
}
