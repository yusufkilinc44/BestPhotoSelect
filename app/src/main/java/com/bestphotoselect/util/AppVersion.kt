package com.bestphotoselect.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Kurulu uygulamanın sürümü, ör. "2.11.0 (37)"; okunamazsa boş. */
fun appVersion(context: Context): String = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    "${info.versionName} (${info.longVersionCode})"
}.getOrDefault("")

@Composable
fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember { appVersion(context) }
}
