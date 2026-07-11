package com.bestphotoselect.ui.results

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bestphotoselect.R
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScanState
import com.bestphotoselect.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onOpenGroup: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val trashMode by viewModel.trashMode.collectAsState()
    val confirmVisible by viewModel.confirmDialogVisible.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onSystemDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ResultsViewModel.Event.LaunchSystemDelete ->
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(event.intent.intentSender).build()
                    )
                is ResultsViewModel.Event.Deleted ->
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.delete_success, event.count)
                    )
                is ResultsViewModel.Event.DeleteFailed ->
                    snackbarHostState.showSnackbar(context.getString(R.string.delete_failed))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val s = state) {
            is ScanState.Done -> {
                if (s.groups.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.results_empty))
                    }
                } else {
                    ResultsContent(
                        groups = s.groups,
                        padding = padding,
                        onOpenGroup = onOpenGroup,
                        onDeleteClick = { viewModel.requestDeleteConfirmation() }
                    )
                }
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.results_empty))
                }
            }
        }
    }

    if (confirmVisible) {
        val candidates = viewModel.deletionCandidates()
        val bytes = candidates.sumOf { it.sizeBytes }
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmDialog() },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.confirm_delete_message,
                            candidates.size,
                            formatBytes(bytes)
                        )
                    )
                    Text(
                        text = if (trashMode) {
                            stringResource(R.string.confirm_delete_trash_note)
                        } else {
                            stringResource(R.string.confirm_delete_permanent_note)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (trashMode) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ResultsContent(
    groups: List<PhotoGroup>,
    padding: PaddingValues,
    onOpenGroup: (Int) -> Unit,
    onDeleteClick: () -> Unit
) {
    val totalCandidates = groups.sumOf { it.deletionCandidates.size }
    val totalBytes = groups.sumOf { it.bytesToFree }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Text(
            text = stringResource(
                R.string.results_summary,
                groups.size,
                totalCandidates,
                formatBytes(totalBytes)
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                GroupCard(group = group, onClick = { onOpenGroup(group.id) })
            }
        }

        Button(
            onClick = onDeleteClick,
            enabled = totalCandidates > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.results_delete_selected, totalCandidates))
        }
    }
}

@Composable
private fun GroupCard(group: PhotoGroup, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.results_group_title, group.photos.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatBytes(group.bytesToFree),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(group.photos, key = { it.photo.id }) { scored ->
                    Box {
                        AsyncImage(
                            model = scored.photo.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        if (scored.photo.id == group.bestPhotoId) {
                            Text(
                                text = stringResource(R.string.results_best_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                    .padding(vertical = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else if (scored.markedForDeletion) {
                            Box(
                                Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            }
        }
    }
}
