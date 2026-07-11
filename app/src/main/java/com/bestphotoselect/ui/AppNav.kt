package com.bestphotoselect.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bestphotoselect.ui.albums.AlbumsScreen
import com.bestphotoselect.ui.groupdetail.GroupDetailScreen
import com.bestphotoselect.ui.history.HistoryScreen
import com.bestphotoselect.ui.results.ResultsScreen
import com.bestphotoselect.ui.scan.ScanScreen
import com.bestphotoselect.ui.settings.SettingsScreen

@Composable
fun AppNav() {
    val nav = rememberNavController()
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
            GroupDetailScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("history") {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}
