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
import androidx.compose.runtime.remember
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
import erl.bilico.ui.LocalTopBarBounds
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.Route
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.components.LocalCommentSeek
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.reportTopBarBottom
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
    onDownload: (() -> Unit)? = null,
    downloaded: Boolean = false,
    onSeekCommentMs: (Long) -> Unit,
    onLike: () -> Unit,
    onTriple: (() -> Unit)? = null,
    onDislike: () -> Unit,
    showDislike: Boolean = true,
    onWatchLater: () -> Unit,
    onFollow: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    chapters: List<VideoChapter> = emptyList(),
    positionMsProvider: () -> Long = { 0L },
    onSelectChapter: (VideoChapter) -> Unit = {},
    onOpenChapters: () -> Unit = {},
    barBackdrops: VideoBarBackdrops? = null,
    blurActive: Boolean = true,
    backEnabled: Boolean = true,
    commentRootId: Long = 0L,
    commentTargetId: Long = 0L,
    sponsorLabel: String? = null,
    promotedBadge: Boolean = false,
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
    val contentTop = firstRowHeight + DETAIL_BAR_HEIGHT + DETAIL_CONTENT_GAP
    // 停在顶部时栏下面没有东西会滚过去，这时候开着模糊只会把第一行的上沿糊出软边；
    // 等列表真的滚起来再启用。
    val barBlurActive = blurActive && rememberBarScrolled(detailPager.currentPage, infoListState, commentListState)
    val detailBarBounds = remember { TopBarBounds() }
    CompositionLocalProvider(LocalTopBarBounds provides detailBarBounds) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg),
    ) {
        HorizontalPager(
            state = detailPager,
            modifier = Modifier
                .fillMaxSize()
                // 图层常驻：LayerBackdrop 自己按消费者数量录制，进退场时拆装节点反而会让整页重录。
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
                        upOfficialType = state.upOfficialType,
                        upOfficialDesc = state.upOfficialDesc,
                        onLike = onLike,
                        onTriple = onTriple,
                        onDislike = onDislike,
                        showDislike = showDislike,
                        onCoin = onOpenCoin,
                        onFavorite = onOpenFavorite,
                        onWatchLater = onWatchLater,
                        onFollow = onFollow,
                        onShare = onShare,
                        onDownload = onDownload,
                        downloaded = downloaded,
                        listState = infoListState,
                        topInset = contentTop,
                        bottomInset = navBarBottom,
                        chapters = chapters,
                        positionMsProvider = positionMsProvider,
                        onSelectChapter = onSelectChapter,
                        onOpenChapters = onOpenChapters,
                        onTagClick = { tag ->
                            val keyword = tag.trim()
                            if (keyword.isNotEmpty()) nav.push(Route.Search(keyword))
                        },
                        sponsorLabel = sponsorLabel,
                        promotedBadge = promotedBadge,
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
                                topGap = 0.dp,
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
                .fillMaxWidth()
                .reportTopBarBottom(detailBarBounds),
            containerColor = pageBg,
            backdrop = if (BlurEffectsEnabled && barBlurActive) detailLayerBackdrop else null,
            materialBackdrop = if (BlurEffectsEnabled && barBlurActive) barMaterialBackdrop else null,
            // 模糊与渐隐都收在栏的下沿，不往下多铺一截去糊正文第一行。
            extraHeight = 0.dp,
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
                backdrop = if (BlurEffectsEnabled && barBlurActive) barMaterialBackdrop ?: detailLayerBackdrop else null,
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
}
