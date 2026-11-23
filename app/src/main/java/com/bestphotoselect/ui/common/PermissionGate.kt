package com.bestphotoselect.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bestphotoselect.R
import com.bestphotoselect.util.GalleryAccess

/**
 * Galeri izni kapısı.
 *
 * - Tam erişim: içerik doğrudan gösterilir.
 * - Kısmi erişim (Android 14+ "Fotoğrafları seç"): içerik yalnızca seçilen
 *   fotoğraflarla çalışır; üstte tüm fotoğraflara erişim vermeyi öneren uyarı çıkar.
 * - Erişim yok: açıklama ve izin düğmesi. İzin kalıcı reddedildiyse (sistem artık
 *   pencere göstermiyorsa) düğme uygulamanın izin ayarlarını açar.
 *
 * Kullanıcı ayarlardan izni değiştirip dönerse durum yeniden okunur.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permissions = remember { GalleryAccess.requestPermissions() }

    var access by remember { mutableStateOf(GalleryAccess.current(context)) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    // İzin penceresinden her dönüşte artar; içerik (albüm listesi) yeniden yüklenir.
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = GalleryAccess.current(context)
        if (now != access) {
            access = now
            if (now != GalleryAccess.Access.NONE) permanentlyDenied = false
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        access = GalleryAccess.current(context)
        refreshToken++
        // Sistem penceresi gösterilmeden ret döndüyse ve gerekçe de gösterilemiyorsa
        // izin kalıcı reddedilmiştir; artık yalnızca ayarlardan verilebilir.
        val activity = context.findActivity()
        permanentlyDenied = access == GalleryAccess.Access.NONE && activity != null &&
            permissions.none { activity.shouldShowRequestPermissionRationale(it) }
    }

    when (access) {
        GalleryAccess.Access.FULL -> key(refreshToken) { content() }
        GalleryAccess.Access.PARTIAL -> Column(Modifier.fillMaxSize()) {
            PartialAccessBanner(onAllowAll = { launcher.launch(permissions) })
            Column(Modifier.weight(1f)) { key(refreshToken) { content() } }
        }
        GalleryAccess.Access.NONE -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    if (permanentlyDenied) R.string.permission_denied_message else R.string.permission_message
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = {
                if (permanentlyDenied) context.openAppSettings() else launcher.launch(permissions)
            }) {
                Text(
                    stringResource(
                        if (permanentlyDenied) R.string.permission_open_settings else R.string.permission_grant
                    )
                )
            }
        }
    }
}

@Composable
private fun PartialAccessBanner(onAllowAll: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.permission_partial_banner),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAllowAll) {
                Text(stringResource(R.string.permission_partial_allow_all))
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
