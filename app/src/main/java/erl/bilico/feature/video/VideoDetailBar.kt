package erl.bilico.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.R
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 播放页标题栏下方的一条：左边「简介 / 评论」分段，右边弹幕输入胶囊。
 * 胶囊就是 COUI 的搜索框，去掉材质描边与底部焦散光；点进去后分段收起、胶囊铺满整行，
 * 右端按搜索栏的取消按钮规格滑出「发送」。
 */
@Composable
internal fun VideoDetailBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    composing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onComposingChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = COUITheme.colorScheme
    val fieldColors = SearchBarDefaults.searchBarColors(backgroundColor = scheme.surfaceContainer)
    // 收起态靠这条间距把胶囊从分段后面拉开，展开时归零，跟着分段一起收。
    val gap by animateDpAsState(
        targetValue = if (composing) 0.dp else 8.dp,
        animationSpec = tween(DETAIL_RESIZE_MILLIS, easing = MoveEase),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = !composing,
            enter = expandHorizontally(tween(DETAIL_RESIZE_MILLIS, easing = MoveEase)) +
                fadeIn(tween(DETAIL_RESIZE_MILLIS, easing = MoveEase)),
            exit = shrinkHorizontally(tween(DETAIL_RESIZE_MILLIS, easing = MoveEase)) +
                fadeOut(tween(DETAIL_RESIZE_MILLIS, easing = MoveEase)),
        ) {
            DetailTabs(selected = selected, onSelect = onSelect)
        }

        SearchBar(
            inputField = {
                InputField(
                    query = draft,
                    onQueryChange = onDraftChange,
                    onSearch = {
                        keyboard?.hide()
                        onSend()
                    },
                    expanded = composing,
                    onExpandedChange = onComposingChange,
                    label = "发个友善的弹幕",
                    colors = fieldColors,
                    stroke = null,
                    glowColor = null,
                    leadingIcon = { Spacer(Modifier.width(14.dp)) },
                    trailingIcon = {
                        DanmakuToggle(enabled = danmakuEnabled, onToggle = onToggleDanmaku)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            onExpandedChange = onComposingChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = gap),
            insideMargin = DpSize(0.dp, 2.dp),
            expanded = composing,
            outsideEndAction = {
                SearchBarActionButton(
                    text = "发送",
                    onClick = {
                        keyboard?.hide()
                        onSend()
                    },
                    enabled = draft.isNotBlank(),
                    containerColor = scheme.surfaceContainer,
                    glowColor = null,
                )
            },
        ) { }
    }
}

@Composable
private fun DetailTabs(selected: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium)
    // 行内分段不吃满整行：按标签实测宽度定死，右边才腾得给弹幕胶囊。
    // 用标准 40dp 那一档，和右边 40dp 的搜索胶囊同高，不会一大一小。
    val tabsWidth = remember(density, labelStyle) {
        with(density) {
            val segmentPadding = 12.dp.toPx()
            val natural = DETAIL_TABS.maxOf {
                measurer.measure(it, labelStyle, maxLines = 1).size.width + segmentPadding * 2f
            }.coerceAtLeast(TabRowDefaults.TabRowWithContourMinWidth.toPx())
            (natural * DETAIL_TABS.size).toDp() + TabRowDefaults.TabRowWithContourPadding * 2
        }
    }

    // 组件内部第一步就 fillMaxWidth，直接给 width 会被它盖掉；套一层定宽的盒子才收得住。
    Box(Modifier.width(tabsWidth)) {
        TabRowWithContour(
            tabs = DETAIL_TABS,
            selectedTabIndex = selected,
            onTabSelected = onSelect,
            colors = TabRowDefaults.tabRowColors(
                contentColor = COUITheme.colorScheme.onSurfaceSecondary,
                selectedContentColor = COUITheme.colorScheme.onSurface,
            ),
            height = TabRowDefaults.TabRowWithContourHeight,
            cornerRadius = TabRowDefaults.TabRowWithContourCornerRadius,
            contourPadding = TabRowDefaults.TabRowWithContourPadding,
        )
    }
}

/** 胶囊右端那个半圆里的弹幕开关，图标取自 PiliPlus 的 dm_on / dm_off 字形。 */
@Composable
private fun DanmakuToggle(enabled: Boolean, onToggle: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .width(1.dp)
                .height(14.dp)
                .background(scheme.dividerLine),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .semantics {
                    contentDescription = if (enabled) "关闭弹幕" else "开启弹幕"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (enabled) "\uE803" else "\uE802",
                fontFamily = DanmakuIconFamily,
                fontSize = 18.sp,
                lineHeight = 18.sp,
                color = if (enabled) scheme.primary else scheme.onSurfaceSecondary,
            )
        }
    }
}

private val DanmakuIconFamily = FontFamily(Font(R.font.piliplus_custom_icons))

private val DETAIL_TABS = listOf("简介", "评论")

/** COUI 搜索栏背景变形的时长，让分段收起与胶囊铺开同一条节奏。 */
private const val DETAIL_RESIZE_MILLIS = 450

/** COUI 的 `COUIMoveEaseInterpolator`：cubic-bezier(0.3, 0, 0.1, 1)。 */
private val MoveEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
