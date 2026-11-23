package com.bestphotoselect.ui.groupdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bestphotoselect.R
import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScoredPhoto
import com.bestphotoselect.util.formatBytes
import kotlin.math.roundToInt

private val OverlayScrim = Color(0x99000000)

/**
 * Yatay mod: fotoğraflar ekran yüksekliğinin tamamını kullanır ve yan yana
 * kaydırılır (sağda bir sonrakinin kenarı görünür). Başlık/açıklama yazıları
 * yoktur; her fotoğrafın kendi kontrolleri üst kenarında, grubun genel tuşları
 * ise fotoğrafların üstünde yüzen tek bir kapsüldedir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LandscapeGroupContent(
    group: PhotoGroup,
    hasOtherGroups: Boolean,
    deleting: Boolean,
    onToggleDeletion: (Long) -> Unit,
    onSetBest: (Long) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onDelete: () -> Unit,
    onIgnore: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val listState = rememberLazyListState()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // Alt kenar boş bırakılmaz: fotoğraf gezinme çubuğuna kadar uzanır,
            // yalnızca yüzen kapsül çubuğun üstüne yerleşir.
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
        ) {
            itemsIndexed(group.photos, key = { _, sp -> sp.photo.id }) { index, scored ->
                LandscapePhotoCard(
                    scored = scored,
                    isBest = scored.photo.id == group.bestPhotoId,
                    isScreenshotGroup = group.isScreenshots,
                    onToggleDeletion = { onToggleDeletion(scored.photo.id) },
                    onSetBest = { onSetBest(scored.photo.id) },
                    onOpen = { onOpenViewer(index) },
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .fillParentMaxWidth(if (group.photos.size > 1) 0.86f else 1f)
                )
            }
        }

        LandscapeActionPill(
            markedCount = group.deletionCandidates.size,
            markedBytes = group.bytesToFree,
            hasOtherGroups = hasOtherGroups,
            deleting = deleting,
            onDelete = onDelete,
            onIgnore = onIgnore,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun LandscapePhotoCard(
    scored: ScoredPhoto,
    isBest: Boolean,
    isScreenshotGroup: Boolean,
    onToggleDeletion: () -> Unit,
    onSetBest: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.Black)
            // Silinecek kare kırmızı çerçeveyle hemen ayırt edilir.
            .border(
                width = if (scored.markedForDeletion) 3.dp else 0.dp,
                color = if (scored.markedForDeletion) MaterialTheme.colorScheme.error else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onOpen)
    ) {
        AsyncImage(
            model = scored.photo.uri,
            contentDescription = scored.photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(OverlayScrim)
                .padding(end = 8.dp)
        ) {
            Checkbox(
                checked = scored.markedForDeletion,
                onCheckedChange = { onToggleDeletion() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.error,
                    uncheckedColor = Color.White,
                    checkmarkColor = Color.White
                )
            )
            Text(
                text = stringResource(if (scored.markedForDeletion) R.string.group_delete_marked else R.string.group_keep),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.weight(1f))
            if (!isScreenshotGroup) {
                Text(
                    text = stringResource(R.string.group_score, (scored.score * 100).roundToInt()),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            if (isBest) {
                Text(
                    text = "★ " + stringResource(
                        if (isScreenshotGroup) R.string.results_newest_badge else R.string.results_best_badge
                    ),
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.labelLarge
                )
            } else {
                TextButton(onClick = onSetBest) {
                    Text(
                        text = stringResource(R.string.group_set_best),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeActionPill(
    markedCount: Int,
    markedBytes: Long,
    hasOtherGroups: Boolean,
    deleting: Boolean,
    onDelete: () -> Unit,
    onIgnore: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            if (hasOtherGroups) {
                IconButton(onClick = onPrevious, enabled = !deleting) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.group_previous)
                    )
                }
            }
            IconButton(onClick = onIgnore, enabled = !deleting) {
                Icon(Icons.Default.VisibilityOff, contentDescription = stringResource(R.string.group_skip))
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onDelete,
                enabled = markedCount > 0 && !deleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (deleting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.group_deleting))
                } else {
                    Text(stringResource(R.string.group_delete_marked_now, markedCount, formatBytes(markedBytes)))
                }
            }
            Spacer(Modifier.width(4.dp))
            if (hasOtherGroups) {
                IconButton(onClick = onNext, enabled = !deleting) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.group_next)
                    )
                }
            }
        }
    }
}
