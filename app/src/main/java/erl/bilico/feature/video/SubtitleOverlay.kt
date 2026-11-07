package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.PlayerAuxiliaryInfo
import erl.bilico.data.model.SubtitleCue
import erl.bilico.data.model.SubtitleTrack
import erl.bilico.data.repository.SubtitleRepository
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
internal class SubtitleController(
    private val repository: SubtitleRepository,
    private val scope: CoroutineScope,
) {
    var tracks by mutableStateOf<List<SubtitleTrack>>(emptyList())
        private set

    var selectedTrackKey by mutableStateOf<String?>(null)
        private set

    var cues by mutableStateOf<List<SubtitleCue>>(emptyList())
        private set

    private var attachJob: Job? = null
    private var cueJob: Job? = null
    private val cueCache = mutableMapOf<String, List<SubtitleCue>>()
    private var attachedKey = ""

    fun attach(
        bvid: String = "",
        aid: Long = 0L,
        cid: Long,
        onAuxiliaryLoaded: ((PlayerAuxiliaryInfo) -> Unit)? = null,
    ) {
        val key = "$bvid|$aid|$cid"
        if (cid <= 0L || key == attachedKey) return
        attachedKey = key
        attachJob?.cancel()
        cueJob?.cancel()
        tracks = emptyList()
        selectedTrackKey = null
        cues = emptyList()

        attachJob = scope.launch {
            repository.fetchPlayerInfo(bvid = bvid, aid = aid, cid = cid)
                .onSuccess { info ->
                    tracks = info.subtitles
                    // 若存在非 AI 的官方字幕，默认自动开启第一条；仅 AI 字幕时保留手动开启，避免干扰。
                    val autoTrack = info.subtitles.firstOrNull { !it.isAi }
                    if (autoTrack != null) {
                        selectTrack(autoTrack.key)
                    }
                    onAuxiliaryLoaded?.invoke(info)
                }
        }
    }

    fun selectTrack(trackKey: String?) {
        selectedTrackKey = trackKey
        cueJob?.cancel()
        if (trackKey == null) {
            cues = emptyList()
            return
        }
        val track = tracks.firstOrNull { it.key == trackKey } ?: run {
            cues = emptyList()
            return
        }
        cueCache[track.key]?.let { cached ->
            cues = cached
            return
        }
        cueJob = scope.launch {
            repository.fetchCues(track.url).onSuccess { loaded ->
                cueCache[track.key] = loaded
                if (selectedTrackKey == track.key) {
                    cues = loaded
                }
            }
        }
    }

    val buttonLabel: String?
        get() {
            if (tracks.isEmpty()) return null
            val active = tracks.firstOrNull { it.key == selectedTrackKey }
            return if (active != null) "字幕开" else "字幕"
        }
}

@Composable
internal fun SubtitleOverlay(
    cues: List<SubtitleCue>,
    positionMs: Long,
    fullscreen: Boolean,
    controlsVisible: Boolean,
    inPip: Boolean,
    modifier: Modifier = Modifier,
) {
    if (cues.isEmpty()) return
    val text = remember(cues, positionMs / 100) { findActiveSubtitleText(cues, positionMs) }
    if (text.isBlank()) return

    val bottomPad = when {
        inPip -> 6.dp
        controlsVisible -> 84.dp
        fullscreen -> 24.dp
        else -> 12.dp
    }
    val fontSize = when {
        inPip -> 11.sp
        fullscreen -> 16.sp
        else -> 13.sp
    }

    Box(
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, bottom = bottomPad),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xBF000000))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = fontSize,
            lineHeight = fontSize * 1.35f,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

private fun findActiveSubtitleText(cues: List<SubtitleCue>, positionMs: Long): String {
    var low = 0
    var high = cues.lastIndex
    var candidate = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (cues[mid].startMs <= positionMs) {
            candidate = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    if (candidate < 0) return ""
    // 相邻两条可能起止重叠，向前回看最多 2 条。
    val lines = mutableListOf<String>()
    for (i in (candidate - 2).coerceAtLeast(0)..candidate) {
        val cue = cues[i]
        if (positionMs in cue.startMs..cue.endMs) {
            lines.add(cue.content)
        }
    }
    return lines.joinToString("\n")
}

@Composable
internal fun SubtitleDialog(
    tracks: List<SubtitleTrack>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    inlineInStage: Boolean = false,
) {
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "CC 字幕",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SubtitleOptionRow(
                    label = "关闭字幕",
                    selected = selectedKey == null,
                    onClick = { onSelect(null) },
                )
                tracks.forEach { track ->
                    SubtitleOptionRow(
                        label = track.label,
                        selected = track.key == selectedKey,
                        onClick = { onSelect(track.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.body1,
            color = if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurface,
        )
    }
}
