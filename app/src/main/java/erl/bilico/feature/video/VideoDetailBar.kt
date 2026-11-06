package erl.bilico.feature.video

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.R
import erl.bilico.ui.components.SegmentCapsule
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 播放页标题栏下方的一条：左边「简介 / 评论」分段，右边弹幕输入胶囊。
 *
 * 分段用全局那个跟手指走的分段胶囊，拖 pager 时指示块每帧贴着页面走；
 * 弹幕胶囊就是 COUI 的搜索框，去掉材质描边与底部焦散光。
 * 点进去后分段收窄淡出、胶囊铺满整行，右端按搜索栏取消按钮的规格滑出「发送」。
 */
@Composable
internal fun VideoDetailBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    pager: PagerState,
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
    // 分段收起的进度：一边收窄，一边在切口处渐隐，收完后不留一条硬边。
    val collapse by animateFloatAsState(
        targetValue = if (composing) 1f else 0f,
        animationSpec = tween(DETAIL_RESIZE_MILLIS, easing = MoveEase),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(DETAIL_TABS_WIDTH * (1f - collapse))
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(DETAIL_TABS_WIDTH)
                    .fadeTrailingEdge(fade = collapse, band = 24.dp),
            ) {
                SegmentCapsule(
                    labels = DETAIL_TABS,
                    selectedIndex = selected,
                    onSelect = onSelect,
                    segmentWidth = DETAIL_SEGMENT_WIDTH,
                    pager = pager,
                )
            }
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

/**
 * 把内容右端按 [fade] 的比例渐隐掉：0 时原样，1 时整块透明。
 * 渐隐带跟着 [fade] 一起由窄变宽，收窄过程中切口不会留下一条硬边。
 */
private fun Modifier.fadeTrailingEdge(fade: Float, band: Dp): Modifier =
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            if (fade <= 0f) {
                drawContent()
                return@drawWithContent
            }
            drawContent()
            val width = size.width
            if (width <= 0f) return@drawWithContent
            val cut = (width * (1f - fade)).coerceIn(0f, width)
            val bandPx = band.toPx() * fade
            val solidStop = ((cut - bandPx) / width).coerceIn(0f, 1f)
            val edgeStop = (cut / width).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    solidStop to Color.Black,
                    edgeStop to Color.Transparent,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
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

/** 每段的宽度；两段加 8dp 内边距就是整条分段胶囊的宽度。 */
private val DETAIL_SEGMENT_WIDTH = 56.dp

private val DETAIL_TABS_WIDTH = DETAIL_SEGMENT_WIDTH * DETAIL_TABS.size + 8.dp

/** COUI 搜索栏背景变形的时长，让分段收起与胶囊铺开同一条节奏。 */
private const val DETAIL_RESIZE_MILLIS = 450

/** COUI 的 `COUIMoveEaseInterpolator`：cubic-bezier(0.3, 0, 0.1, 1)。 */
private val MoveEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
