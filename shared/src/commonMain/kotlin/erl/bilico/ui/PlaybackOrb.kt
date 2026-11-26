package erl.bilico.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.platformUseCompactNavBarInset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import erl.bilico.data.util.Formatters
import erl.bilico.player.PlaybackBridge
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryBall
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.NavigationPopupMenuDefaults
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.overlay.OverlayNavigationPopupMenu
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.sin

internal val PlaybackOrbCoverSize = 40.dp

@Composable
internal fun PlaybackOrbContent(
    artworkUrl: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val sizePx = with(LocalDensity.current) { PlaybackOrbCoverSize.roundToPx() }
    val model = remember(artworkUrl, sizePx) {
        Formatters.sizedImageUrl(artworkUrl, sizePx, sizePx)
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PlaybackOrbCoverSize)
                .clip(CircleShape)
                .background(COUITheme.colorScheme.surfaceContainerHigh)
                .border(0.5.dp, Color.White.copy(alpha = 0.24f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (model.isNotBlank()) {
                AsyncImage(
                    model = model,
                    contentDescription = "后台播放封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )
            if (isPlaying) {
                PlayingWaveIndicator(
                    playing = true,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Icon(
                    imageVector = COUIIcons.Demibold.Pause,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 0.6.dp)
                        .size(16.dp),
                    tint = Color.Black.copy(alpha = 0.32f),
                )
                Icon(
                    imageVector = COUIIcons.Demibold.Pause,
                    contentDescription = "已暂停",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(16.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * 居中叠加在封面图标上的跳动音浪：播放时随帧时钟起伏，暂停时平缓落回低柱。
 */
@Composable
internal fun PlayingWaveIndicator(
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    var phaseSec by remember { mutableFloatStateOf(0f) }
    val activity = remember { Animatable(if (playing) 1f else 0f) }

    LaunchedEffect(playing) {
        activity.animateTo(
            targetValue = if (playing) 1f else 0f,
            animationSpec = tween(durationMillis = 220),
        )
    }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) {
                    lastNanos = nanos
                    return@withFrameNanos
                }
                val elapsed = nanos - lastNanos
                // 音浪按 ~25fps 刷新即可，避免 120Hz 下每秒唤醒底栏三体融合毛玻璃 120 次。
                if (elapsed < 40_000_000L) return@withFrameNanos
                val dt = (elapsed / 1_000_000_000f).coerceIn(0f, 0.1f)
                phaseSec = (phaseSec + dt) % 1000f
                lastNanos = nanos
            }
        }
    }

    val barWidth = 2.6.dp
    val barGap = 2.2.dp
    val maxHeight = 14.dp
    val totalWidth = barWidth * 4 + barGap * 3

    Canvas(modifier = modifier.size(width = totalWidth, height = maxHeight)) {
        val barWidthPx = barWidth.toPx()
        val gapPx = barGap.toPx()
        val maxHPx = size.height
        val radius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
        val act = activity.value
        val t = phaseSec

        val freqs = floatArrayOf(7.2f, 9.4f, 6.6f, 8.5f)
        val offsets = floatArrayOf(0.0f, 1.35f, 2.65f, 4.10f)
        val restFractions = floatArrayOf(0.28f, 0.42f, 0.34f, 0.26f)

        for (i in 0 until 4) {
            val wave = 0.5f + 0.5f * sin(t * freqs[i] + offsets[i])
            val activeFrac = 0.28f + 0.72f * wave
            val frac = restFractions[i] + (activeFrac - restFractions[i]) * act
            val h = (maxHPx * frac).coerceAtLeast(barWidthPx)
            val x = i * (barWidthPx + gapPx)
            val y = (maxHPx - h) / 2f

            drawRoundRect(
                color = Color.Black.copy(alpha = 0.32f),
                topLeft = Offset(x, y + 0.6.dp.toPx()),
                size = Size(barWidthPx, h),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, h),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
internal fun PlaybackOrbMenu(
    expanded: Boolean,
    isPlaying: Boolean,
    onDismissRequest: () -> Unit,
    onTogglePlay: () -> Unit,
    onStopPlayback: () -> Unit,
) {
    val entries = remember(isPlaying) {
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = if (isPlaying) "暂停播放" else "继续播放",
                        icon = { mod ->
                            Icon(
                                imageVector = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                                contentDescription = null,
                                modifier = mod,
                            )
                        },
                        onClick = {
                            onDismissRequest()
                            onTogglePlay()
                        },
                    ),
                    DropdownItem(
                        text = "关闭后台播放",
                        icon = { mod ->
                            Icon(
                                imageVector = COUIIcons.Close,
                                contentDescription = null,
                                modifier = mod,
                            )
                        },
                        onClick = {
                            onDismissRequest()
                            onStopPlayback()
                        },
                    ),
                ),
            ),
        )
    }
    val positionProvider = remember {
        object : PopupPositionProvider {
            private val base = NavigationPopupMenuDefaults.PositionProvider
            override fun getMargins() = base.getMargins()
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowBounds: IntRect,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
                popupMargin: IntRect,
                alignment: PopupPositionProvider.Align,
            ): IntOffset {
                val minX = windowBounds.left + popupMargin.left
                val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(minX)
                val minY = windowBounds.top + popupMargin.top
                val maxY = (windowBounds.bottom - popupContentSize.height - popupMargin.bottom).coerceAtLeast(minY)
                return IntOffset(
                    x = (anchorBounds.right - popupContentSize.width).coerceIn(minX, maxX),
                    y = (anchorBounds.top - popupContentSize.height - popupMargin.bottom).coerceIn(minY, maxY),
                )
            }
        }
    }
    OverlayNavigationPopupMenu(
        expanded = expanded,
        entries = entries,
        onDismissRequest = onDismissRequest,
        alignment = PopupPositionProvider.Align.BottomEnd,
        popupPositionProvider = positionProvider,
    )
}

@Composable
internal fun SecondaryPlaybackOrbOverlay(
    nav: NavState,
    orbOrigin: ExpandOrigin,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val artworkUrl = PlaybackBridge.currentArtworkUrl
    val isPlaying = PlaybackBridge.currentIsPlaying
    var menuExpanded by remember { mutableStateOf(false) }

    PinnedBarDensity {
        val density = LocalDensity.current
        val navInsetModifier = if (platformUseCompactNavBarInset) {
            val bottomInset = if (WindowInsets.navigationBars.getBottom(density) > 0) {
                BottomFloatingNavigationBarDefaults.MenuToScreenNaviDistance
            } else {
                0.dp
            }
            Modifier.padding(bottom = bottomInset)
        } else {
            Modifier.navigationBarsPadding()
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(navInsetModifier),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BottomFloatingNavigationBarDefaults.MinHeightWithoutPaddingVertical)
                    .padding(horizontal = BottomFloatingAccessoryNavigationBarDefaults.PaddingHorizontal),
                contentAlignment = Alignment.CenterEnd,
            ) {
                BottomFloatingAccessoryBall(
                    onClick = { nav.restoreMinimized() },
                    contentDescription = "恢复播放页面",
                    modifier = Modifier.testTag("playback_fusion_orb"),
                    menu = {
                        PlaybackOrbMenu(
                            expanded = menuExpanded,
                            isPlaying = isPlaying,
                            onDismissRequest = { menuExpanded = false },
                            onTogglePlay = { PlaybackBridge.togglePlayPause(context) },
                            onStopPlayback = { nav.dismissMinimized(pausePlayback = true) },
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onLongClick = { menuExpanded = true },
                                onClick = { nav.restoreMinimized() },
                            )
                            .expandOrigin(orbOrigin),
                    ) {
                        PlaybackOrbContent(
                            artworkUrl = artworkUrl,
                            isPlaying = isPlaying,
                        )
                    }
                }
            }
        }
    }
}
