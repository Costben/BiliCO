package erl.bilico.feature.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.SearchSuggestion
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.COUISmoothCapsuleShape
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Search
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

private val EM_TAG_REGEX = Regex("<em[^>]*>(.*?)</em>", RegexOption.IGNORE_CASE)
private val ANY_HTML_TAG_REGEX = Regex("<[^>]+>")

/** 实时输入期间的搜索建议列表；未出词或无联想时兜底给一条直搜入口，避免整块空白闪跳。 */
internal fun LazyListScope.searchSuggestSection(
    query: String,
    suggestions: List<SearchSuggestion>,
    suggestionsLoading: Boolean,
    onPick: (String) -> Unit,
) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return

    if (suggestions.isNotEmpty()) {
        item(key = "suggest-card") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                suggestions.forEachIndexed { index, item ->
                    SuggestionRow(
                        query = trimmed,
                        suggestion = item,
                        onClick = remember(item.text, onPick) { { onPick(item.text) } },
                        modifier = Modifier.testTag("search_suggest_$index"),
                    )
                    if (index < suggestions.lastIndex) {
                        HorizontalDivider(
                            Modifier.padding(start = 44.dp, end = DividerDefaults.CardInset),
                        )
                    }
                }
            }
        }
    } else {
        item(key = "suggest-direct") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                DirectSearchRow(
                    query = trimmed,
                    loading = suggestionsLoading,
                    onClick = remember(trimmed, onPick) { { onPick(trimmed) } },
                    modifier = Modifier.testTag("search_suggest_direct"),
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    query: String,
    suggestion: SearchSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val styled = remember(query, suggestion, scheme.primary, scheme.onSurface) {
        buildSuggestionAnnotatedString(
            query = query,
            suggestion = suggestion,
            highlightColor = scheme.primary,
            normalColor = scheme.onSurface,
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = COUIIcons.Basic.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = scheme.onSurfaceSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = styled,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DirectSearchRow(
    query: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val label = remember(query, scheme.primary, scheme.onSurface) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = scheme.onSurface)) {
                append("搜索 “")
            }
            withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Medium)) {
                append(query)
            }
            withStyle(SpanStyle(color = scheme.onSurface)) {
                append("”")
            }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = COUIIcons.Basic.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = scheme.onSurfaceSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (loading) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                size = 15.dp,
                strokeWidth = 1.8.dp,
            )
        }
    }
}

private fun buildSuggestionAnnotatedString(
    query: String,
    suggestion: SearchSuggestion,
    highlightColor: Color,
    normalColor: Color,
): AnnotatedString {
    val rawHtml = suggestion.rawHtml
    if (rawHtml.isNotBlank() && EM_TAG_REGEX.containsMatchIn(rawHtml)) {
        val parsed = buildAnnotatedString {
            var cursor = 0
            for (match in EM_TAG_REGEX.findAll(rawHtml)) {
                if (match.range.first > cursor) {
                    val plain = cleanHtmlSegment(rawHtml.substring(cursor, match.range.first))
                    if (plain.isNotEmpty()) {
                        withStyle(SpanStyle(color = normalColor)) { append(plain) }
                    }
                }
                val highlighted = cleanHtmlSegment(match.groupValues[1])
                if (highlighted.isNotEmpty()) {
                    withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Medium)) {
                        append(highlighted)
                    }
                }
                cursor = match.range.last + 1
            }
            if (cursor < rawHtml.length) {
                val tail = cleanHtmlSegment(rawHtml.substring(cursor))
                if (tail.isNotEmpty()) {
                    withStyle(SpanStyle(color = normalColor)) { append(tail) }
                }
            }
        }
        if (parsed.text.isNotEmpty()) return parsed
    }

    val text = suggestion.text
    if (query.isBlank()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val found = text.indexOf(query, startIndex = cursor, ignoreCase = true)
            if (found < 0) {
                withStyle(SpanStyle(color = normalColor)) { append(text.substring(cursor)) }
                break
            }
            if (found > cursor) {
                withStyle(SpanStyle(color = normalColor)) { append(text.substring(cursor, found)) }
            }
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Medium)) {
                append(text.substring(found, found + query.length))
            }
            cursor = found + query.length
        }
    }
}

