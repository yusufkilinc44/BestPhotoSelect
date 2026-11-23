package com.bestphotoselect.ui.about

import com.bestphotoselect.util.rememberAppVersion
import com.bestphotoselect.ui.theme.accentFor
import androidx.compose.material3.CardDefaults
import com.bestphotoselect.ui.theme.appTopBarColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bestphotoselect.R

// Metinler FaceAnalyzer / DHash / PhotoGrouper / BestPhotoSelector / ScoringWeights
// içindeki gerçek sabitlerden alınmıştır; onlar değişirse strings.xml de güncellenmeli.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAlgorithmScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = appTopBarColors(),
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.about_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.app_name) + " · " +
                    stringResource(R.string.app_version, rememberAppVersion()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary
            )
            InfoCard(0, R.string.about_model_title, R.string.about_model_body)
            InfoCard(4, R.string.about_similarity_title, R.string.about_similarity_body)
            InfoCard(2, R.string.about_scoring_title, R.string.about_scoring_body, R.string.about_scoring_note)
            InfoCard(1, R.string.about_screenshots_title, R.string.about_screenshots_body)
            InfoCard(3, R.string.about_privacy_title, R.string.about_privacy_body)
        }
    }
}

@Composable
private fun InfoCard(accentKey: Long, titleRes: Int, bodyRes: Int, noteRes: Int? = null) {
    val accent = accentFor(accentKey)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.container, contentColor = accent.content)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (noteRes != null) {
                Text(
                    text = stringResource(noteRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}
