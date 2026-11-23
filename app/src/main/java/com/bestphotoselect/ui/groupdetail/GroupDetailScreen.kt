package com.bestphotoselect.ui.groupdetail

import com.bestphotoselect.ui.theme.appTopBarColors
import android.app.Activity
import android.app.PendingIntent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.bestphotoselect.data.DeletionRepository
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.PhotoItem
import com.bestphotoselect.util.formatBytes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.bestphotoselect.R
import com.bestphotoselect.data.ScanRepository
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScanState
import com.bestphotoselect.data.model.ScoredPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val deletionRepository: DeletionRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val groupId: Int = checkNotNull(savedStateHandle["groupId"])

    sealed interface Event {
        data class LaunchSystemDelete(val intent: PendingIntent) : Event
        /** Sıradaki gruba geç; null ise başka grup kalmadı (sonuçlara dön). */
        data class GoToGroup(val groupId: Int?, val deletedCount: Int) : Event
        data object DeleteFailed : Event
    }

    val group: StateFlow<PhotoGroup?> = scanRepository.state
        .map { state ->
            (state as? ScanState.Done)?.groups?.firstOrNull { it.id == groupId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = scanRepository.currentGroups().firstOrNull { it.id == groupId }
        )

    val trashMode: StateFlow<Boolean> = settingsRepository.settings
        .map { it.trashMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    /**
     * Bu ekran kendi isteğiyle başka gruba geçerken grup oturumdan düşebilir;
     * o durumda "grup yok oldu, geri dön" davranışı tetiklenmemeli.
     */
    @Volatile
    var leaving = false
        private set

    private var pendingDeletion: List<PhotoItem> = emptyList()

    fun toggleDeletion(photoId: Long) = scanRepository.toggleDeletion(groupId, photoId)
    fun setBest(photoId: Long) = scanRepository.setBest(groupId, photoId)

    /** Sonuç listesindeki sıraya göre bir sonraki grup; sondaysa baştan ilk farklı grup. */
    fun nextGroupId(): Int? {
        val groups = scanRepository.currentGroups()
        val index = groups.indexOfFirst { it.id == groupId }
        return groups.drop(index + 1).firstOrNull()?.id
            ?: groups.firstOrNull { it.id != groupId }?.id
    }

    /** Sonuç listesindeki sıraya göre bir önceki grup; baştaysa sondan ilk farklı grup. */
    fun previousGroupId(): Int? {
        val groups = scanRepository.currentGroups()
        val index = groups.indexOfFirst { it.id == groupId }
        return (if (index > 0) groups.getOrNull(index - 1) else null)?.id
            ?: groups.lastOrNull { it.id != groupId }?.id
    }

    fun goToPreviousGroup() {
        val previous = previousGroupId() ?: return
        leaving = true
        _events.tryEmit(Event.GoToGroup(previous, deletedCount = 0))
    }

    fun goToNextGroup() {
        val next = nextGroupId()
        leaving = true
        _events.tryEmit(Event.GoToGroup(next, deletedCount = 0))
    }

    fun skipGroup() {
        val next = nextGroupId()
        leaving = true
        scanRepository.skipGroup(groupId)
        _events.tryEmit(Event.GoToGroup(next, deletedCount = 0))
    }

    /**
     * Yalnızca bu gruptaki işaretlileri siler. MANAGE_MEDIA varsa sistem
     * diyaloğu olmadan, yoksa sistem silme isteğiyle.
     */
    fun deleteMarked() {
        val photos = group.value?.deletionCandidates?.map { it.photo }.orEmpty()
        if (photos.isEmpty() || _deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            val trash = settingsRepository.settings.first().trashMode
            if (deletionRepository.trySilentDelete(photos, toTrash = trash)) {
                onDeleted(photos, trash)
            } else {
                pendingDeletion = photos
                _events.emit(Event.LaunchSystemDelete(deletionRepository.buildDeleteRequest(photos, toTrash = trash)))
            }
        }
    }

    fun onSystemDeleteResult(approved: Boolean) {
        viewModelScope.launch {
            val photos = pendingDeletion
            pendingDeletion = emptyList()
            if (approved && photos.isNotEmpty()) {
                onDeleted(photos, settingsRepository.settings.first().trashMode)
            } else {
                _deleting.value = false
                _events.emit(Event.DeleteFailed)
            }
        }
    }

    private suspend fun onDeleted(photos: List<PhotoItem>, trashed: Boolean) {
        val next = nextGroupId()
        deletionRepository.recordDeletion(photos, wasAuto = false, trashed = trashed)
        leaving = true
        scanRepository.onPhotosDeleted(photos.map { it.id }.toSet())
        _events.emit(Event.GoToGroup(next, deletedCount = photos.size))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onOpenGroup: (Int) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val group by viewModel.group.collectAsState()
    val trashMode by viewModel.trashMode.collectAsState()
    val deleting by viewModel.deleting.collectAsState()
    // Telefon çevrilince tam ekran görüntüleyici açık kalsın.
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showIgnoreConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Grup başka bir yerden düştüyse (ör. sonuçlardan toplu silme) geri dön;
    // bu ekran kendisi sıradaki gruba geçiyorsa dokunma.
    LaunchedEffect(group) {
        if (group == null && !viewModel.leaving) onBack()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onSystemDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupDetailViewModel.Event.LaunchSystemDelete ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(event.intent.intentSender).build())
                is GroupDetailViewModel.Event.GoToGroup -> {
                    if (event.deletedCount > 0) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.delete_success, event.deletedCount),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    val next = event.groupId
                    if (next != null) onOpenGroup(next) else onBack()
                }
                is GroupDetailViewModel.Event.DeleteFailed ->
                    Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Tam ekran görüntüleyici ve onay pencereleri her iki düzende de tüm ekranın üstünde çizilir.
    Box(Modifier.fillMaxSize()) {
        val current = group
        if (isLandscape && current != null) {
            LandscapeGroupContent(
                group = current,
                hasOtherGroups = viewModel.nextGroupId() != null,
                deleting = deleting,
                onToggleDeletion = viewModel::toggleDeletion,
                onSetBest = viewModel::setBest,
                onOpenViewer = { viewerIndex = it },
                onDelete = { showDeleteConfirm = true },
                onIgnore = { showIgnoreConfirm = true },
                onPrevious = viewModel::goToPreviousGroup,
                onNext = viewModel::goToNextGroup
            )
        } else {
            Scaffold(
                bottomBar = {
                    val g = group
                    if (g != null) {
                        GroupBottomBar(
                            markedCount = g.deletionCandidates.size,
                            markedBytes = g.bytesToFree,
                            hasOtherGroups = viewModel.nextGroupId() != null,
                            deleting = deleting,
                            onDelete = { showDeleteConfirm = true },
                            onPrevious = viewModel::goToPreviousGroup,
                            onNext = viewModel::goToNextGroup
                        )
                    }
                },
                topBar = {
                    TopAppBar(
                        colors = appTopBarColors(),
                        title = { Text(stringResource(R.string.group_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        },
                        actions = {
                            TextButton(onClick = { showIgnoreConfirm = true }) {
                                Text(stringResource(R.string.group_skip))
                            }
                        }
                    )
                }
            ) { padding ->
                val g = group ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column {
                            if (g.isScreenshots) {
                                Text(
                                    text = stringResource(R.string.group_screenshot_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.group_tap_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    itemsIndexed(g.photos, key = { _, sp -> sp.photo.id }) { index, scored ->
                        PhotoCard(
                            scored = scored,
                            isBest = scored.photo.id == g.bestPhotoId,
                            isScreenshotGroup = g.isScreenshots,
                            onToggleDeletion = { viewModel.toggleDeletion(scored.photo.id) },
                            onSetBest = { viewModel.setBest(scored.photo.id) },
                            onOpenViewer = { viewerIndex = index }
                        )
                    }
                }
            }
        }

        val g = group
        if (g != null) {
            if (showIgnoreConfirm) {
                AlertDialog(
                    onDismissRequest = { showIgnoreConfirm = false },
                    title = { Text(stringResource(R.string.group_ignore_confirm_title)) },
                    text = { Text(stringResource(R.string.group_ignore_confirm_message, g.photos.size)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showIgnoreConfirm = false
                            viewModel.skipGroup()
                        }) {
                            Text(stringResource(R.string.group_ignore_confirm_positive))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showIgnoreConfirm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(stringResource(R.string.confirm_delete_title)) },
                    text = {
                        Column {
                            Text(
                                stringResource(
                                    R.string.group_delete_confirm_message,
                                    g.deletionCandidates.size,
                                    formatBytes(g.bytesToFree)
                                )
                            )
                            Text(
                                text = stringResource(
                                    if (trashMode) R.string.confirm_delete_trash_note else R.string.confirm_delete_permanent_note
                                ),
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
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            viewModel.deleteMarked()
                        }) {
                            Text(stringResource(R.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }

        val vi = viewerIndex
        if (g != null && vi != null) {
            PhotoViewerOverlay(
                photos = g.photos,
                bestPhotoId = g.bestPhotoId,
                isScreenshotGroup = g.isScreenshots,
                initialIndex = vi,
                onToggleDeletion = viewModel::toggleDeletion,
                onSetBest = viewModel::setBest,
                onDismiss = { viewerIndex = null }
            )
        }
    }
}

@Composable
private fun GroupBottomBar(
    markedCount: Int,
    markedBytes: Long,
    hasOtherGroups: Boolean,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (deleting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.group_deleting),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Button(
                onClick = onDelete,
                enabled = markedCount > 0 && !deleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(stringResource(R.string.group_delete_marked_now, markedCount, formatBytes(markedBytes)))
            }
            if (hasOtherGroups) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onPrevious, enabled = !deleting) {
                        Text(stringResource(R.string.group_previous))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onNext, enabled = !deleting) {
                        Text(stringResource(R.string.group_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    scored: ScoredPhoto,
    isBest: Boolean,
    isScreenshotGroup: Boolean,
    onToggleDeletion: () -> Unit,
    onSetBest: () -> Unit,
    onOpenViewer: () -> Unit
) {
    Card {
        Column {
            Box {
                AsyncImage(
                    model = scored.photo.uri,
                    contentDescription = scored.photo.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.33f)
                        .clickable(onClick = onOpenViewer)
                )
                if (isBest) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(
                                    if (isScreenshotGroup) R.string.results_newest_badge else R.string.results_best_badge
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
            }
            Column(Modifier.padding(12.dp)) {
                if (!isScreenshotGroup) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.group_score, (scored.score * 100).roundToInt()),
                            style = MaterialTheme.typography.titleSmall
                        )
                        scored.analysis.face?.let { face ->
                            ScoreBar(
                                label = stringResource(R.string.group_eyes),
                                value = face.eyesOpen
                            )
                            ScoreBar(
                                label = stringResource(R.string.group_face),
                                value = face.frontal
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "En iyi" işaretli fotoğraf dahil, herhangi bir fotoğraf silinmeye
                    // işaretlenebilir — kullanıcı yapay zekanın seçimine katılmıyorsa
                    // veya tüm grubu temizlemek istiyorsa buna izin verilir.
                    Checkbox(
                        checked = scored.markedForDeletion,
                        onCheckedChange = { onToggleDeletion() }
                    )
                    Text(
                        text = if (scored.markedForDeletion) {
                            stringResource(R.string.group_delete_marked)
                        } else {
                            stringResource(R.string.group_keep)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (scored.markedForDeletion) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (!isBest) {
                        TextButton(onClick = onSetBest) {
                            Text(stringResource(R.string.group_set_best))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, value: Float?) {
    if (value == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(0.3f)
        )
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f)
        )
    }
}
