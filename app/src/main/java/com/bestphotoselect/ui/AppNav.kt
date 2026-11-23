package com.bestphotoselect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bestphotoselect.ui.about.AboutAlgorithmScreen
import com.bestphotoselect.ui.albums.AlbumsScreen
import com.bestphotoselect.ui.groupdetail.GroupDetailScreen
import com.bestphotoselect.ui.history.HistoryScreen
import com.bestphotoselect.ui.results.ResultsScreen
import com.bestphotoselect.ui.scan.ScanScreen
import com.bestphotoselect.ui.settings.SettingsScreen

@Composable
fun AppNav(openResultsRequest: Int = 0) {
    val nav = rememberNavController()
    // Otomatik tarama bildirimine dokunulduysa sonuç ekranını aç.
    LaunchedEffect(openResultsRequest) {
        if (openResultsRequest > 0) {
            nav.navigate("results") {
                popUpTo("albums")
                launchSingleTop = true
            }
        }
    }
    NavHost(navController = nav, startDestination = "albums") {
        composable("albums") {
            AlbumsScreen(
                onScanStarted = { nav.navigate("scan") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenHistory = { nav.navigate("history") }
            )
        }
        composable("scan") {
            ScanScreen(
                onDone = {
                    nav.navigate("results") {
                        popUpTo("albums")
                    }
                },
                onCancelled = { nav.popBackStack() }
            )
        }
        composable("results") {
            ResultsScreen(
                onOpenGroup = { groupId -> nav.navigate("group/$groupId") },
                onBack = { nav.popBackStack("albums", inclusive = false) }
            )
        }
        composable(
            "group/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.IntType })
        ) {
            GroupDetailScreen(
                onBack = { nav.popBackStack() },
                // Sıradaki gruba geçerken mevcut grup yığından çıkarılır: geri
                // tuşu gezilen tüm grupları tek tek değil, doğrudan sonuçları açar.
                onOpenGroup = { nextId ->
                    nav.navigate("group/$nextId") {
                        popUpTo("group/{groupId}") { inclusive = true }
                    }
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAbout = { nav.navigate("about") }
            )
        }
        composable("about") {
            AboutAlgorithmScreen(onBack = { nav.popBackStack() })
        }
        composable("history") {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}
