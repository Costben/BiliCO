package erl.bilico.feature.download

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.BangumiDetail
import erl.bilico.data.model.BangumiEpisode
import erl.bilico.data.model.QualityOption
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoPart
import erl.bilico.data.repository.CdnOption
import erl.bilico.di.AppContainer
import erl.bilico.feature.video.PlayerModalHost
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState

@Composable
internal fun rememberVideoDownloadDialog(
    visible: Boolean,
    container: AppContainer,
    detail: VideoDetail?,
    selectedCid: Long,
    activeQuality: Int,
    qualities: List<QualityOption>,
    onEnqueued: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onDismiss: () -> Unit,
): (@Composable (Boolean) -> Unit)? = if (visible && detail != null) {
    { inlineInStage ->
        VideoDownloadDialog(
            container = container,
            detail = detail,
            initialCid = selectedCid,
            activeQuality = activeQuality,
            initialQualities = qualities,
            inlineInStage = inlineInStage,
            onEnqueued = onEnqueued,
            onOpenDownloads = onOpenDownloads,
            onDismiss = onDismiss,
        )
    }
} else {
    null
}

/** 从普通视频播放页打开离线缓存选择面板。 */
@Composable
internal fun VideoDownloadDialog(
    container: AppContainer,
    detail: VideoDetail,
    initialCid: Long,
    activeQuality: Int,
    initialQualities: List<QualityOption>,
    inlineInStage: Boolean = false,
    onEnqueued: (Int) -> Unit = {},
    onOpenDownloads: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val defaultQuality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val cdn by container.settings.cdnOption.collectAsStateWithLifecycle()
    var qualities by remember(detail.bvid, initialQualities) {
        mutableStateOf(mergeQualityOptions(initialQualities))
    }

    LaunchedEffect(detail.bvid, detail.cid) {
        if (initialQualities.isEmpty() && detail.cid > 0L) {
            container.video.playback(detail.bvid, detail.cid, defaultQuality, CdnOption.fromId(cdn))
                .onSuccess { source ->
                    if (source.qualities.isNotEmpty()) {
                        qualities = mergeQualityOptions(source.qualities)
                    }
                }
        }
    }

    val entries = remember(detail.pages, detail.cid, detail.durationSec, tasks) {
        buildVideoPickEntries(container, detail)
    }

    DownloadPickerBody(
        title = "离线缓存",
        subtitle = detail.title,
        qualities = qualities,
        initialQuality = activeQuality.takeIf { it > 0 } ?: defaultQuality,
        entries = entries,
        initialSelectedId = initialCid.takeIf { it > 0L } ?: detail.cid,
        inlineInStage = inlineInStage,
        onOpenDownloads = onOpenDownloads,
        onConfirm = { qn, qLabel, selectedIds ->
            val pages = detail.pages.ifEmpty {
                listOf(VideoPart(detail.cid, 1, detail.title, detail.durationSec.toLong(), detail.aspect))
            }
            val chosen = pages.filter { it.cid in selectedIds }
            val count = container.downloads.enqueueVideo(detail, chosen, qn, qLabel)
            onEnqueued(count)
        },
        onDismiss = onDismiss,
    )
}

/** 从番剧/影视播放页打开离线缓存选择面板。 */
@Composable
internal fun PgcDownloadDialog(
    container: AppContainer,
    detail: BangumiDetail,
    currentEpisode: BangumiEpisode?,
    activeQuality: Int,
    initialQualities: List<QualityOption>,
    inlineInStage: Boolean = false,
    onEnqueued: (Int) -> Unit = {},
    onOpenDownloads: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val defaultQuality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val qualities = remember(initialQualities) { mergeQualityOptions(initialQualities) }

    val entries = remember(detail.episodes, detail.seasonId, tasks) {
        detail.episodes.mapIndexed { idx, ep ->
            DownloadPickEntry(
                id = ep.epId,
                title = ep.title.ifBlank { "第 ${idx + 1} 话" },
                durationSec = ep.durationSec,
                status = container.downloads.statusForBangumi(detail.seasonId, ep.epId, ep.cid),
            )
        }
    }

    DownloadPickerBody(
        title = "离线缓存",
        subtitle = detail.title,
        qualities = qualities,
        initialQuality = activeQuality.takeIf { it > 0 } ?: defaultQuality,
        entries = entries,
        initialSelectedId = currentEpisode?.epId ?: detail.episodes.firstOrNull()?.epId ?: 0L,
        inlineInStage = inlineInStage,
        onOpenDownloads = onOpenDownloads,
        onConfirm = { qn, qLabel, selectedIds ->
            val chosen = detail.episodes.filter { it.epId in selectedIds && it.cid > 0L }
            val count = container.downloads.enqueueBangumi(detail, chosen, qn, qLabel)
            onEnqueued(count)
        },
        onDismiss = onDismiss,
    )
}

