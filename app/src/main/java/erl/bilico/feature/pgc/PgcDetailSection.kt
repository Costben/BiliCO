package erl.bilico.feature.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.CommentTab
import erl.bilico.feature.comment.ReplyDetailOverlay
import erl.bilico.feature.comment.ReplyThreadController
import erl.bilico.feature.video.CollapsedBarHeight
import erl.bilico.feature.video.DETAIL_BAR_HEIGHT
import erl.bilico.feature.video.DETAIL_CONTENT_GAP
import erl.bilico.feature.video.DetailBarGlassSurface
import erl.bilico.feature.video.VideoBarBackdrops
import erl.bilico.feature.video.VideoDetailBar
import erl.bilico.feature.video.rememberBarScrolled
import erl.bilico.feature.video.rememberStableNavBarBottomDp
import erl.bilico.feature.video.rememberStableStatusBarHeightPx
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalTopBarBounds
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.LocalCommentSeek
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.reportTopBarBottom
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop

/**
 * 番剧播放页下半部分的「简介 / 评论」横向切换区域及顶部悬浮毛玻璃切换栏，
 * 结构、透明渐变材质与发弹幕交互与普通视频详情页对齐。
 */
@Composable
internal fun PgcDetailSection(
    container: AppContainer,
    nav: NavState,
    state: PgcUiState,
    aid: Long,
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
    onToggleFollow: () -> Unit,
    onRetry: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSelectSeason: (Long) -> Unit,
    onDownload: (() -> Unit)? = null,
    onSeekCommentMs: (Long) -> Unit,
    barBackdrops: VideoBarBackdrops? = null,
    blurActive: Boolean = true,
    backEnabled: Boolean = true,
) {
    val navBarBottom = rememberStableNavBarBottomDp()
    val fallbackBackdrop = rememberLayerBackdrop()
    val detailLayerBackdrop = barBackdrops?.detail ?: fallbackBackdrop
    val barMaterialBackdrop = barBackdrops?.barMaterial
    val pageBg = pageContainerColor()
    val statusBarDp = with(LocalDensity.current) { rememberStableStatusBarHeightPx().toDp() }
    val firstRowHeight = statusBarDp + CollapsedBarHeight
    val contentTop = firstRowHeight + DETAIL_BAR_HEIGHT + DETAIL_CONTENT_GAP
    // 与普通视频详情页一致：停在顶部时不启用毛玻璃。
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
                .layerBackdropIf(BlurEffectsEnabled, detailLayerBackdrop)
                .background(pageBg),
            beyondViewportPageCount = 0,
            key = { PGC_DETAIL_PAGES[it] },
        ) { page ->
            when (page) {
                0 -> PgcInfoTab(
                    detail = state.detail,
                    isLoading = state.isLoading,
                    error = state.error,
                    selectedEpIndex = state.selectedEpIndex,
                    followed = state.followed,
                    onToggleFollow = onToggleFollow,
                    onRetry = onRetry,
                    onSelectEpisode = onSelectEpisode,
                    onSelectSeason = onSelectSeason,
                    onDownload = onDownload,
                    listState = infoListState,
                    topInset = contentTop,
                    bottomInset = navBarBottom,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> {
                    if (aid > 0L) {
                        CompositionLocalProvider(LocalCommentSeek provides onSeekCommentMs) {
                            CommentTab(
                                container = container,
                                oid = aid,
                                onOpenUser = { mid -> if (mid > 0L) nav.push(Route.Space(mid)) },
                                listState = commentListState,
                                topInset = contentTop,
                                topGap = 0.dp,
                                bottomInset = navBarBottom,
                                replyThreadController = replyThread,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        EmptyState(
                            text = "暂无评论",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = contentTop, bottom = navBarBottom),
                        )
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
            // 与视频详情页一致：模糊与渐隐都收在栏的下沿。
            extraHeight = 0.dp,
            topInset = firstRowHeight,
        ) {
            VideoDetailBar(
                selected = detailPager.currentPage,
                onSelect = onSelectPage,
                commentCount = 0L,
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

        if (aid > 0L) {
            CompositionLocalProvider(LocalCommentSeek provides onSeekCommentMs) {
                ReplyDetailOverlay(
                    controller = replyThread,
                    container = container,
                    oid = aid,
                    listState = replyListState,
                    topInset = firstRowHeight,
                    bottomInset = navBarBottom,
                    backEnabled = backEnabled,
                    onOpenUser = { mid -> if (mid > 0L) nav.push(Route.Space(mid)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    }
}

private val PGC_DETAIL_PAGES = listOf("info", "comment")
