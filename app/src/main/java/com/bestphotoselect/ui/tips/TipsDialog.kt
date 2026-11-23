package com.bestphotoselect.ui.tips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestphotoselect.R
import com.bestphotoselect.ui.theme.accentFor

/**
 * İpuçlarını tek tek gösteren renkli pencere. Her ipucu ekrana geldiği an
 * [onShown] ile "görüldü" sayılır; "Kapat" kalanları sonraki açılışlara bırakır.
 */
@Composable
fun TipsDialog(tips: List<Tip>, onShown: (Tip) -> Unit, onDismiss: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val tip = tips.getOrNull(index) ?: return
    LaunchedEffect(tip.id) { onShown(tip) }
    val accent = accentFor(Tips.ALL.indexOf(tip).toLong())
    val isLast = index == tips.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(accent.container),
                contentAlignment = Alignment.Center
            ) {
                Text(tip.emoji, fontSize = 36.sp)
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (tips.size > 1) {
                        stringResource(R.string.tip_label_count, index + 1, tips.size)
                    } else {
                        stringResource(R.string.tip_label)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(tip.titleRes),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Text(
                text = stringResource(tip.bodyRes),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = { if (isLast) onDismiss() else index++ }) {
                Text(stringResource(if (isLast) R.string.tip_done else R.string.tip_next))
            }
        },
        dismissButton = if (isLast) null else {
            {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.tip_close)) }
                }
            }
        }
    )
}
