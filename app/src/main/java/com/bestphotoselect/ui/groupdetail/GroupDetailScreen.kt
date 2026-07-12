package com.bestphotoselect.ui.groupdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val groupId: Int = checkNotNull(savedStateHandle["groupId"])

    val group: StateFlow<PhotoGroup?> = scanRepository.state
        .map { state ->
            (state as? ScanState.Done)?.groups?.firstOrNull { it.id == groupId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = scanRepository.currentGroups().firstOrNull { it.id == groupId }
        )

    fun toggleDeletion(photoId: Long) = scanRepository.toggleDeletion(groupId, photoId)
    fun setBest(photoId: Long) = scanRepository.setBest(groupId, photoId)
    fun skipGroup() = scanRepository.skipGroup(groupId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val group by viewModel.group.collectAsState()

    // Grup silindi/atlandıysa geri dön.
    LaunchedEffect(group) {
        if (group == null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.skipGroup()
                        onBack()
                    }) {
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
            items(g.photos, key = { it.photo.id }) { scored ->
                PhotoCard(
                    scored = scored,
                    isBest = scored.photo.id == g.bestPhotoId,
                    onToggleDeletion = { viewModel.toggleDeletion(scored.photo.id) },
                    onSetBest = { viewModel.setBest(scored.photo.id) }
                )
            }
        }
    }
}

@Composable
private fun PhotoCard(
    scored: ScoredPhoto,
    isBest: Boolean,
    onToggleDeletion: () -> Unit,
    onSetBest: () -> Unit
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
                )
                if (isBest) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.results_best_badge)) },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
