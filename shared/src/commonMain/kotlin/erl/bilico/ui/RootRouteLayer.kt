package erl.bilico.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import erl.bilico.di.AppContainer
import erl.bilico.feature.article.ArticleScreen
import erl.bilico.feature.chat.ChatScreen
import erl.bilico.feature.comment.EmoteManagerScreen
import erl.bilico.feature.download.DownloadsScreen
import erl.bilico.feature.dynamic.DynamicDetailScreen
import erl.bilico.feature.favorite.FavoriteFolderScreen
import erl.bilico.feature.favorite.FavoriteFoldersScreen
import erl.bilico.feature.following.FollowingScreen
import erl.bilico.feature.history.HistoryScreen
import erl.bilico.feature.live.LiveRoomScreen
import erl.bilico.feature.login.LoginScreen
import erl.bilico.feature.login.WebLoginScreen
import erl.bilico.feature.me.WebProfileEditScreen
import erl.bilico.feature.message.MessageScreen
import erl.bilico.feature.pgc.BangumiFollowScreen
import erl.bilico.feature.pgc.PgcScreen
import erl.bilico.feature.search.SearchScreen
import erl.bilico.feature.settings.AboutScreen
import erl.bilico.feature.settings.BlockManagerScreen
import erl.bilico.feature.settings.SettingsScreen
import erl.bilico.feature.settings.StatsOverlaySettingsScreen
import erl.bilico.feature.space.SpaceCollectionScreen
import erl.bilico.feature.space.SpaceScreen
import erl.bilico.feature.update.UpdateScreen
import erl.bilico.feature.video.VideoScreen
import erl.bilico.feature.watchlater.WatchLaterScreen
import erl.bilico.platform.LocalScreenConfig
import erl.bilico.platform.ScreenConfig
import erl.bilico.ui.components.InAppWebScreen
import io.github.suqi8.coui.kmp.squircle.SquircleShape

@Composable
internal fun RouteLayer(
    entry: NavEntry,
    index: Int,
    lastIndex: Int,
    widthPx: Float,
    screenCorner: Dp,
    nav: NavState,
    container: AppContainer,
    screenConfig: ScreenConfig,
    frozen: FrozenPortrait?,
) {
    val progress: () -> Float = { entry.progress.value }
    val isMinimized = index < 0
    val activeTop = nav.stack.lastOrNull { !it.exiting }
    val activeBelowTop = nav.stack.filter { !it.exiting }.let { if (it.size >= 2) it[it.size - 2] else null }
    val isTop = !isMinimized && entry === activeTop
    val keepSurface = isTop || (
        !isMinimized && entry.exiting &&
            !(entry.route is Route.Video && activeTop?.route is Route.Video)
        )
    val belowTop = !isMinimized && entry === activeBelowTop
    val slideDriver = nav.stack.lastOrNull { it.exiting } ?: activeTop
    val topProgress: () -> Float = { slideDriver?.progress?.value ?: 1f }
    val slide = !isMinimized && !entry.expand && (entry === slideDriver || (slideDriver != null && !slideDriver.expand))
    val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }

    val covered by remember(isMinimized, index, nav.stack.size) {
        derivedStateOf {
            isMinimized || (index >= 0 && nav.stack.drop(index + 1).any {
                !it.exiting && !it.previewing && it.progress.value >= 1f
            })
        }
    }

    val layer = Modifier
        .fillMaxSize()
        .zIndex(if (isMinimized) -1f else 1f + index * 0.01f)
        .then(
            if (isTop) {
                Modifier.pointerInput(Unit) { detectTapGestures { } }
            } else {
                Modifier
            },
        )
        .graphicsLayer {
            if (isMinimized) {
                alpha = 0f
                scaleX = 0f
                scaleY = 0f
                clip = false
                return@graphicsLayer
            }
            if (slide) {
                translationX = when {
                    entry === slideDriver -> (1f - progress()) * widthPx
                    isTop || belowTop -> -topProgress() * widthPx / RouteParallaxDivisor
                    else -> 0f
                }
                if (progress() < 1f || topProgress() < 1f) {
                    shape = cornerShape
                    clip = true
                } else {
                    clip = false
                }
            }
        }
        .drawWithContent {
            if (!covered) {
                drawContent()
            }
        }

    val transition = remember(entry) {
        RouteTransition(
            progress = progress,
            exiting = { entry.exiting || entry.previewing },
        )
    }

    val freeze = if (!isTop) frozen else null
    val layerConfig = freeze?.configuration ?: screenConfig
    val freezeLayout = Modifier.layout { measurable, constraints ->
        val inner = if (freeze != null) Constraints.fixed(freeze.width, freeze.height) else constraints
        val placeable = measurable.measure(inner)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

    // 非栈顶页仍留在组合树里，必须按 isTop 停用其返回分发器，避免后台页面抢走栈顶预测性返回。
    val routeDispatcherOwner = rememberNavigationEventDispatcherOwner(
        enabled = isTop,
        parent = LocalNavigationEventDispatcherOwner.current,
    )
    SideEffect {
        routeDispatcherOwner.navigationEventDispatcher.isEnabled = isTop
    }

    CompositionLocalProvider(
        LocalRouteTransition provides transition,
        LocalRouteIsTop provides isTop,
        LocalRouteKeepSurface provides keepSurface,
        LocalRouteMinimized provides isMinimized,
        LocalNavEntry provides entry,
        LocalScreenConfig provides layerConfig,
        LocalNavigationEventDispatcherOwner provides routeDispatcherOwner,
    ) {
        val bypassExitSnapshot = entry.hasSurfaceView || entry.route.isPlayback()
        val slideDriverIndex = if (slideDriver != null) nav.stack.indexOf(slideDriver) else -1
        val belowSlideDriver = !isMinimized && index >= 0 && slideDriverIndex > 0 && index == slideDriverIndex - 1
        val freezeSelf = !bypassExitSnapshot && ((entry.expand && entry.animating) || entry.exiting || entry.previewing)
        val freezeBelow = belowSlideDriver && !bypassExitSnapshot && slideDriver != null && slideDriver.belowFrozen
        val freezeSnapshot = freezeSelf || freezeBelow
        Box(layer.then(freezeLayout)) {
            if (entry.hasExpandOrigin || entry.route.isPlayback() || entry.route is Route.Opus) {
                ExpandLayer(entry, screenCorner, progress, { entry.fade.value }, { entry.offset.value }) {
                    ExitSnapshotHost(exiting = freezeSnapshot, hasSurfaceView = bypassExitSnapshot) {
                        RouteContent(entry.route, nav, container)
                    }
                }
            } else {
                ExitSnapshotHost(exiting = freezeSnapshot, hasSurfaceView = bypassExitSnapshot) {
                    RouteContent(entry.route, nav, container)
                }
            }
        }
    }
}