private sealed interface FeedDownloadPhase {
    data object Loading : FeedDownloadPhase
    data class Error(val message: String) : FeedDownloadPhase
    data class Ready(val detail: VideoDetail, val qualities: List<QualityOption>) : FeedDownloadPhase
}

/** 从推荐流 / 列表三点菜单快捷打开离线缓存：单弹窗原位平滑增高并淡入内容，不硬闪切换弹窗。 */
@Composable
internal fun FeedVideoDownloadDialog(
    container: AppContainer,
    bvid: String,
    onEnqueued: (Int) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val defaultQuality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val cdn by container.settings.cdnOption.collectAsStateWithLifecycle()
    var phase by remember(bvid) { mutableStateOf<FeedDownloadPhase>(FeedDownloadPhase.Loading) }
    var reloadTick by remember(bvid) { mutableIntStateOf(0) }

    LaunchedEffect(bvid, reloadTick) {
        phase = FeedDownloadPhase.Loading
        container.video.detail(bvid)
            .onSuccess { loaded ->
                val fetched = if (loaded.cid > 0L) {
                    container.video.playback(bvid, loaded.cid, defaultQuality, CdnOption.fromId(cdn))
                        .getOrNull()?.qualities.orEmpty()
                } else {
                    emptyList()
                }
                phase = FeedDownloadPhase.Ready(loaded, mergeQualityOptions(fetched))
            }
            .onFailure { err ->
                phase = FeedDownloadPhase.Error(err.message ?: "视频信息加载失败")
            }
    }

    val readyDetail = (phase as? FeedDownloadPhase.Ready)?.detail
    PlayerModalHost(
        inlineInStage = false,
        onDismiss = onDismiss,
        maxWidth = 440.dp,
    ) {
        val dismiss = LocalDismissState.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            DownloadPickerHeader(
                title = "离线缓存",
                subtitle = readyDetail?.title.orEmpty(),
                onOpenDownloads = null,
                onDismiss = dismiss,
            )
            AnimatedContent(
                targetState = phase,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(120, easing = FastOutSlowInEasing)))
                        .using(
                            SizeTransform(clip = true) { _, _ ->
                                tween(durationMillis = 280, easing = DownloadMorphEasing)
                            },
                        )
                },
                label = "feed_download_morph",
            ) { state ->
                when (state) {
                    FeedDownloadPhase.Loading -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "正在获取视频清晰度…",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        DialogButtonBar(
                            negative = DialogButtonBarAction(text = "关闭", onClick = dismiss),
                            positive = null,
                        )
                    }

                    is FeedDownloadPhase.Error -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(horizontal = 20.dp),
                            style = COUITheme.textStyles.body2,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        DialogButtonBar(
                            negative = DialogButtonBarAction(text = "关闭", onClick = dismiss),
                            positive = DialogButtonBarAction(
                                text = "重试",
                                recommended = true,
                                onClick = { reloadTick++ },
                            ),
                        )
                    }

                    is FeedDownloadPhase.Ready -> {
                        val detail = state.detail
                        val entries = remember(detail.pages, detail.cid, detail.durationSec, tasks) {
                            buildVideoPickEntries(container, detail)
                        }
                        DownloadPickerContent(
                            qualities = state.qualities,
                            initialQuality = defaultQuality,
                            entries = entries,
                            initialSelectedId = detail.cid,
                            inlineInStage = false,
                            onConfirm = { qn, qLabel, selectedIds ->
                                val pages = detail.pages.ifEmpty {
                                    listOf(VideoPart(detail.cid, 1, detail.title, detail.durationSec.toLong(), detail.aspect))
                                }
                                val chosen = pages.filter { it.cid in selectedIds }
                                onEnqueued(container.downloads.enqueueVideo(detail, chosen, qn, qLabel))
                            },
                            onDismiss = dismiss,
                        )
                    }
                }
            }
        }
    }
}
