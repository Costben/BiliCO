package erl.bilico.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicScreen
import erl.bilico.feature.feed.FeedScreen
import erl.bilico.feature.history.HistoryScreen
import erl.bilico.feature.live.LiveListScreen
import erl.bilico.feature.live.LiveRoomScreen
import erl.bilico.feature.me.MeScreen
import erl.bilico.feature.video.VideoScreen
import io.github.suqi8.coui.kmp.basic.NavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationBarItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.Play

private data class TopTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val TOP_TABS = listOf(
    TopTab("feed", "推荐", COUIIcons.Home),
    TopTab("dynamic", "动态", COUIIcons.Community),
    TopTab("live", "直播", COUIIcons.Play),
    TopTab("me", "我的", COUIIcons.Contacts),
)

@Composable
fun BiliCoApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TOP_TABS.any { it.route == currentRoute }

    fun openTab(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo("feed") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "feed",
            modifier = Modifier.weight(1f),
        ) {
            composable("feed") {
                FeedScreen(
                    container = container,
                    onOpenVideo = { bvid -> navController.navigate("video/$bvid") },
                )
            }
            composable("dynamic") {
                DynamicScreen(
                    container = container,
                    onOpenVideo = { bvid -> navController.navigate("video/$bvid") },
                    onOpenMe = { openTab("me") },
                )
            }
            composable("live") {
                LiveListScreen(
                    container = container,
                    onOpenRoom = { roomId -> navController.navigate("live/$roomId") },
                )
            }
            composable(
                route = "live/{roomId}",
                arguments = listOf(navArgument("roomId") { type = NavType.LongType }),
            ) { entry ->
                val roomId = entry.arguments?.getLong("roomId") ?: 0L
                LiveRoomScreen(
                    container = container,
                    roomId = roomId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("me") {
                MeScreen(
                    container = container,
                    onOpenHistory = { navController.navigate("history") },
                )
            }
            composable("history") {
                HistoryScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenVideo = { bvid, startSec ->
                        navController.navigate("video/$bvid?t=$startSec")
                    },
                )
            }
            composable(
                route = "video/{bvid}?t={t}",
                arguments = listOf(
                    navArgument("t") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                val bvid = entry.arguments?.getString("bvid").orEmpty()
                val startSec = entry.arguments?.getLong("t") ?: 0L
                VideoScreen(
                    container = container,
                    bvid = bvid,
                    startPositionMs = startSec * 1000,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (showBottomBar) {
            NavigationBar(defaultWindowInsetsPadding = true) {
                TOP_TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { openTab(tab.route) },
                        icon = tab.icon,
                        label = tab.label,
                    )
                }
            }
        }
    }
}
