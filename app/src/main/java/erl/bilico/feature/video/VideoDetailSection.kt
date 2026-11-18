package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.model.VideoEpisode
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.CommentTab
import erl.bilico.feature.comment.ReplyDetailOverlay
import erl.bilico.feature.comment.ReplyThreadController
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.Route
import erl.bilico.ui.components.LocalCommentSeek
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Playlist
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 竖屏播放页下半部分的「简介 / 评论」横向切换区域及顶部悬浮毛玻璃切换栏。
 */
@Composable
internal fun VideoDetailSection(
    container: AppContainer,
    nav: NavState,
    state: VideoUiState,
    detailPager: PagerState,
    infoListState: LazyListState,
    commentListState: LazyListState,
    replyThread: ReplyThreadController,
    replyListState: LazyListState,
    danmakuEnabled: Boolean,
    danmakuComposing: Boolean,
    danmakuDraft: String,
    onOpenDanmakuSettings: (() -> Unit)? = null,
    onDanmakuDraftChange: (String) -> Unit,
    onDanmakuComposingChange: (Boolean) -> Unit,
    onSendDanmaku: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onRetry: () -> Unit,
    onSelectPart: (Int) -> Unit,
    onSelectEpisode: (VideoEpisode) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenCoin: () -> Unit,
    onOpenFavorite: () -> Unit,
    onShare: () -> Unit,
    onSeekCommentMs: (Long) -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onWatchLater: () -> Unit,
    onFollow: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    chapters: List<VideoChapter> = emptyList(),
    positionMs: Long = 0L,
    onSelectChapter: (VideoChapter) -> Unit = {},
    onOpenChapters: () -> Unit = {},
    barBackdrops: VideoBarBackdrops? = null,
    blurActive: Boolean = true,
    backEnabled: Boolean = true,
    commentRootId: Long = 0L,
    commentTargetId: Long = 0L,
) {
    val navBarBottom = rememberStableNavBarBottomDp()
    val fallbackBackdrop = rememberLayerBackdrop()
    val detailLayerBackdrop = barBackdrops?.detail ?: fallbackBackdrop
    val barMaterialBackdrop = barBackdrops?.barMaterial
    val pageBg = pageContainerColor()
    val statusBarDp = with(LocalDensity.current) { rememberStableStatusBarHeightPx().toDp() }
    val firstRowHeight = statusBarDp + CollapsedBarHeight
    // 列表整块铺到最上面（一直到折叠态状态栏），滚动时正文从两行栏底下穿过去；
    // 静止时靠列表顶部内边距让出折叠栏 + 弹幕切换栏，第一行不会被压住。
    val contentTop = firstRowHeight + DETAIL_BAR_HEIGHT + 12.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg),
    ) {
        HorizontalPager(
            state = detailPager,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled && blurActive, detailLayerBackdrop)
                .background(pageBg),
            beyondViewportPageCount = 0,
            key = { DETAIL_PAGES[it] },
        ) { page ->
            when (page) {
                0 -> Box(Modifier.fillMaxSize()) {
                    VideoInfoTab(
                        detail = state.detail,
                        isLoading = state.isLoading,
                        error = state.error,
                        selectedCid = state.selectedCid,
                        onRetry = onRetry,
                        onSelectPart = onSelectPart,
                        onSelectEpisode = onSelectEpisode,
                        onOpenUser = onOpenUser,
                        onOpenVideo = onOpenVideo,
                        related = state.related,
                        container = container,
                        liked = state.liked,
                        disliked = state.disliked,
                        coined = state.coined,
                        favorited = state.favorited,
                        following = state.following,
                        upFollowers = state.upFollowers,
                        onLike = onLike,
                        onDislike = onDislike,
                        onCoin = onOpenCoin,
                        onFavorite = onOpenFavorite,
                        onWatchLater = onWatchLater,
                        onFollow = onFollow,
                        onShare = onShare,
                        listState = infoListState,
                        topInset = contentTop,
                        bottomInset = navBarBottom,
                        chapters = chapters,
                        positionMs = positionMs,
                        onSelectChapter = onSelectChapter,
                        onOpenChapters = onOpenChapters,
                        onTagClick = { tag ->
                            val keyword = tag.trim()
                            if (keyword.isNotEmpty()) nav.push(Route.Search(keyword))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (nav.playQueue.items.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = navBarBottom),
                        ) {
                            PinnedBarDensity {
                                Box(Modifier.fillMaxSize()) {
                                    FloatingActionButton(
                                        onClick = onOpenQueue,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(
                                                end = BottomFloatingAccessoryNavigationBarDefaults.PaddingHorizontal,
                                                bottom = (BottomFloatingNavigationBarDefaults.MinHeightWithoutPaddingVertical -
                                                    BottomFloatingAccessoryNavigationBarDefaults.AccessorySize) / 2,
                                            )
                                            .size(BottomFloatingAccessoryNavigationBarDefaults.AccessorySize)
                                            .semantics { contentDescription = "播放队列" },
                                    ) {
                                        Icon(
                                            imageVector = COUIIcons.Playlist,
                                            contentDescription = "播放队列",
                                            modifier = Modifier.size(24.dp),
                                            tint = COUITheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    val aid = state.detail?.aid ?: 0L
                    if (aid > 0L) {
                        CompositionLocalProvider(
                            LocalCommentSeek provides onSeekCommentMs,
                        ) {
                            CommentTab(
                                container = container,
                                oid = aid,
                                onOpenUser = onOpenUser,
                                listState = commentListState,
                                topInset = contentTop,
                                bottomInset = navBarBottom,
                                targetRootId = commentRootId,
                                targetReplyId = commentTargetId,
                                replyThreadController = replyThread,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        DetailBarGlassSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            containerColor = pageBg,
            backdrop = if (BlurEffectsEnabled && blurActive) detailLayerBackdrop else null,
            materialBackdrop = if (BlurEffectsEnabled && blurActive) barMaterialBackdrop else null,
            topInset = firstRowHeight,
        ) {
            VideoDetailBar(
                selected = detailPager.currentPage,
                onSelect = onSelectPage,
                commentCount = state.detail?.reply ?: 0L,
                danmakuEnabled = danmakuEnabled,
                onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                onOpenDanmakuSettings = onOpenDanmakuSettings,
                composing = danmakuComposing,
                draft = danmakuDraft,
                onDraftChange = onDanmakuDraftChange,
                onComposingChange = onDanmakuComposingChange,
                onSend = onSendDanmaku,
                backdrop = if (BlurEffectsEnabled && blurActive) barMaterialBackdrop ?: detailLayerBackdrop else null,
            )
        }

        val aid = state.detail?.aid ?: 0L
        if (aid > 0L) {
            CompositionLocalProvider(
                LocalCommentSeek provides onSeekCommentMs,
            ) {
                ReplyDetailOverlay(
                    controller = replyThread,
                    container = container,
                    oid = aid,
                    listState = replyListState,
                    topInset = firstRowHeight,
                    bottomInset = navBarBottom,
                    backEnabled = backEnabled,
                    onOpenUser = onOpenUser,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
