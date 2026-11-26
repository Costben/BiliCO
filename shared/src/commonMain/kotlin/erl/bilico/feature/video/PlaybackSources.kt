package erl.bilico.feature.video

import erl.bilico.data.model.PlaybackSource
import erl.bilico.platform.currentTimeMillis
import erl.bilico.player.BiliPlayer

/** 一次播放尝试：视频轨 + 可选的音频轨。换线时整对一起换，避免音画来自不同线路。 */
internal data class PlaybackAttempt(
    val videoUrl: String,
    val audioUrl: String?,
)

internal fun PlaybackSource.streamAttempts(): List<PlaybackAttempt> {
    val videos = (listOf(videoUrl) + videoBackupUrls).filter { it.isNotBlank() }
    val audios = (listOfNotNull(audioUrl) + audioBackupUrls).filter { it.isNotBlank() }
    if (videos.isEmpty()) return emptyList()
    val count = maxOf(videos.size, audios.size)
    return List(count) { index ->
        PlaybackAttempt(
            videoUrl = videos.getOrElse(index) { videos.last() },
            audioUrl = audios.getOrNull(index) ?: audios.lastOrNull(),
        )
    }
}

internal fun playbackMediaId(bvid: String, cid: Long, videoUrl: String): String =
    "$bvid/$cid/${videoUrl.hashCode()}"

internal fun BiliPlayer.loadAttempt(
    headers: Map<String, String>,
    attempt: PlaybackAttempt,
    speed: Float,
    seekMs: Long,
) {
    setPlaybackSpeed(speed)
    setMedia(
        videoUrl = attempt.videoUrl,
        audioUrl = attempt.audioUrl,
        headers = headers,
        startPositionMs = seekMs,
        playWhenReady = true,
        isLive = false,
    )
}

internal object PlaybackPositions {
    private const val CAPACITY = 32
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    private data class Mark(val positionMs: Long, val at: Long)

    private val marks = LinkedHashMap<String, Mark>(CAPACITY)

    fun remember(mediaId: String, positionMs: Long) {
        if (mediaId.isEmpty() || positionMs <= 0L) return
        marks.remove(mediaId)
        marks[mediaId] = Mark(positionMs, currentTimeMillis())
        while (marks.size > CAPACITY) {
            val first = marks.keys.firstOrNull() ?: break
            marks.remove(first)
        }
    }

    fun last(mediaId: String): Long {
        val mark = marks[mediaId] ?: return 0L
        return mark.positionMs.takeIf { currentTimeMillis() - mark.at < MAX_AGE_MS } ?: 0L
    }

    fun forget(mediaId: String) {
        marks.remove(mediaId)
    }
}

internal class PlaybackAttempts(
    private val player: BiliPlayer,
    private val speed: () -> Float,
) {
    private var attempts: List<PlaybackAttempt> = emptyList()
    private var headers: Map<String, String> = emptyMap()
    private var index = 0
    var currentMediaId: String = ""
        private set

    val count: Int get() = attempts.size

    fun start(
        source: PlaybackSource,
        headers: Map<String, String>,
        seekMs: Long,
        mediaId: String = "",
    ) {
        this.headers = headers
        this.currentMediaId = mediaId
        attempts = source.streamAttempts()
        index = 0
        val first = attempts.firstOrNull() ?: return
        val from = if (seekMs > 0L) seekMs else PlaybackPositions.last(mediaId)
        player.loadAttempt(headers, first, speed(), from)
    }

    fun rememberPosition(positionMs: Long) {
        PlaybackPositions.remember(currentMediaId, positionMs)
    }

    fun adopt(source: PlaybackSource, headers: Map<String, String>, mediaId: String) {
        this.headers = headers
        this.currentMediaId = mediaId
        attempts = source.streamAttempts()
        index = 0
    }

    fun next(): Boolean {
        val next = index + 1
        if (next >= attempts.size) return false
        index = next
        player.loadAttempt(headers, attempts[next], speed(), player.currentPosition)
        return true
    }
}

internal fun describePlaybackError(error: String): String =
    if (error.startsWith("播放失败")) error else "播放失败：$error"
