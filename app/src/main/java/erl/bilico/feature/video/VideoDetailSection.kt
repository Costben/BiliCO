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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.VideoEpisode
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.CommentTab
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.components.LocalCommentSeek
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
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
    danmakuEnabled: Boolean,
    danmakuComposing: Boolean,
    danmakuDraft: String,
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
) {
    val navBarBottom = rememberStableNavBarBottomDp()
    val detailLayerBackdrop = rememberLayerBackdrop()
    val pageBg = pageContainerColor()
    // 列表整块铺到最上面，滚动时正文从标签栏底下穿过去，由栏的毛玻璃盖住；
    // 静止时靠列表自己的顶部内边距让开这一条，第一行不会被栏压住。
    val contentTop = DETAIL_BAR_HEIGHT + 12.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg),
    ) {
        HorizontalPager(
            state = detailPager,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = navBarBottom)
                .layerBackdropIf(BlurEffectsEnabled, detailLayerBackdrop)
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
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (nav.playQueue.items.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = onOpenQueue,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
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
            backdrop = if (BlurEffectsEnabled) detailLayerBackdrop else null,
        ) {
            VideoDetailBar(
                selected = detailPager.currentPage,
                onSelect = onSelectPage,
                commentCount = state.detail?.reply ?: 0L,
                danmakuEnabled = danmakuEnabled,
                onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                composing = danmakuComposing,
                draft = danmakuDraft,
                onDraftChange = onDanmakuDraftChange,
                onComposingChange = onDanmakuComposingChange,
                onSend = onSendDanmaku,
            )
        }
    }
}
