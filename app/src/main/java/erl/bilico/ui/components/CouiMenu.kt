package erl.bilico.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import erl.bilico.ui.BiliCoTheme
import io.github.suqi8.coui.kmp.anim.BezierGravityPathMath
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupContent
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PopupLayoutPosition
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.launch

/** 菜单里的一行：文案加要做的事。 */
internal class CouiMenuItem(val label: String, val onClick: () -> Unit)

/**
 * COUI 那套弹层菜单：面板半透明，后面是背景模糊，边缘有一圈高光和内阴影，
 * 打开时按重力路径从锚点弹出来，收起时缩回锚点。
 *
 * 必须和锚点（那个三点按钮）放在同一个 [androidx.compose.foundation.layout.Box] 里做兄弟节点，
 * 菜单是按这个 Box 的位置定位的。
 */
@Composable
internal fun CouiPopupMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    items: List<CouiMenuItem>,
) {
    OverlayListPopup(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        val dismiss = LocalDismissState.current
        ListPopupColumn {
            items.forEachIndexed { index, item ->
                DropdownImpl(
                    item = DropdownItem(text = item.label),
                    optionSize = items.size,
                    isSelected = false,
                    index = index,
                    groupKey = "menu",
                    role = Role.Button,
                    onSelectedIndexChange = {
                        item.onClick()
                        dismiss?.invoke()
                    },
                )
            }
        }
    }
}

/**
 * 横屏全屏旋转舞台内的 COUI 17 原生弹层菜单：
 * 直接挂载在已旋转 ±90° 的播放器舞台内部（保证菜单朝向与全屏横屏一致），
 * 复用 COUI 17 的 [ListPopupContent]、[ListPopupColumn]、[DropdownImpl] 以及
 * 三弹簧重力轨迹（Path + Fraction + Alpha）与 28dp 超椭圆 + PopupShadow。
 */
@Composable
internal fun CouiStagePopupMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    items: List<CouiMenuItem>,
    topMargin: Dp = 54.dp,
    endMargin: Dp = 24.dp,
) {
    val fractionProgress = remember { Animatable(0f) }
    val alphaProgress = remember { Animatable(0f) }
    val pathProgress = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    var popupContentSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(show) {
        if (show) {
            mounted = true
            launch { pathProgress.animateTo(1f, ListPopupDefaults.PathEnterAnimationSpec) }
            launch { fractionProgress.animateTo(1f, ListPopupDefaults.FractionAnimationSpec) }
            launch { alphaProgress.animateTo(1f, ListPopupDefaults.AlphaEnterAnimationSpec) }
        } else if (mounted) {
            launch { pathProgress.animateTo(0f, ListPopupDefaults.PathExitAnimationSpec) }
            launch { fractionProgress.animateTo(0f, ListPopupDefaults.FractionExitAnimationSpec) }
            alphaProgress.animateTo(0f, ListPopupDefaults.AlphaExitAnimationSpec)
            pathProgress.snapTo(0f)
            fractionProgress.snapTo(0f)
            alphaProgress.snapTo(0f)
            mounted = false
        }
    }

    if (!show && !mounted) return

    BackHandler(enabled = show) { onDismiss() }

    val motion = ListPopupDefaults.GravityMotion
    val gravityPath = remember(popupContentSize) {
        val start = if (popupContentSize == IntSize.Zero) {
            Offset(0f, -48f)
        } else {
            Offset(popupContentSize.width * 0.35f, -popupContentSize.height * 0.5f)
        }
        BezierGravityPathMath.controlPoints(
            start = start,
            end = Offset.Zero,
            config = motion.gravityConfig,
            p1T = motion.p1T,
            p2T = motion.p2T,
        )
    }

    BiliCoTheme(darkMode = 2) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (show) {
                        Modifier.pointerInput(onDismiss) {
                            detectTapGestures(onTap = { onDismiss() })
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topMargin, end = endMargin),
            ) {
                ListPopupContent(
                    popupContentSize = popupContentSize,
                    onPopupContentSizeChange = { popupContentSize = it },
                    fractionProgress = { fractionProgress.value },
                    alphaProgress = { alphaProgress.value },
                    pathProgress = { pathProgress.value },
                    gravityPath = gravityPath,
                    popupLayoutPosition = PopupLayoutPosition(
                        showBelow = true,
                        showAbove = false,
                        isRightAligned = true,
                    ),
                    localTransformOrigin = TransformOrigin(0.88f, 0f),
                    enableGlass = false,
                    interactionEnabled = show,
                    content = {
                        CompositionLocalProvider(LocalDismissState provides onDismiss) {
                            ListPopupColumn {
                                items.forEachIndexed { index, item ->
                                    DropdownImpl(
                                        item = DropdownItem(text = item.label),
                                        optionSize = items.size,
                                        isSelected = false,
                                        index = index,
                                        groupKey = "stage_menu",
                                        role = Role.Button,
                                        onSelectedIndexChange = {
                                            onDismiss()
                                            item.onClick()
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
