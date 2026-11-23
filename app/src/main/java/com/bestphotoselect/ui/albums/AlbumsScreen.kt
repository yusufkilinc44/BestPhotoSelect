package com.bestphotoselect.ui.albums

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.bestphotoselect.R
import com.bestphotoselect.data.model.Album
import com.bestphotoselect.data.model.ThemeMode
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
    val asList by viewModel.albumsAsList.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.albums_title)) },
                actions = {
                    ThemeMenu(
                        mode = viewModel.themeMode.collectAsState().value,
                        onSelect = { viewModel.setThemeMode(it) }
                    )
                    IconButton(onClick = { viewModel.setAlbumsAsList(!asList) }) {
                        if (asList) {
                            Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.albums_view_grid))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(R.string.albums_view_list))
                        }
                    }
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
    ManageMediaPrompt(viewModel)

    val albums by viewModel.albums.collectAsState()
    val selected by viewModel.selectedBuckets.collectAsState()
    val asList by viewModel.albumsAsList.collectAsState()
    val includeWhatsApp by viewModel.includeWhatsApp.collectAsState()

    val visibleAlbums = albums?.let { all -> if (includeWhatsApp) all else all.filter { !it.isWhatsApp } }

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

                if (albums!!.any { it.isWhatsApp }) {
                    WhatsAppToggle(
                        checked = includeWhatsApp,
                        onCheckedChange = { viewModel.setIncludeWhatsApp(it) }
                    )
                }

                if (asList) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listItems(visibleAlbums!!, key = { it.bucketId }) { album ->
                            AlbumRow(
                                album = album,
                                selected = album.bucketId in selected,
                                onClick = { viewModel.toggleAlbum(album.bucketId) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleAlbums!!, key = { it.bucketId }) { album ->
                            AlbumCard(
                                album = album,
                                selected = album.bucketId in selected,
                                onClick = { viewModel.toggleAlbum(album.bucketId) }
                            )
                        }
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
                if (album.isScreenshots) {
                    Text(
                        text = stringResource(R.string.albums_screenshot_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (album.isWhatsApp) {
                    Text(
                        text = stringResource(R.string.albums_whatsapp_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Üst çubukta tema seçimi: sistem varsayılanı, açık ya da koyu. */
@Composable
private fun ThemeMenu(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = when (mode) {
                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                },
                contentDescription = stringResource(R.string.theme)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                ThemeMode.SYSTEM to R.string.theme_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark
            ).forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { RadioButton(selected = option == mode, onClick = null) },
                    onClick = {
                        onSelect(option)
                        open = false
                    }
                )
            }
        }
    }
}

/** WhatsApp klasörlerini listede gösteren/gizleyen anahtar; gizliyken seçilemez ve taranmaz. */
@Composable
private fun WhatsAppToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.albums_whatsapp_toggle),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!checked) {
                Text(
                    text = stringResource(R.string.albums_whatsapp_toggle_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Liste görünümünde albüm satırı: küçük kapak, ad, sayı/boyut, seçim işareti. */
@Composable
private fun AlbumRow(album: Album, selected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = album.coverUri,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .alpha(if (selected) 0.75f else 1f)
            )
            Column(Modifier.weight(1f)) {
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
                if (album.isScreenshots) {
                    Text(
                        text = stringResource(R.string.albums_screenshot_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (album.isWhatsApp) {
                    Text(
                        text = stringResource(R.string.albums_whatsapp_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Android, MANAGE_MEDIA ("Medya yönetimi") özel izni yoksa her silmede kendi onay
 * penceresini gösterir. Bu izin çalışma zamanı penceresiyle verilemez; yalnızca
 * sistem ayarlarındaki ekrandan açılır. Kurulumdan sonra bir kez açıklanıp o ekran
 * açılır; kullanıcı ne seçerse seçsin bir daha sorulmaz (Ayarlar'dan yine verilebilir).
 */
@Composable
private fun ManageMediaPrompt(viewModel: AlbumsViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalContext.current
    val prompted by viewModel.manageMediaPrompted.collectAsState()
    if (prompted != false || MediaStore.canManageMedia(context)) return

    AlertDialog(
        onDismissRequest = { viewModel.markManageMediaPrompted() },
        title = { Text(stringResource(R.string.manage_media_prompt_title)) },
        text = { Text(stringResource(R.string.manage_media_prompt_message)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.markManageMediaPrompted()
                context.startActivity(
                    Intent(AndroidSettings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}"))
                )
            }) {
                Text(stringResource(R.string.manage_media_prompt_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.markManageMediaPrompted() }) {
                Text(stringResource(R.string.manage_media_prompt_later))
            }
        }
    )
}
