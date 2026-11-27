package erl.bilico.feature.dynamic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import erl.bilico.player.LocalInPipMode
import erl.bilico.ui.ExpandEasing
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.components.compactNavBarBottomPadding
import erl.bilico.ui.reportBottomBarTop
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Send
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 动态详情页右下角发送评论悬浮球：高度与悬浮底栏/融合球统一。 */
@Composable
internal fun DynamicCommentFab(
    nav: NavState,
    topBarBounds: TopBarBounds,
    onClick: () -> Unit,
) {
    val ballInteraction = remember { MutableInteractionSource() }
    val ballPressed by ballInteraction.collectIsPressedAsState()
    val ballScale = remember { Animatable(1f) }
    LaunchedEffect(ballPressed) {
        ballScale.animateTo(
            targetValue = if (ballPressed) 0.94f else 1f,
            animationSpec = if (ballPressed) {
                spring(dampingRatio = 0.55f, stiffness = 1400f)
            } else {
                spring(dampingRatio = 0.34f, stiffness = 420f)
            },
        )
    }
    val orbVisible = !LocalInPipMode.current && (nav.minimizedEntry != null || nav.isMinimizingToOrb)
    PinnedBarDensity {
        val fabLift by animateDpAsState(
            targetValue = if (orbVisible) {
                BottomFloatingAccessoryNavigationBarDefaults.AccessorySize +
                    BottomFloatingAccessoryNavigationBarDefaults.SideSpacing
            } else {
                0.dp
            },
            animationSpec = tween(durationMillis = 280, easing = ExpandEasing),
            label = "dynamicCommentFabLift",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = compactNavBarBottomPadding()),
        ) {
            FloatingActionButton(
                onClick = onClick,
                interactionSource = ballInteraction,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = -fabLift)
                    .reportBottomBarTop(topBarBounds)
                    .padding(
                        end = BottomFloatingAccessoryNavigationBarDefaults.PaddingHorizontal,
                        bottom = (BottomFloatingNavigationBarDefaults.MinHeightWithoutPaddingVertical -
                            BottomFloatingAccessoryNavigationBarDefaults.AccessorySize) / 2,
                    )
                    .size(BottomFloatingAccessoryNavigationBarDefaults.AccessorySize)
                    .graphicsLayer {
                        scaleX = ballScale.value
                        scaleY = ballScale.value
                    },
            ) {
                Icon(
                    imageVector = COUIIcons.Send,
                    contentDescription = "发送评论",
                    modifier = Modifier.size(24.dp),
                    tint = COUITheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
