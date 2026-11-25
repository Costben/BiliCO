package erl.bilico.feature.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.settings.CoverAspect
import erl.bilico.data.util.Formatters
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.RowScrollState
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.attachToRow
import erl.bilico.ui.components.COUISmoothCapsuleShape
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.expandOrigin
import erl.bilico.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 搜索框下方的热搜词条单元格（支持双列等宽网格与单列自适应宽度）。 */
@Composable
internal fun HotKeywordGridCell(
    rank: Int,
    keyword: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 438.65f,
        ),
        label = "hot-chip-press-scale",
    )
    val baseBg = COUITheme.colorScheme.surfaceContainerHigh
    val pressedBg = remember(baseBg) {
        Color(
            red = (baseBg.red * 0.72f + 0.28f).coerceIn(0f, 1f),
            green = (baseBg.green * 0.72f + 0.28f).coerceIn(0f, 1f),
            blue = (baseBg.blue * 0.72f + 0.28f).coerceIn(0f, 1f),
            alpha = baseBg.alpha,
        )
    }
    val animatedBg by animateColorAsState(
        targetValue = if (isPressed) pressedBg else baseBg,
        animationSpec = tween(durationMillis = 150),
        label = "hot-chip-bg",
    )
    Row(
        modifier = modifier
            .height(34.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(COUISmoothCapsuleShape())
            .background(animatedBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = remember(keyword, onPick) { { onPick(keyword) } },
            )
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$rank",
            fontSize = 12.sp,
            fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
            color = when (rank) {
                1 -> Color(0xFFFF3B30)
                2 -> Color(0xFFFF9500)
                3 -> Color(0xFFFFCC00)
                else -> COUITheme.colorScheme.onSurfaceSecondary
            },
            maxLines = 1,
        )
        Text(
            text = keyword,
            fontSize = 13.sp,
            color = COUITheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (fullWidth) Modifier.weight(1f) else Modifier,
        )
    }
}

/** 搜索视频结果顶部的轻量排版切换条：单列列表 / 双列网格。 */
@Composable
internal fun SearchLayoutToggleRow(
    orderLabel: String,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = orderLabel,
            fontSize = 12.sp,
            color = scheme.onSurfaceSecondary,
            maxLines = 1,
        )
        Row(
            modifier = Modifier
                .clip(COUISmoothCapsuleShape())
                .background(scheme.surfaceContainerHigh)
                .clickable(onClick = onToggleGrid)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag("search_layout_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (gridMode) SearchListLayoutIcon else SearchGridLayoutIcon,
                contentDescription = if (gridMode) "切换为单列" else "切换为网格",
                modifier = Modifier.size(13.dp),
                tint = scheme.onSurface,
            )
            Text(
                text = if (gridMode) "单列" else "网格",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** 双列网格渲染搜索视频结果：复用外层 LazyColumn，保留卡片展开动画与顶部混排项。 */
internal fun LazyListScope.searchVideoGridItems(
    videos: List<SearchVideo>,
    keyPrefix: String,
    nav: NavState,
    rowScrollState: RowScrollState,
    topBarBounds: TopBarBounds,
    coverAspect: CoverAspect = CoverAspect.WIDE,
) {
    val rows = videos.chunked(2)
    items(
        items = rows,
        key = { pair -> "${keyPrefix}_grid_${pair.first().bvid}_${pair.getOrNull(1)?.bvid.orEmpty()}" },
        contentType = { "search-video-grid-row" },
    ) { pair ->
        val rowKey = "${keyPrefix}_grid_${pair.first().bvid}_${pair.getOrNull(1)?.bvid.orEmpty()}"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pair.forEach { video ->
                val origin = rememberExpandOrigin()
                SideEffect {
                    origin.attachToRow(rowScrollState, rowKey, topBarBounds)
                }
                SearchVideoGridCard(
                    video = video,
                    coverAspect = coverAspect,
                    modifier = Modifier
                        .weight(1f)
                        .expandOrigin(origin),
                    onClick = { nav.push(Route.Video(video.bvid), origin) },
                )
            }
            if (pair.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchVideoGridCard(
    video: SearchVideo,
    coverAspect: CoverAspect,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val coverWidthPx = remember(screenWidthDp, density) {
        with(density) { ((screenWidthDp.dp - 42.dp) / 2f).roundToPx() }.coerceIn(1, 600)
    }
    val coverHeightPx = remember(coverWidthPx, coverAspect) {
        (coverWidthPx / coverAspect.ratio).toInt().coerceAtLeast(1)
    }
    val coverUrl = remember(video.cover, coverWidthPx, coverHeightPx) {
        Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx)
    }

    val pressState = rememberPreciseClickState()
    var longPressMenuExpanded by remember { mutableStateOf(false) }
    val menuItems = rememberSearchVideoMenuItems(video)

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .preciseClickAnchor(pressState),
            insideMargin = PaddingValues(0.dp),
            onClick = onClick,
            onLongPress = { longPressMenuExpanded = true },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(coverAspect.ratio)
                        .clip(SquircleShape(10.dp)),
                ) {
                    CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                    SearchVideoCoverBadges(playText = video.playText, durationText = video.durationText)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 6.dp),
                ) {
                    Text(
                        text = video.title,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = COUITheme.colorScheme.onSurface,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = video.author.ifBlank { "未知 UP" },
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (video.pubdateText.isNotBlank()) {
                            Text(
                                text = video.pubdateText,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                            )
                        }
                        SearchVideoMoreButton(
                            items = menuItems,
                            buttonSize = 20.dp,
                            iconSize = 15.dp,
                        )
                    }
                }
            }
        }

        CouiPopupMenu(
            show = longPressMenuExpanded,
            onDismiss = { longPressMenuExpanded = false },
            items = menuItems,
            preciseClickState = pressState,
        )
    }
}

private val SearchGridLayoutIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SearchGridLayout",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val stroke = SolidColor(Color.Black)
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
            moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
            moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
            moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
        }
    }.build()
}

private val SearchListLayoutIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SearchListLayout",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val stroke = SolidColor(Color.Black)
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 18f); lineTo(20f, 18f)
        }
    }.build()
}
