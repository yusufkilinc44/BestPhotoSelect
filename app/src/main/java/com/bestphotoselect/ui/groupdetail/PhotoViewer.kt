package com.bestphotoselect.ui.groupdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bestphotoselect.R
import com.bestphotoselect.data.model.ScoredPhoto
import kotlin.math.roundToInt

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Grup içi tam ekran karşılaştırma: fotoğraflar arasında yan yana kaydırarak
 * gezinilir, pinch/çift dokunuşla yakınlaştırılır; "en iyi yap" ve "sil
 * işaretle" aksiyonları buradan da uygulanabilir.
 */
@Composable
fun PhotoViewerOverlay(
    photos: List<ScoredPhoto>,
    bestPhotoId: Long?,
    isScreenshotGroup: Boolean,
    initialIndex: Int,
    onToggleDeletion: (Long) -> Unit,
    onSetBest: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return
    val startPage = initialIndex.coerceIn(0, photos.size - 1)
    val pagerState = rememberPagerState(initialPage = startPage) { photos.size }
    var pagerScrollEnabled by remember { mutableStateOf(true) }

    // Ayrı bir Dialog penceresi sistem çubuğu boşluklarını her cihazda iletmiyordu;
    // görüntüleyici ana pencerede tüm ekranı kaplayan bir katman olarak çizilir ve
    // kontroller safeDrawing alanına (gezinme çubuğunun üstüne) yerleşir.
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Alttaki grup ekranına dokunuş geçmesin.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = pagerScrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                scored = photos[page],
                onZoomChanged = { zoomed -> pagerScrollEnabled = !zoomed }
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(8.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.back),
                tint = Color.White
            )
        }

        val current = photos[pagerState.currentPage.coerceIn(0, photos.size - 1)]
        val isBest = current.photo.id == bestPhotoId

        Text(
            text = stringResource(R.string.viewer_counter, pagerState.currentPage + 1, photos.size),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(16.dp)
        )

        ViewerBottomBar(
            scored = current,
            isBest = isBest,
            isScreenshotGroup = isScreenshotGroup,
            onSetBest = { onSetBest(current.photo.id) },
            onToggleDeletion = { onToggleDeletion(current.photo.id) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .safeDrawingPadding()
                .padding(16.dp)
        )
    }
}

@Composable
private fun ViewerBottomBar(
    scored: ScoredPhoto,
    isBest: Boolean,
    isScreenshotGroup: Boolean,
    onSetBest: () -> Unit,
    onToggleDeletion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusChip(
                text = if (isScreenshotGroup) {
                    stringResource(
                        when {
                            scored.markedForDeletion -> R.string.viewer_will_delete
                            isBest -> R.string.results_newest_badge
                            else -> R.string.group_keep
                        }
                    )
                } else {
                    stringResource(R.string.group_score, (scored.score * 100).roundToInt())
                }
            )
            scored.analysis.face?.let { face ->
                Spacer(Modifier.width(6.dp))
                StatusChip(text = "👁 ${(face.eyesOpen * 100).roundToInt()}%")
            }
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp)
        ) {
            if (!isBest) {
                TextButton(onClick = onSetBest) {
                    Text(stringResource(R.string.viewer_make_best), color = Color.White)
                }
            }
            TextButton(onClick = onToggleDeletion) {
                Text(
                    text = if (scored.markedForDeletion) {
                        stringResource(R.string.viewer_unmark_delete)
                    } else {
                        stringResource(R.string.viewer_mark_delete)
                    },
                    color = if (scored.markedForDeletion) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.White
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text, color = Color.White) },
        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x66000000))
    )
}

@Composable
private fun ZoomableImage(
    scored: ScoredPhoto,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember(scored.photo.id) { mutableFloatStateOf(MIN_SCALE) }
    var offsetX by remember(scored.photo.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(scored.photo.id) { mutableFloatStateOf(0f) }

    fun applyScale(newScale: Float) {
        scale = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (scale <= MIN_SCALE) {
            offsetX = 0f
            offsetY = 0f
        }
        onZoomChanged(scale > MIN_SCALE)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Yalnızca iki parmakla ya da zaten yakınlaştırılmışken tüketilir;
            // tek parmakla yatay kaydırma pager'a kalır (fotoğraflar arası geçiş).
            .pointerInput(scored.photo.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || scale > MIN_SCALE) {
                            applyScale(scale * event.calculateZoom())
                            if (scale > MIN_SCALE) {
                                val pan = event.calculatePan()
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(scored.photo.id) {
                detectTapGestures(
                    onDoubleTap = {
                        applyScale(if (scale > MIN_SCALE) MIN_SCALE else DOUBLE_TAP_SCALE)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = scored.photo.uri,
            contentDescription = scored.photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}