@Composable
private fun RouteContent(route: Route, nav: NavState, container: AppContainer) {
    when (route) {
        Route.Main -> MainShell(nav, container)
        is Route.Video -> VideoScreen(
            container = container,
            nav = nav,
            bvid = route.bvid,
            startPositionMs = route.startSec * 1000,
            initialCid = route.cid,
            commentRootId = route.commentRootId,
            commentTargetId = route.commentTargetId,
            commentMatchAuthor = route.commentMatchAuthor,
            commentMatchText = route.commentMatchText,
            offline = route.offline,
            promoted = route.promoted,
        )
        is Route.LiveRoom -> LiveRoomScreen(container, nav, route.roomId)
        is Route.Space -> SpaceScreen(nav, container, route.mid)
        is Route.SpaceCollection -> SpaceCollectionScreen(
            nav = nav,
            container = container,
            mid = route.mid,
            collectionId = route.collectionId,
            isSeason = route.isSeason,
            title = route.title,
            total = route.total,
        )
        Route.History -> HistoryScreen(container, nav)
        Route.Favorites -> FavoriteFoldersScreen(container, nav)
        is Route.FavoriteFolder -> FavoriteFolderScreen(container, nav, route.mediaId, route.title)
        Route.WatchLater -> WatchLaterScreen(container, nav)
        Route.Downloads -> DownloadsScreen(container, nav)
        Route.Following -> FollowingScreen(container, nav)
        Route.Settings -> SettingsScreen(container, nav)
        Route.StatsOverlaySettings -> StatsOverlaySettingsScreen(container, nav)
        Route.BlockManager -> BlockManagerScreen(container, nav)
        Route.About -> AboutScreen(container, nav)
        is Route.Pgc -> PgcScreen(container, nav, route.seasonId, route.epId, route.startSec * 1000, route.offline)
        Route.Messages -> MessageScreen(container, nav)
        Route.BangumiFollow -> BangumiFollowScreen(container, nav)
        Route.Login -> LoginScreen(container, nav)
        Route.WebLogin -> WebLoginScreen(container, nav)
        Route.WebProfileEdit -> WebProfileEditScreen(container, nav)
        is Route.EmoteManager -> EmoteManagerScreen(container, nav, route.url)
        is Route.WebPage -> InAppWebScreen(container, nav, route.url, route.title)
        Route.Update -> UpdateScreen(container, nav)
        is Route.Article -> ArticleScreen(
            container = container,
            nav = nav,
            cvId = route.cvId,
            initialTitle = route.title,
            commentRootId = route.commentRootId,
            commentTargetId = route.commentTargetId,
            commentMatchAuthor = route.commentMatchAuthor,
            commentMatchText = route.commentMatchText,
        )
        is Route.Opus -> DynamicDetailScreen(
            container = container,
            nav = nav,
            dynamicId = route.dynamicId,
            commentRootId = route.commentRootId,
            commentTargetId = route.commentTargetId,
            commentMatchAuthor = route.commentMatchAuthor,
            commentMatchText = route.commentMatchText,
        )
        is Route.Chat -> ChatScreen(container, nav, route.talkerId, route.talkerName, route.talkerFace)
        is Route.Search -> SearchScreen(container, nav, route.query)
    }
}
