package erl.bilico.feature.video

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.DanmakuToggle
import erl.bilico.ui.components.SegmentCapsule
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
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
    commentCount: Long,
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

    // 「评论」后面带上评论数；数字没拿到之前维持原来的两段等宽。
    val tabs = remember(commentCount) {
        if (commentCount > 0L) {
            listOf(DETAIL_TABS[0], "评论 ${Formatters.formatStat(commentCount)}")
        } else {
            DETAIL_TABS
        }
    }
    val density = LocalDensity.current
    val labelStyle = COUITheme.textStyles.main.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
    val measurer = rememberTextMeasurer()
    val segmentWidths = remember(tabs, density) {
        tabs.map { label ->
            val textWidth = measurer.measure(
                text = label,
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width
            val width = with(density) { textWidth.toDp() } + DETAIL_LABEL_PADDING
            if (width > DETAIL_SEGMENT_WIDTH) width else DETAIL_SEGMENT_WIDTH
        }
    }
    val tabsWidth = segmentWidths.fold(0.dp) { sum, width -> sum + width } + 8.dp

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
                .width(tabsWidth * (1f - collapse))
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(tabsWidth)
                    .fadeTrailingEdge(fade = collapse, band = 24.dp),
            ) {
                SegmentCapsule(
                    labels = tabs,
                    selectedIndex = selected,
                    onSelect = onSelect,
                    segmentWidths = segmentWidths,
                    pager = pager,
                    // 不要底槽：未选中只留文字，选中项一块高亮，颜色与右侧发弹幕胶囊一致。
                    height = DETAIL_CAPSULE_HEIGHT,
                    trackColor = Color.Transparent,
                    highlightColor = scheme.surfaceContainer,
                    highlightElevation = DETAIL_CAPSULE_ELEVATION,
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
                    label = "发弹幕",
                    colors = fieldColors,
                    stroke = null,
                    glowColor = null,
                    leadingIcon = { Spacer(Modifier.width(14.dp)) },
                    trailingIcon = {
                        DanmakuToggle(
                            enabled = danmakuEnabled,
                            onToggle = onToggleDanmaku,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DETAIL_CAPSULE_HEIGHT)
                        .shadow(
                            elevation = DETAIL_CAPSULE_ELEVATION,
                            shape = DetailCapsuleShape,
                            clip = false,
                        ),
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
                    modifier = Modifier.shadow(
                        elevation = DETAIL_CAPSULE_ELEVATION,
                        shape = DetailCapsuleShape,
                        clip = false,
                    ),
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

private val DETAIL_TABS = listOf("简介", "评论")

/** 分段高亮块与弹幕输入框共用的高度，两者同一行要对齐。 */
private val DETAIL_CAPSULE_HEIGHT = 36.dp

/** 高亮块、发弹幕输入框与发送按钮共用的阴影，三块材质看起来才是一套。 */
private val DETAIL_CAPSULE_ELEVATION = 1.5.dp

/** 胶囊轮廓，给阴影用；与 COUI 搜索框的胶囊形状一致。 */
private val DetailCapsuleShape = RoundedCornerShape(percent = 50)

/** 每段的宽度；两段加 8dp 内边距就是整条分段胶囊的宽度。 */
private val DETAIL_SEGMENT_WIDTH = 56.dp

/** 标签两侧的留白：文字宽度加上它才是这一段的宽度。 */
private val DETAIL_LABEL_PADDING = 26.dp

/** COUI 搜索栏背景变形的时长，让分段收起与胶囊铺开同一条节奏。 */
private const val DETAIL_RESIZE_MILLIS = 450

/** COUI 的 `COUIMoveEaseInterpolator`：cubic-bezier(0.3, 0, 0.1, 1)。 */
private val MoveEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
