package erl.bilico.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.settings.AutoNextPreferences
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.LoadingImage
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

/** 管理非分P或合集连播时下一个推荐视频的倒计时弹窗状态。 */
@Stable
internal class AutoNextRecommendController {
    var target by mutableStateOf<FeedVideo?>(null)
        private set
    var remainingSeconds by mutableIntStateOf(0)
        internal set
    var totalSeconds by mutableIntStateOf(AutoNextPreferences.DEFAULT_COUNTDOWN_SECONDS)
        private set

    fun prompt(video: FeedVideo, countdownSeconds: Int) {
        val clamped = countdownSeconds.coerceIn(
            AutoNextPreferences.MIN_COUNTDOWN_SECONDS,
            AutoNextPreferences.MAX_COUNTDOWN_SECONDS,
        )
        totalSeconds = clamped
        remainingSeconds = clamped
        target = video
    }

    fun cancel() {
        target = null
        remainingSeconds = 0
    }

    fun consumeTarget(): FeedVideo? {
        val current = target ?: return null
        cancel()
        return current
    }
}

@Composable
internal fun rememberAutoNextRecommendController(
    bvid: String,
    isTop: Boolean,
    inPipMode: Boolean,
    container: AppContainer,
    nav: NavState,
): Pair<AutoNextRecommendController, ((FeedVideo) -> Unit)?> {
    val controller = remember(bvid) { AutoNextRecommendController() }
    val popupEnabled by container.settings.autoNext.recommendPopup.collectAsStateWithLifecycle()
    val countdownSeconds by container.settings.autoNext.countdownSeconds.collectAsStateWithLifecycle()
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()

    LaunchedEffect(controller.target, isTop, inPipMode, playbackEndMode, bvid) {
        val next = controller.target ?: return@LaunchedEffect
        if (!isTop || playbackEndMode != PlaybackEndMode.AUTO_NEXT || next.bvid == bvid) {
            controller.cancel()
            return@LaunchedEffect
        }
        if (inPipMode) {
            controller.cancel()
            nav.replaceTop(Route.Video(next.bvid, 0L))
            return@LaunchedEffect
        }
        val total = controller.totalSeconds.coerceIn(
            AutoNextPreferences.MIN_COUNTDOWN_SECONDS,
            AutoNextPreferences.MAX_COUNTDOWN_SECONDS,
        )
        for (sec in total downTo 1) {
            controller.remainingSeconds = sec
            delay(1_000L)
        }
        controller.cancel()
        nav.replaceTop(Route.Video(next.bvid, 0L))
    }

    val promptHandler: ((FeedVideo) -> Unit)? = if (popupEnabled && isTop && !inPipMode) {
        { video -> controller.prompt(video, countdownSeconds) }
    } else {
        null
    }
    return controller to promptHandler
}

/** 竖屏详情页收成顶部横栏时，弹窗改挂到屏幕右下角。 */
@Composable
internal fun BoxScope.PortraitCollapsedAutoNextOverlay(
    controller: AutoNextRecommendController,
    nav: NavState,
) {
    AutoNextRecommendOverlay(
        video = controller.target,
        remainingSeconds = controller.remainingSeconds,
        totalSeconds = controller.totalSeconds,
        fullscreen = false,
        bottomPad = 16.dp,
        endPad = 16.dp,
        onPlayNow = { controller.consumeTarget()?.let { nav.replaceTop(Route.Video(it.bvid, 0L)) } },
        onDismiss = controller::cancel,
        modifier = Modifier.navigationBarsPadding(),
    )
}

/** 播放器右下角的「即将播放下一个推荐视频」倒计时弹窗。 */
@Composable
internal fun BoxScope.AutoNextRecommendOverlay(
    video: FeedVideo?,
    remainingSeconds: Int,
    totalSeconds: Int,
    fullscreen: Boolean,
    bottomPad: Dp,
    endPad: Dp,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedBottom by animateDpAsState(
        targetValue = bottomPad,
        animationSpec = tween(durationMillis = 200, easing = ColorOSMoveEase),
        label = "autoNextBottomPad",
    )
    AnimatedVisibility(
        visible = video != null,
        enter = fadeIn(tween(200, easing = ColorOSMoveEase)) +
            slideInVertically(tween(200, easing = ColorOSMoveEase)) { it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 },
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = endPad, bottom = animatedBottom),
    ) {
        if (video != null) {
            AutoNextRecommendCard(
                video = video,
                remainingSeconds = remainingSeconds.coerceAtLeast(1),
                totalSeconds = totalSeconds.coerceIn(
                    AutoNextPreferences.MIN_COUNTDOWN_SECONDS,
                    AutoNextPreferences.MAX_COUNTDOWN_SECONDS,
                ),
                fullscreen = fullscreen,
                onPlayNow = onPlayNow,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun AutoNextRecommendCard(
    video: FeedVideo,
    remainingSeconds: Int,
    totalSeconds: Int,
    fullscreen: Boolean,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val coverWidth = if (fullscreen) 88.dp else 72.dp
    val coverWidthPx = with(density) { coverWidth.roundToPx() }
    val coverHeightPx = coverWidthPx * 9 / 16
    val cardWidth = if (fullscreen) 288.dp else 236.dp
    val cardShape = SquircleShape(12.dp)
    val progress = remember(video.bvid, totalSeconds) { Animatable(1f) }
    LaunchedEffect(video.bvid, totalSeconds) {
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = totalSeconds * 1000, easing = LinearEasing),
        )
    }

    Column(
        modifier = Modifier
            .width(cardWidth)
            .clip(cardShape)
            .background(Color(0xD9141416))
            .border(0.5.dp, Color.White.copy(alpha = 0.16f), cardShape)
            .clickable(onClick = onPlayNow)
            .testTag("auto_next_recommend_popup"),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(coverWidth)
                    .aspectRatio(16f / 9f)
                    .clip(SquircleShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingImage(
                    url = Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx),
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0x80000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = COUIIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.White,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "即将播放 · ${remainingSeconds}s",
                    style = COUITheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                    color = COUITheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = video.title,
                    style = COUITheme.textStyles.footnote1.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Top)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = "取消连播" }
                    .testTag("auto_next_recommend_dismiss"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = COUIIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.72f),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = progress.value.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(COUITheme.colorScheme.primary),
            )
        }
    }
}
