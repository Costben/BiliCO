package erl.bilico.feature.video

import erl.bilico.platform.PlatformBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import erl.bilico.player.BiliPlayer
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.settings.PlayerTopBarLayout
import erl.bilico.data.settings.PlayerTopBarMode
import erl.bilico.ui.BiliCoTheme
import erl.bilico.ui.BlurEffectsEnabled
import io.github.suqi8.coui.kmp.basic.GradientStrokeStyle
import io.github.suqi8.coui.kmp.basic.InnerShadowStyle
import io.github.suqi8.coui.kmp.basic.couiGlassSurface
import io.github.suqi8.coui.kmp.basic.materialShadowEdge
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.launch

/** COUI 居中弹窗进场曲线：pathInterpolator(0.3, 0, 0.1, 1)，250ms。 */
private val StageModalEnterEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/** COUI 居中弹窗退场曲线：pathInterpolator(0.3, 0, 1, 1)，150ms。 */
private val StageModalExitEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

private const val STAGE_MODAL_ENTER_MS = 250
private const val STAGE_MODAL_EXIT_MS = 150

/**
 * 播放器弹层宿主：竖屏时走标准 Dialog；全屏时直接嵌在横屏舞台里居中展示，
 * 避免 Dialog 自己再开一个窗口跟宿主抢方向，并复刻 COUI 居中弹窗的进退场动效。
 */
@Composable
internal fun PlayerModalHost(
    inlineInStage: Boolean,
    onDismiss: () -> Unit,
    canDismiss: () -> Boolean = { true },
    horizontalPadding: Dp = 24.dp,
    cornerRadius: Dp = 28.dp,
    maxWidth: Dp = 420.dp,
    content: @Composable () -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentCanDismiss by rememberUpdatedState(canDismiss)
    if (!inlineInStage) {
        val colors = COUITheme.colorScheme
        val textStyles = COUITheme.textStyles
        val dimens = COUITheme.dimens
        var show by remember { mutableStateOf(false) }
        var settled by remember { mutableStateOf(false) }
        val requestDismiss: () -> Unit = remember {
            {
                if (currentCanDismiss()) show = false
            }
        }
        LaunchedEffect(Unit) { show = true }
        OverlayDialog(
            show = show,
            outsideMargin = DpSize(16.dp, 8.dp),
            maxWidth = maxWidth + 32.dp,
            cornerRadius = cornerRadius,
            onDismissRequest = requestDismiss,
            onDismissFinished = {
                if (!settled) {
                    settled = true
                    currentOnDismiss()
                }
            },
        ) {
            COUITheme(colors = colors, textStyles = textStyles, dimens = dimens) {
                CompositionLocalProvider(LocalDismissState provides requestDismiss) {
                    content()
                }
            }
        }
    } else {
        val progress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
        var exiting by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val requestDismiss: () -> Unit = remember {
            {
                if (!exiting && currentCanDismiss()) {
                    exiting = true
                    scope.launch {
                        progress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(STAGE_MODAL_EXIT_MS, easing = StageModalExitEasing),
                        )
                        currentOnDismiss()
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(STAGE_MODAL_ENTER_MS, easing = StageModalEnterEasing),
            )
        }
        PlatformBackHandler(enabled = !exiting, onBack = requestDismiss)
        BiliCoTheme(darkMode = 2) {
            val scheme = COUITheme.colorScheme
            val dimColor = scheme.windowDimming
            val dialogShape = remember(cornerRadius) {
                SquircleShape(
                    cornerRadius = cornerRadius,
                    weight = DialogDefaults.CornerWeight,
                )
            }
            val panelBg = DialogDefaults.backgroundColor()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(dimColor.copy(alpha = dimColor.alpha * progress.value))
                    }
                    .then(
                        if (exiting) {
                            Modifier
                        } else {
                            Modifier.pointerInput(requestDismiss) {
                                detectTapGestures { requestDismiss() }
                            }
                        },
                    )
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val modalScaleBlock: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
                    val p = progress.value
                    val scale = if (exiting) 1f else 0.8f + 0.2f * p
                    scaleX = scale
                    scaleY = scale
                }
                Box(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = progress.value
                            val scale = if (exiting) 1f else 0.8f + 0.2f * p
                            scaleX = scale
                            scaleY = scale
                            alpha = p
                        }
                        .pointerInput(Unit) { detectTapGestures { } }
                        .couiGlassSurface(
                            backdrop = if (BlurEffectsEnabled) LocalBackdrop.current else null,
                            shape = dialogShape,
                            containerColor = panelBg,
                            fallback = Modifier.squircleSurface(
                                color = panelBg,
                                cornerRadius = cornerRadius,
                                weight = DialogDefaults.CornerWeight,
                            ),
                            blurRadius = DialogDefaults.blurRadius(),
                            blendColors = DialogDefaults.blurColors(),
                            enclosingLayerBlock = modalScaleBlock,
                        )
                        .materialShadowEdge(
                            stroke = DialogDefaults.stroke(),
                            cornerWeight = DialogDefaults.CornerWeight,
                            cornerRadius = cornerRadius,
                            edgeStyle = GradientStrokeStyle.EDGE_STYLE_2,
                            shadowStyle = InnerShadowStyle.SHADOW_STYLE_0,
                        )
                        .clip(dialogShape),
                ) {
                    CompositionLocalProvider(LocalDismissState provides requestDismiss) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerStageDialogs(
    player: BiliPlayer,
    source: PlaybackSource?,
    fullscreen: Boolean,
    portraitFullscreen: Boolean = false,
    statsVisible: Boolean,
    onDismissStats: () -> Unit,
    sponsorAssistVisible: Boolean,
    sponsorSegments: List<SponsorSegment>,
    onJumpSponsorSegment: ((SponsorSegment) -> Unit)?,
    onSubmitSponsor: (() -> Unit)?,
    onDismissSponsorAssist: () -> Unit,
    topBarSettingsVisible: Boolean,
    topBarLayoutFullscreen: PlayerTopBarLayout,
    topBarLayoutPortrait: PlayerTopBarLayout,
    onToggleTopBarItem: ((PlayerTopBarMode, String, Boolean) -> Unit)?,
    onMoveTopBarItem: ((PlayerTopBarMode, Int, Int) -> Unit)?,
    onDismissTopBarSettings: () -> Unit,
) {
    if (statsVisible) {
        PlayerStatsDialog(
            player = player,
            source = source,
            inlineInStage = fullscreen,
            onDismiss = onDismissStats,
        )
    }

    if (sponsorAssistVisible) {
        SponsorAssistDialog(
            segments = sponsorSegments,
            inlineInStage = fullscreen,
            onJump = { segment ->
                onDismissSponsorAssist()
                onJumpSponsorSegment?.invoke(segment)
            },
            onSubmit = onSubmitSponsor?.let { submit ->
                {
                    onDismissSponsorAssist()
                    submit()
                }
            },
            onDismiss = onDismissSponsorAssist,
        )
    }

    if (topBarSettingsVisible && onToggleTopBarItem != null) {
        PlayerTopBarSettingsDialog(
            fullscreen = topBarLayoutFullscreen,
            portrait = topBarLayoutPortrait,
            onToggle = onToggleTopBarItem,
            onMove = onMoveTopBarItem ?: { _, _, _ -> },
            onDismiss = onDismissTopBarSettings,
            inlineInStage = fullscreen,
            portraitFullscreen = portraitFullscreen,
        )
    }
}
