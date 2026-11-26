package erl.bilico.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.suqi8.coui.kmp.basic.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.settings.StatsOverlayCorner
import erl.bilico.data.settings.StatsOverlayField
import erl.bilico.data.settings.StatsOverlayPreferences
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

/**
 * 统计信息浮窗二级设置页：开关、四角快捷停靠与自由拖动预览、以及 15 项显示字段开关。
 */
@Composable
fun StatsOverlaySettingsScreen(
    container: AppContainer,
    nav: NavState,
) {
    val prefs = container.settings.statsOverlay
    val enabled by prefs.enabled.collectAsStateWithLifecycle()
    val xFraction by prefs.xFraction.collectAsStateWithLifecycle()
    val yFraction by prefs.yFraction.collectAsStateWithLifecycle()
    val visibleFields by prefs.visibleFields.collectAsStateWithLifecycle()
    val activeCorner = remember(xFraction, yFraction) {
        StatsOverlayPreferences.matchingCorner(xFraction, yFraction)
    }

    SecondaryPage(title = "统计信息浮窗", onBack = nav::pop) {
        item(key = "toggle") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                SwitchPreference(
                    checked = enabled,
                    onCheckedChange = prefs::setEnabled,
                    title = "显示统计浮窗",
                    summary = "在播放画面上方悬浮显示纯文字统计信息（无边框），可在播放时直接拖动位置",
                    modifier = Modifier.testTag("stats_overlay_switch"),
                    cardListPosition = CardListPosition.None,
                )
            }
        }

        item(key = "position-title") { SectionTitle("显示位置") }

        item(key = "position") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(14.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                StatsPositionPreview(
                    xFraction = xFraction,
                    yFraction = yFraction,
                    visibleFields = visibleFields,
                    onPositionCommit = prefs::setPosition,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatsOverlayCorner.entries.forEach { corner ->
                        CornerButton(
                            label = corner.label,
                            selected = activeCorner == corner,
                            onClick = { prefs.setCorner(corner) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "当前：${StatsOverlayPreferences.positionLabel(xFraction, yFraction)}，可直接拖动预览框内文字或点击四角快捷定位；播放时直接长按浮窗文字可关闭",
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }

        item(key = "fields-title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "显示项目（${visibleFields.size}/${StatsOverlayField.entries.size}）",
                    style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTextAction("全选", onClick = prefs::selectAllFields)
                    QuickTextAction("恢复默认", onClick = prefs::resetFieldsToDefault)
                }
            }
        }

        item(key = "fields") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                val all = StatsOverlayField.entries
                all.forEachIndexed { index, field ->
                    val position = when (index) {
                        0 -> CardListPosition.Head
                        all.lastIndex -> CardListPosition.Tail
                        else -> CardListPosition.Middle
                    }
                    SwitchPreference(
                        checked = field.id in visibleFields,
                        onCheckedChange = { checked -> prefs.setFieldVisible(field.id, checked) },
                        title = field.label,
                        modifier = Modifier.testTag("stats_field_${field.id}"),
                        cardListPosition = position,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsPositionPreview(
    xFraction: Float,
    yFraction: Float,
    visibleFields: Set<String>,
    onPositionCommit: (Float, Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember(xFraction) { mutableFloatStateOf(xFraction) }
    var dragY by remember(yFraction) { mutableFloatStateOf(yFraction) }
    val activeX = if (dragging) dragX else xFraction
    val activeY = if (dragging) dragY else yFraction

    val sampleLines = remember(visibleFields) {
        val list = StatsOverlayField.entries
            .filter { it.id in visibleFields }
            .map { "${it.label}: ${sampleValueFor(it)}" }
        list.ifEmpty { listOf("未选择任何显示项目") }
    }
    val density = LocalDensity.current
    val padPx = with(density) { 10.dp.roundToPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF14161A))
            .border(1.dp, COUITheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .testTag("stats_overlay_preview"),
    ) {
        var boxWidthPx by remember { mutableIntStateOf(0) }
        var boxHeightPx by remember { mutableIntStateOf(0) }
        val maxX = (constraints.maxWidth - boxWidthPx - padPx * 2).coerceAtLeast(0)
        val maxY = (constraints.maxHeight - boxHeightPx - padPx * 2).coerceAtLeast(0)
        val xPx = padPx + (activeX * maxX).roundToInt()
        val yPx = padPx + (activeY * maxY).roundToInt()

        val previewStyle = if (sampleLines.size > 10) PREVIEW_STATS_COMPACT_TEXT_STYLE else PREVIEW_STATS_TEXT_STYLE
        Column(
            horizontalAlignment = if (activeX > 0.6f) Alignment.End else Alignment.Start,
            modifier = Modifier
                .offset { IntOffset(xPx, yPx) }
                .onSizeChanged {
                    boxWidthPx = it.width
                    boxHeightPx = it.height
                }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragStart = {
                            dragX = xFraction
                            dragY = yFraction
                            dragging = true
                        },
                        onDragEnd = {
                            dragging = false
                            onPositionCommit(dragX, dragY)
                        },
                        onDragCancel = {
                            dragging = false
                            onPositionCommit(dragX, dragY)
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        if (maxX > 0) dragX = (dragX + dragAmount.x / maxX).coerceIn(0f, 1f)
                        if (maxY > 0) dragY = (dragY + dragAmount.y / maxY).coerceIn(0f, 1f)
                    }
                },
        ) {
            sampleLines.forEach { line ->
                MaterialText(
                    text = line,
                    style = previewStyle,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CornerButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) scheme.primary else scheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary else scheme.outline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("stats_corner_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.footnote1.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) scheme.onPrimary else scheme.onSurface,
        )
    }
}

@Composable
private fun QuickTextAction(
    label: String,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.footnote1,
            color = scheme.primary,
        )
    }
}

private fun sampleValueFor(field: StatsOverlayField): String = when (field) {
    StatsOverlayField.QUALITY -> "1080P 60 FPS"
    StatsOverlayField.RESOLUTION -> "1920 x 1080"
    StatsOverlayField.FRAME_RATE -> "60.0 fps"
    StatsOverlayField.PLAYER_TYPE -> "ExoPlayer (MediaCodec)"
    StatsOverlayField.VIDEO_CODEC -> "HEVC"
    StatsOverlayField.AUDIO_CODEC -> "AAC"
    StatsOverlayField.VIDEO_BITRATE -> "4.2 Mbps"
    StatsOverlayField.AUDIO_BITRATE -> "192 kbps"
    StatsOverlayField.RENDERED_FRAMES -> "1240"
    StatsOverlayField.DROPPED_FRAMES -> "0 / 1240 (0.0%)"
    StatsOverlayField.BUFFER -> "18.5 s"
    StatsOverlayField.BANDWIDTH -> "32.0 Mbps"
    StatsOverlayField.VIDEO_HOST -> "upos-sz-mirrorcos.bilivideo.com"
    StatsOverlayField.AUDIO_HOST -> "upos-sz-mirrorcos.bilivideo.com"
    StatsOverlayField.SPEED -> "1.0x"
}

private val PREVIEW_STATS_TEXT_STYLE = TextStyle(
    color = Color.White,
    fontSize = 8.5.sp,
    lineHeight = 10.sp,
    fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.Monospace,
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.9f),
        offset = Offset(1f, 1f),
        blurRadius = 2.5f,
    ),
)

private val PREVIEW_STATS_COMPACT_TEXT_STYLE = PREVIEW_STATS_TEXT_STYLE.copy(
    fontSize = 7.sp,
    lineHeight = 8.2.sp,
)
