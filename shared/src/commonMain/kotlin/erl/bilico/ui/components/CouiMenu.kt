package erl.bilico.ui.components

import erl.bilico.platform.PlatformBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import erl.bilico.ui.BiliCoTheme
import erl.bilico.ui.BlurEffectsEnabled
import io.github.suqi8.coui.kmp.anim.BezierGravityPathMath
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupContent
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PopupLayoutPosition
import io.github.suqi8.coui.kmp.basic.PopupListDivider
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 菜单里的一行：文案加要做的事；[selected] 为 true 时带选中标记；[icon] 可选左侧图标；
 * [summary] 是标题下方的辅助说明；[hint] 是标题右侧的附加内容（如开关、标签）；
 * [keepOpen] 为 true 时点完不收起菜单；[children] 非空时点击展开 COUI 二级子菜单；
 * [group] 不同的相邻分组之间自动插入 COUI 分割线。
 */
internal class CouiMenuItem(
    val label: String,
    val onClick: () -> Unit = {},
    val selected: Boolean = false,
    val icon: (@Composable (Modifier) -> Unit)? = null,
    val hint: (@Composable () -> Unit)? = null,
    val summary: String? = null,
    val keepOpen: Boolean = false,
    val children: List<CouiMenuItem>? = null,
    val group: Int = 0,
)

private fun CouiMenuItem.toDropdownItem(): DropdownItem = DropdownItem(
    text = label,
    selected = selected,
    onClick = onClick,
    icon = icon,
    summary = summary,
    children = children?.map { it.toDropdownItem() },
    hint = hint,
)

/** 按 [CouiMenuItem.group] 切分为连续分组，供 COUI 级联弹层自动在组间绘制分割线。 */
private fun buildGroupedEntries(items: List<CouiMenuItem>): List<DropdownEntry> {
    if (items.isEmpty()) return emptyList()
    val result = mutableListOf<DropdownEntry>()
    var currentGroup = items.first().group
    val bucket = mutableListOf<DropdownItem>()
    for (item in items) {
        if (item.group != currentGroup && bucket.isNotEmpty()) {
            result += DropdownEntry(items = bucket.toList())
            bucket.clear()
            currentGroup = item.group
        }
        bucket += item.toDropdownItem()
    }
    if (bucket.isNotEmpty()) {
        result += DropdownEntry(items = bucket.toList())
    }
    return result
}

@Composable
private fun rememberSelectedScrollModifier(
    selectedIndex: Int,
    trigger: Any?,
): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var selectedSize by remember(selectedIndex) { mutableStateOf(IntSize.Zero) }
    var selectedCoords by remember(selectedIndex) { mutableStateOf<LayoutCoordinates?>(null) }
    var scrolledForOpen by remember(selectedIndex, trigger) { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(trigger, selectedIndex, selectedSize.height) {
        val active = trigger != null && trigger != false
        if (active && selectedIndex > 0 && !scrolledForOpen && selectedSize.height > 0) {
            val padPx = with(density) { 72.dp.toPx() }
            val target = Rect(
                left = 0f,
                top = -padPx,
                right = selectedSize.width.toFloat().coerceAtLeast(1f),
                bottom = selectedSize.height.toFloat() + padPx,
            )
            // OverlayListPopup 首帧透明度 < 0.1 时先挂 frozenVerticalScroll，等切到 verticalScroll 再滚入可视区。
            for (attempt in 0 until 5) {
                delay(if (attempt == 0) 90L else 60L)
                withFrameNanos { }
                requester.bringIntoView(target)
                withFrameNanos { }
                val coords = selectedCoords
                if (coords != null && coords.isAttached && coords.boundsInWindow().height >= selectedSize.height * 0.5f) {
                    scrolledForOpen = true
                    break
                }
            }
        }
    }
    return Modifier
        .onSizeChanged { selectedSize = it }
        .onGloballyPositioned { selectedCoords = it }
        .bringIntoViewRequester(requester)
}

/**
 * COUI 那套弹层菜单：面板半透明，后面是背景模糊，边缘有一圈高光和内阴影，
 * 打开时按重力路径从锚点弹出来，收起时缩回锚点。
 * 包含 [CouiMenuItem.children] 时自动使用 COUI 原生 [OverlayCascadingListPopup] 展开二级菜单。
 *
 * 必须和锚点（那个三点按钮）放在同一个 [androidx.compose.foundation.layout.Box] 里做兄弟节点，
 * 菜单是按这个 Box 的位置定位的。
 */
