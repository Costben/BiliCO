package erl.bilico.feature.video

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.chip.ChipTab
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.shape.CouiCapsuleShape
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.DanmakuToggle
import io.github.suqi8.coui.kmp.anim.folmeSpring
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 播放页标题栏下方的一条：左边「简介 / 评论」胶囊标签，右边弹幕输入胶囊。
 *
 * 标签直接用 COUI 的 [ChipTabGroup]（ColorOS 17 的 chip tab）：选中项是一块带高光与焦散光的胶囊，
 * 未选中只有文字；弹幕胶囊就是 COUI 的搜索框，去掉材质描边与底部焦散光。
 * 点进去后标签收窄淡出、胶囊铺满整行，右端按搜索栏取消按钮的规格滑出「发送」。
 */
@Composable
internal fun VideoDetailBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    commentCount: Long,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    composing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onComposingChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val fieldColors = SearchBarDefaults.searchBarColors(backgroundColor = scheme.surfaceContainer)

    // 「评论」后面带上评论数；数字没拿到之前维持原来的两段等宽。
    val tabs = remember(commentCount) {
        if (commentCount > 0L) {
            listOf(DETAIL_TABS[0], "评论 ${Formatters.formatStat(commentCount)}")
        } else {
            DETAIL_TABS
        }
    }
    val fallbackBackdrop = rememberCouiBackdrop()
    val activeBackdrop = backdrop ?: fallbackBackdrop
    val detailRenderers = remember { List(2) { CouiFloatingRenderer.standalone() } }
    val density = LocalDensity.current
    val chipStyle = ChipTabDefaults.textStyle()
    val measurer = rememberTextMeasurer()
    val tabsWidth = remember(tabs, density, chipStyle) {
        tabs.map { label ->
            val textWidth = measurer.measure(
                text = label,
                style = chipStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width
            (with(density) { textWidth.toDp() } + 32.dp).coerceAtLeast(56.dp)
        }.fold(0.dp) { sum, width -> sum + width } +
            8.dp * (tabs.size - 1).coerceAtLeast(0)
    }

    // 收起态靠这条间距把胶囊从标签后面拉开，展开时归零，跟着标签一起收。
    val gap by animateDpAsState(
        targetValue = if (composing) 0.dp else 8.dp,
        animationSpec = tween(DETAIL_RESIZE_MILLIS, easing = MoveEase),
    )
    // 标签收起的进度：宽度跟着收窄，同时整块淡出，收完后不留一条硬边。
    val collapse by animateFloatAsState(
        targetValue = if (composing) 1f else 0f,
        animationSpec = tween(DETAIL_RESIZE_MILLIS, easing = MoveEase),
    )
    // 收起时标签同时向左退场，右缘始终咬住收窄后的边界，
    // 否则内层还占着整条宽度，会在输入框下面透出半透明的重影。
    // 右端补偿跟着「发送」的展开走同一段节奏，整段动画里行尾都咬住不动。
    val actionSlack by animateDpAsState(
        targetValue = if (composing) ACTION_END_SLACK else 0.dp,
        animationSpec = tween(DETAIL_RESIZE_MILLIS, easing = MoveEase),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DETAIL_BAR_HEIGHT)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(tabsWidth * (1f - collapse))) {
            Box(
                modifier = Modifier
                    .requiredWidth(tabsWidth)
                    .graphicsLayer {
                        alpha = 1f - collapse
                        translationX = -size.width * collapse
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, title ->
                        ChipTab(
                            selected = selected == index,
                            onClick = { onSelect(index) },
                            label = title,
                            backdrop = activeBackdrop,
                            dark = isDark,
                            renderer = detailRenderers[index],
                        )
                    }
                }
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
                        .detailCapsuleMaterial(isDark = isDark),
                )
            },
            onExpandedChange = onComposingChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = gap)
                // outsideEndAction 槽位按旧的纯文字「取消」留了 4dp 起始 + 16dp 结尾的外边距，
                // 展开后整行右端会多空出 16dp。这里只把搜索栏多量出这一段，让它自己吃回去。
                .actionEndSlack(actionSlack),
            insideMargin = DpSize(0.dp, 2.dp),
            expanded = composing,
            outsideEndAction = {
                DetailSendButton(
                    text = "发送",
                    onClick = {
                        keyboard?.hide()
                        onSend()
                    },
                    enabled = draft.isNotBlank(),
                    containerColor = scheme.surfaceContainer,
                    isDark = isDark,
                )
            },
        ) { }
    }
}

private val DETAIL_TABS = listOf("简介", "评论")

/**
 * 与顶栏返回/主页/更多按键材质一致的轻量胶囊外观：
 * 采用极淡温润高光轮廓，去除原本厚重过深的 Elevation 阴影。
 */
private fun Modifier.detailCapsuleMaterial(isDark: Boolean): Modifier =
    this.border(
        width = 0.5.dp,
        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f),
        shape = CouiCapsuleShape,
    )

/**
 * 按 [slack] 多量出右侧宽度，但仍按原本的宽度上报。
 *
 * COUI 的 `outsideEndAction` 槽位在按钮后面固定垫了 16dp（`ActionEndPadding`），
 * 那是给旧的纯文字「取消」算的。把胶囊「发送」塞进去，整行右端就比左端多空一截；
 * 多量这 16dp 让搜索栏自己把这截吃回去，行尾重新对齐。
 */
private fun Modifier.actionEndSlack(slack: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = slack.roundToPx()
        val placeable = if (extra == 0) {
            measurable.measure(constraints)
        } else {
            measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + extra,
                    maxWidth = constraints.maxWidth + extra,
                ),
            )
        }
        layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
    }

/**
 * 专为弹幕输入行定制的「发送」按钮：
 * 高度严格对齐发弹幕输入框（32dp），变得更加小巧紧凑；
 * 采用连续曲率胶囊形状 [CouiCapsuleShape] 与顶栏按键同款微光材质。
 */
@Composable
private fun DetailSendButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && isPressed) 1f else 0f,
        animationSpec = remember { folmeSpring(damping = 1f, response = 0.3f, visibilityThreshold = 0.001f) },
        label = "detailSendButtonPress",
    )
    val scale = 1f - 0.11f * pressProgress
    val contentColor = if (enabled) {
        COUITheme.colorScheme.onSurface
    } else {
        COUITheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        modifier = modifier
            .height(DETAIL_CAPSULE_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .detailCapsuleMaterial(isDark = isDark)
            .background(color = containerColor, shape = CouiCapsuleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 弹幕输入框与发送按钮共用的紧凑高度；与 COUI-Kit ChipTab 32dp 一致 */
private val DETAIL_CAPSULE_HEIGHT = 32.dp

/**
 * 「简介 / 评论」标签栏的高度。
 *
 * 详情页把列表顶到整块区域的最上面，由这条栏浮在上面盖住滚过去的正文；
 * 列表的顶部内边距要按这个高度留，静止时第一行才不会被栏压住。
 */
internal val DETAIL_BAR_HEIGHT = 48.dp

/** COUI `SearchBar` 在 `outsideEndAction` 后面固定垫的 16dp，展开时补回来。 */
private val ACTION_END_SLACK = 16.dp

/** COUI 搜索栏背景变形的时长，让标签收起与胶囊铺开同一条节奏。 */
private const val DETAIL_RESIZE_MILLIS = 450

/** COUI 的 `COUIMoveEaseInterpolator`：cubic-bezier(0.3, 0, 0.1, 1)。 */
private val MoveEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