private fun cleanHtmlSegment(raw: String): String =
    Formatters.decodeHtmlEntities(raw.replace(ANY_HTML_TAG_REGEX, ""))

internal fun LazyListScope.searchHomeSection(
    hot: List<String>,
    hotLoading: Boolean,
    history: List<String>,
    onPick: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isNotEmpty()) {
        item(key = "history-title") {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("搜索历史", Modifier.weight(1f), horizontalPadding = 6.dp)
                Text(
                    text = "清空",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
        item(key = "history") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KeywordChips(
                    keywords = history,
                    onPick = onPick,
                    onDelete = onDeleteHistory,
                )
            }
        }
    }

    item(key = "hot-title") {
        SectionTitle("热搜", Modifier.padding(horizontal = 16.dp), horizontalPadding = 6.dp)
    }

    if (hotLoading && hot.isEmpty()) {
        item(key = "hot-loading") { Box(Modifier.padding(horizontal = 16.dp)) { LoadingState() } }
    } else {
        item(key = "hot") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (hot.isEmpty()) {
                    EmptyState("暂时没有热搜")
                } else {
                    HotKeywordChips(keywords = hot, onPick = onPick)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordChips(
    keywords: List<String>,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keywords.forEach { keyword ->
            HistoryChip(
                text = keyword,
                onClick = remember(keyword, onPick) { { onPick(keyword) } },
                onDelete = remember(keyword, onDelete) { { onDelete(keyword) } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryChip(
    text: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var isDeleteMode by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animProgress = remember { Animatable(if (isDeleteMode) 1f else 0f) }
    LaunchedEffect(isDeleteMode) {
        animProgress.animateTo(
            targetValue = if (isDeleteMode) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 438.65f,
            ),
        )
    }
    val animFraction = animProgress.value

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 438.65f,
        ),
        label = "history-chip-press-scale",
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
        label = "history-chip-bg",
    )

    Layout(
        content = {
            Text(
                text = text,
                fontSize = 13.sp,
                color = COUITheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 32.dp)
                    .graphicsLayer {
                        scaleX = 0.3f + 0.7f * animFraction
                        scaleY = 0.3f + 0.7f * animFraction
                        alpha = animFraction
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = isDeleteMode && animFraction > 0.5f,
                        onClick = onDelete,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val iconColor = COUITheme.colorScheme.onSurfaceSecondary
                Canvas(modifier = Modifier.size(9.dp)) {
                    val stroke = 1.3.dp.toPx()
                    drawLine(
                        color = iconColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        },
        modifier = Modifier
            .height(32.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(COUISmoothCapsuleShape())
            .background(animatedBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isDeleteMode) {
                        isDeleteMode = false
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    isDeleteMode = !isDeleteMode
                },
            ),
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val closePlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))

        val paddingStartPx = 12.dp.roundToPx()
        val paddingEndClosedPx = 12.dp.roundToPx()
        val extraWidthPx = 18.dp.roundToPx()
        val currentExtraPx = (extraWidthPx * animFraction).roundToInt()

        val totalWidth = paddingStartPx + textPlaceable.width + paddingEndClosedPx + currentExtraPx
        val totalHeight = constraints.maxHeight.coerceAtLeast(32.dp.roundToPx())

        layout(totalWidth, totalHeight) {
            val textY = (totalHeight - textPlaceable.height) / 2
            textPlaceable.placeRelative(paddingStartPx, textY)

            val closeY = (totalHeight - closePlaceable.height) / 2
            val closeX = paddingStartPx + textPlaceable.width + (currentExtraPx - extraWidthPx) + 2.dp.roundToPx()
            closePlaceable.placeRelative(closeX, closeY)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotKeywordChips(keywords: List<String>, onPick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keywords.forEachIndexed { index, keyword ->
            val rank = index + 1
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
                modifier = Modifier
                    .height(32.dp)
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
                    .padding(horizontal = 12.dp),
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
                )
                Text(
                    text = keyword,
                    fontSize = 13.sp,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