@Composable
private fun MenuPopupBody(
    items: List<CouiMenuItem>,
    groupKey: String,
    headerContent: (@Composable () -> Unit)?,
    scrollTrigger: Boolean = false,
    onItemSelected: (CouiMenuItem) -> Unit,
) {
    val selectedIndex = remember(items) { items.indexOfFirst { it.selected } }
    val selectedModifier = rememberSelectedScrollModifier(
        selectedIndex = selectedIndex,
        trigger = scrollTrigger,
    )
    val content: @Composable () -> Unit = {
        ListPopupColumn {
            items.forEachIndexed { index, item ->
                if (index > 0 && item.group != items[index - 1].group) {
                    PopupListDivider()
                }
                val hasSubmenu = !item.children.isNullOrEmpty()
                DropdownImpl(
                    item = item.toDropdownItem(),
                    optionSize = items.size,
                    isSelected = item.selected,
                    index = index,
                    modifier = if (index == selectedIndex) selectedModifier else Modifier,
                    isFirst = headerContent == null && index == 0,
                    isLast = index == items.lastIndex,
                    groupKey = groupKey,
                    hasSubmenu = hasSubmenu,
                    role = Role.Button,
                    onSelectedIndexChange = { onItemSelected(item) },
                )
            }
        }
    }
    if (headerContent == null) {
        content()
    } else {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            headerContent()
            PopupListDivider()
            Box(
                modifier = Modifier.fillMaxWidth(),
                propagateMinConstraints = true,
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun CouiPopupMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    items: List<CouiMenuItem>,
    preciseClickState: PreciseClickState? = null,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    maxHeight: Dp? = null,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val hasSubmenus = remember(items) { items.any { !it.children.isNullOrEmpty() } }
    if (hasSubmenus) {
        val entries = remember(items) { buildGroupedEntries(items) }
        OverlayCascadingListPopup(
            show = show,
            entries = entries,
            onDismissRequest = onDismiss,
            alignment = PopupPositionProvider.Align.End,
            maxHeight = maxHeight,
        )
        return
    }
    OverlayListPopup(
        show = show,
        alignment = alignment,
        onDismissRequest = onDismiss,
        maxHeight = maxHeight,
        preciseClickState = preciseClickState,
    ) {
        val dismiss = LocalDismissState.current
        MenuPopupBody(
            items = items,
            groupKey = "menu",
            headerContent = headerContent,
            scrollTrigger = show,
            onItemSelected = { item ->
                item.onClick()
                if (!item.keepOpen) dismiss?.invoke()
            },
        )
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
    alignment: Alignment = Alignment.TopEnd,
    bottomMargin: Dp = 0.dp,
    maxHeight: Dp = 350.dp,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val fractionProgress = remember { Animatable(0f) }
    val alphaProgress = remember { Animatable(0f) }
    val pathProgress = remember { Animatable(0f) }
    val subFraction = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    var popupContentSize by remember { mutableStateOf(IntSize.Zero) }
    var subContentSize by remember { mutableStateOf(IntSize.Zero) }
    var expandedSubmenuIndex by remember { mutableStateOf<Int?>(null) }
    var displayedSubmenuIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(show) {
        if (show) {
            mounted = true
            launch { pathProgress.animateTo(1f, ListPopupDefaults.PathEnterAnimationSpec) }
            launch { fractionProgress.animateTo(1f, ListPopupDefaults.FractionAnimationSpec) }
            launch { alphaProgress.animateTo(1f, ListPopupDefaults.AlphaEnterAnimationSpec) }
        } else if (mounted) {
            expandedSubmenuIndex = null
            launch { pathProgress.animateTo(0f, ListPopupDefaults.PathExitAnimationSpec) }
            launch { fractionProgress.animateTo(0f, ListPopupDefaults.FractionExitAnimationSpec) }
            alphaProgress.animateTo(0f, ListPopupDefaults.AlphaExitAnimationSpec)
            pathProgress.snapTo(0f)
            fractionProgress.snapTo(0f)
            alphaProgress.snapTo(0f)
            subFraction.snapTo(0f)
            displayedSubmenuIndex = null
            mounted = false
        }
    }

    LaunchedEffect(expandedSubmenuIndex) {
        if (expandedSubmenuIndex != null) {
            displayedSubmenuIndex = expandedSubmenuIndex
            subFraction.animateTo(1f, ListPopupDefaults.FractionAnimationSpec)
        } else if (displayedSubmenuIndex != null) {
            subFraction.animateTo(0f, ListPopupDefaults.FractionExitAnimationSpec)
            displayedSubmenuIndex = null
        }
    }

    if (!show && !mounted) return

    PlatformBackHandler(enabled = show) {
        if (expandedSubmenuIndex != null) {
            expandedSubmenuIndex = null
        } else {
            onDismiss()
        }
    }

    val showAbove = alignment == Alignment.BottomEnd || alignment == Alignment.BottomStart
    val motion = ListPopupDefaults.GravityMotion
    val gravityPath = remember(popupContentSize, showAbove) {
        val start = when {
            popupContentSize == IntSize.Zero -> Offset(0f, if (showAbove) 48f else -48f)
            showAbove -> Offset(popupContentSize.width * 0.25f, popupContentSize.height * 0.45f)
            else -> Offset(popupContentSize.width * 0.35f, -popupContentSize.height * 0.5f)
        }
        BezierGravityPathMath.controlPoints(
            start = start,
            end = Offset.Zero,
            config = motion.gravityConfig,
            p1T = motion.p1T,
            p2T = motion.p2T,
        )
    }
    val subGravityPath = remember(subContentSize) {
        val start = if (subContentSize == IntSize.Zero) {
            Offset(0f, -32f)
        } else {
            Offset(subContentSize.width * 0.2f, -subContentSize.height * 0.3f)
        }
        BezierGravityPathMath.controlPoints(
            start = start,
            end = Offset.Zero,
            config = motion.gravityConfig,
            p1T = motion.p1T,
            p2T = motion.p2T,
        )
    }

    val primarySubProgress = subFraction.value
    val activeTrigger = displayedSubmenuIndex?.let { items.getOrNull(it) }
    val activeChildren = activeTrigger?.children.orEmpty()

    BiliCoTheme(darkMode = 2) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (show) {
                        Modifier.pointerInput(onDismiss) {
                            detectTapGestures(
                                onTap = {
                                    if (expandedSubmenuIndex != null) {
                                        expandedSubmenuIndex = null
                                    } else {
                                        onDismiss()
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(
                        top = if (showAbove) 48.dp else topMargin,
                        bottom = bottomMargin,
                        end = endMargin,
                    ),
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        val s = lerp(1f, 0.9f, primarySubProgress)
                        scaleX = s
                        scaleY = s
                        alpha = lerp(1f, 0.45f, primarySubProgress)
                    },
                ) {
                    ListPopupContent(
                        popupContentSize = popupContentSize,
                        onPopupContentSizeChange = { popupContentSize = it },
                        fractionProgress = { fractionProgress.value },
                        alphaProgress = { alphaProgress.value },
                        pathProgress = { pathProgress.value },
                        gravityPath = gravityPath,
                        popupLayoutPosition = PopupLayoutPosition(
                            showBelow = !showAbove,
                            showAbove = showAbove,
                            isRightAligned = true,
                        ),
                        localTransformOrigin = if (showAbove) TransformOrigin(0.88f, 1f) else TransformOrigin(0.88f, 0f),
                        enableGlass = BlurEffectsEnabled,
                        interactionEnabled = show && expandedSubmenuIndex == null,
                        modifier = Modifier.heightIn(max = maxHeight),
                        content = {
                            CompositionLocalProvider(LocalDismissState provides onDismiss) {
                                MenuPopupBody(
                                    items = items,
                                    groupKey = "stage_menu",
                                    headerContent = headerContent,
                                    scrollTrigger = show,
                                    onItemSelected = { item ->
                                        val idx = items.indexOf(item)
                                        if (!item.children.isNullOrEmpty() && idx >= 0) {
                                            expandedSubmenuIndex = idx
                                        } else {
                                            item.onClick()
                                            if (!item.keepOpen) onDismiss()
                                        }
                                    },
                                )
                            }
                        },
                    )
                    if (expandedSubmenuIndex != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { expandedSubmenuIndex = null })
                                },
                        )
                    }
                }

                if (activeTrigger != null && activeChildren.isNotEmpty()) {
                    val subSelectedIndex = remember(activeChildren) { activeChildren.indexOfFirst { it.selected } }
                    val subSelectedModifier = rememberSelectedScrollModifier(
                        selectedIndex = subSelectedIndex,
                        trigger = displayedSubmenuIndex,
                    )
                    ListPopupContent(
                        popupContentSize = subContentSize,
                        onPopupContentSizeChange = { subContentSize = it },
                        fractionProgress = { subFraction.value * fractionProgress.value },
                        alphaProgress = { subFraction.value * alphaProgress.value },
                        pathProgress = { subFraction.value },
                        gravityPath = subGravityPath,
                        popupLayoutPosition = PopupLayoutPosition(
                            showBelow = true,
                            showAbove = false,
                            isRightAligned = true,
                        ),
                        localTransformOrigin = TransformOrigin(0.88f, 0.25f),
                        enableGlass = BlurEffectsEnabled,
                        interactionEnabled = show && expandedSubmenuIndex != null,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .widthIn(min = 212.dp)
                            .heightIn(max = maxHeight),
                        content = {
                            ListPopupColumn {
                                DropdownImpl(
                                    item = DropdownItem(text = activeTrigger.label, summary = activeTrigger.summary),
                                    optionSize = activeChildren.size + 1,
                                    isSelected = false,
                                    index = 0,
                                    groupKey = "stage_submenu",
                                    hasSubmenu = true,
                                    isFirst = true,
                                    isLast = false,
                                    role = Role.Button,
                                    onSelectedIndexChange = { expandedSubmenuIndex = null },
                                )
                                PopupListDivider()
                                activeChildren.forEachIndexed { childIndex, child ->
                                    DropdownImpl(
                                        item = child.toDropdownItem(),
                                        optionSize = activeChildren.size + 1,
                                        isSelected = child.selected,
                                        index = childIndex + 1,
                                        modifier = if (childIndex == subSelectedIndex) subSelectedModifier else Modifier,
                                        groupKey = "stage_submenu",
                                        isFirst = false,
                                        isLast = childIndex == activeChildren.lastIndex,
                                        role = Role.RadioButton,
                                        onSelectedIndexChange = {
                                            child.onClick()
                                            if (!child.keepOpen) onDismiss()
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
