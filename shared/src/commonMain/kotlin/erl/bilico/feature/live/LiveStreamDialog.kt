package erl.bilico.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.LiveCandidate
import erl.bilico.data.model.LiveRoomDetail
import erl.bilico.data.model.LiveStreamNode
import erl.bilico.data.model.LiveStreamSelection
import erl.bilico.data.network.BiliClient
import erl.bilico.data.util.Formatters
import erl.bilico.feature.video.PlayerModalHost
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.ExpandMore
import io.github.suqi8.coui.kmp.icon.extended.Music
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState

/**
 * 照搬 PiliPlus `_showLiveStreamDialog`：按 protocol -> format -> codec -> url_info 四级展开切换线路。
 * 全屏时走 `inlineInStage = true`，避免弹窗触发舞台退出全屏。
 */
@Composable
internal fun LiveStreamDialog(
    streams: List<LiveStreamNode>,
    current: LiveStreamSelection,
    inlineInStage: Boolean,
    onSelect: (LiveStreamSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
        maxWidth = 420.dp,
    ) {
        val scheme = COUITheme.colorScheme
        val dismiss = LocalDismissState.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "切换线路",
                    style = COUITheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(text = "关闭", onClick = dismiss)
            }
            if (streams.isEmpty()) {
                Text(
                    text = "当前暂无可切换线路",
                    fontSize = 13.sp,
                    color = scheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (inlineInStage) 240.dp else 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    streams.forEachIndexed { si, stream ->
                        val isCurrStream = si == current.streamIndex
                        var streamOpen by remember(si, isCurrStream) { mutableStateOf(isCurrStream) }
                        LineGroupRow(
                            label = stream.protocolName.ifBlank { "线路 $si" },
                            expanded = streamOpen,
                            active = isCurrStream,
                            onClick = { streamOpen = !streamOpen },
                        )
                        if (streamOpen) {
                            stream.formats.forEachIndexed { fi, format ->
                                val isCurrFormat = isCurrStream && fi == current.formatIndex
                                var formatOpen by remember(si, fi, isCurrFormat) { mutableStateOf(isCurrFormat) }
                                LineGroupRow(
                                    label = format.formatName.ifBlank { "格式 $fi" },
                                    expanded = formatOpen,
                                    active = isCurrFormat,
                                    indent = 16.dp,
                                    onClick = { formatOpen = !formatOpen },
                                )
                                if (formatOpen) {
                                    format.codecs.forEachIndexed { ci, codec ->
                                        val isCurrCodec = isCurrFormat && ci == current.codecIndex
                                        var codecOpen by remember(si, fi, ci, isCurrCodec) { mutableStateOf(isCurrCodec) }
                                        val codecLabel = buildString {
                                            append(codec.codecName.ifBlank { "编码 $ci" })
                                            if (codec.qualityDesc.isNotBlank()) append(" (${codec.qualityDesc})")
                                        }
                                        LineGroupRow(
                                            label = codecLabel,
                                            expanded = codecOpen,
                                            active = isCurrCodec,
                                            indent = 32.dp,
                                            onClick = { codecOpen = !codecOpen },
                                        )
                                        if (codecOpen) {
                                            codec.urlInfos.forEachIndexed { ui, urlInfo ->
                                                val isCurrUrl = isCurrCodec && ui == current.urlIndex
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(SquircleShape(8.dp))
                                                        .clickable(enabled = !isCurrUrl) {
                                                            onSelect(LiveStreamSelection(si, fi, ci, ui))
                                                            dismiss()
                                                        }
                                                        .padding(start = 48.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text(
                                                        text = "${urlInfo.host}...",
                                                        fontSize = 13.sp,
                                                        color = if (isCurrUrl) scheme.primary else scheme.onSurfaceSecondary,
                                                        fontWeight = if (isCurrUrl) FontWeight.Medium else FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    if (isCurrUrl) {
                                                        Icon(
                                                            imageVector = COUIIcons.Ok,
                                                            contentDescription = "当前线路",
                                                            tint = scheme.primary,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (si < streams.lastIndex) {
                            HorizontalDivider(color = scheme.dividerLine)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineGroupRow(
    label: String,
    expanded: Boolean,
    active: Boolean,
    indent: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val tint = if (active) scheme.primary else scheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = indent + 4.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) COUIIcons.ExpandMore else COUIIcons.ChevronForward,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun LiveAudioOnlyOverlay(detail: LiveRoomDetail?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            val face = detail?.anchorFace.orEmpty()
            if (face.isNotBlank()) {
                AsyncImage(
                    model = Formatters.sizedImageUrl(face, 144, 144),
                    contentDescription = detail?.anchorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = COUIIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                text = "黑听中 · 仅播放音频",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = listOfNotNull(
                    detail?.anchorName?.takeIf { it.isNotBlank() },
                    "点击屏幕可在右上角退出黑听",
                ).joinToString(" · "),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )
        }
    }
}
