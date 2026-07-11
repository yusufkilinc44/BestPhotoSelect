package com.bestphotoselect.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bestphotoselect.R
import com.bestphotoselect.data.model.Album
import com.bestphotoselect.ui.common.PermissionGate
import com.bestphotoselect.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onScanStarted: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.albums_title)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            PermissionGate {
                AlbumsContent(
                    viewModel = viewModel,
                    onScanStarted = onScanStarted
                )
            }
        }
    }
}

@Composable
private fun AlbumsContent(
    viewModel: AlbumsViewModel,
    onScanStarted: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadAlbums() }

    val albums by viewModel.albums.collectAsState()
    val selected by viewModel.selectedBuckets.collectAsState()

    when {
        albums == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        albums!!.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.albums_empty))
            }
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selected.isEmpty()) {
                            stringResource(R.string.albums_select_hint)
                        } else {
                            stringResource(R.string.albums_selected_count, selected.size)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Text(stringResource(R.string.albums_select_all))
                    }
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text(stringResource(R.string.albums_clear))
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums!!, key = { it.bucketId }) { album ->
                        AlbumCard(
                            album = album,
                            selected = album.bucketId in selected,
                            onClick = { viewModel.toggleAlbum(album.bucketId) }
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.startScan()
                        onScanStarted()
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.albums_scan))
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, selected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Box {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .alpha(if (selected) 0.75f else 1f)
                )
                Icon(
                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.photos_count, album.photoCount) +
                        " · " + formatBytes(album.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
