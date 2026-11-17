package erl.bilico.feature.video

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import erl.bilico.data.model.PlaybackSource
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 一次播放尝试：视频轨 + 可选的音频轨。换线时整对一起换，避免音画来自不同线路。 */
internal data class PlaybackAttempt(
    val videoUrl: String,
    val audioUrl: String?,
)

/**
 * 把 [PlaybackSource] 里排好序的候选地址摊成尝试表。
 * 视频候选多于音频时复用最后一条音频，反之亦然。
 */
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

/** 同一份片源在播放器上的标识：片源没变但页面被重建时，靠它跳过重复装载。 */
internal fun playbackMediaId(bvid: String, cid: Long, videoUrl: String): String =
    "$bvid/$cid/${videoUrl.hashCode()}"

internal fun mediaSourceFor(
    factory: OkHttpDataSource.Factory,
    attempt: PlaybackAttempt,
    mediaId: String,
): MediaSource {
    val video = ProgressiveMediaSource.Factory(factory)
        .createMediaSource(MediaItem.Builder().setUri(attempt.videoUrl).setMediaId(mediaId).build())
    val audioUrl = attempt.audioUrl
    if (audioUrl.isNullOrBlank()) return video
    return MergingMediaSource(
        video,
        ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(audioUrl)),
    )
}

internal fun ExoPlayer.loadAttempt(
    factory: OkHttpDataSource.Factory,
    attempt: PlaybackAttempt,
    speed: Float,
    seekMs: Long,
    mediaId: String,
) {
    setMediaSource(mediaSourceFor(factory, attempt, mediaId))
    setPlaybackSpeed(speed)
    prepare()
    if (seekMs > 0L) seekTo(seekMs)
    playWhenReady = true
}

/**
 * 本次运行里每条片源播到哪儿，按 mediaId 记。
 *
 * 播放页被重建（最小化复原、点媒体条封面重建 Activity、播放器被换掉）时，
 * 片源会重新装载一次；没有这份记忆就只能从 0 开始，用户看到的就是「又从头播了」。
 * 只记当前进程、只留最近 [CAPACITY] 条、超过 [MAX_AGE_MS] 就作废，
 * 免得下次正常打开一条很久以前看过的视频时莫名其妙跳到末尾。
 */
internal object PlaybackPositions {

    private const val CAPACITY = 32
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    private data class Mark(val positionMs: Long, val at: Long)

    private val marks = object : LinkedHashMap<String, Mark>(CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Mark>): Boolean =
            size > CAPACITY
    }

    fun remember(mediaId: String, positionMs: Long) {
        if (mediaId.isEmpty()) return
        if (positionMs <= 0L) return
        marks[mediaId] = Mark(positionMs, System.currentTimeMillis())
    }

    fun last(mediaId: String): Long {
        val mark = marks[mediaId] ?: return 0L
        return mark.positionMs.takeIf { System.currentTimeMillis() - mark.at < MAX_AGE_MS } ?: 0L
    }

    fun forget(mediaId: String) {
        marks.remove(mediaId)
    }
}

/**
 * 播放失败时按候选表就地换线。PCDN 节点会拒绝连接或迟迟不响应，
 * 换一条常规 CDN 通常立刻恢复，不用重新请求 playurl。
 */
internal class PlaybackAttempts(
    private val player: ExoPlayer,
    private val speed: () -> Float,
) {
    private var attempts: List<PlaybackAttempt> = emptyList()
    private var factory: OkHttpDataSource.Factory? = null
    private var index = 0
    private var mediaId: String = ""

    val count: Int get() = attempts.size

    fun start(
        source: PlaybackSource,
        factory: OkHttpDataSource.Factory,
        seekMs: Long,
        mediaId: String,
    ) {
        this.factory = factory
        this.mediaId = mediaId
        attempts = source.streamAttempts()
        index = 0
        val first = attempts.firstOrNull() ?: return
        // 没有显式续播点就接着本次运行里播到的位置放：播放页被重建、播放器被换掉那几种情况
        // 都会走到这里，不兜这一层就又从 0 开始演一遍已经看过的内容。
        val from = if (seekMs > 0L) seekMs else PlaybackPositions.last(mediaId)
        player.loadAttempt(factory, first, speed(), from, mediaId)
    }

    /** 把当前播放位置记下来，供 [start] 在重建后接上。 */
    fun rememberPosition(positionMs: Long) {
        PlaybackPositions.remember(mediaId, positionMs)
    }

    /**
     * 播放器上已经装着同一份片源：只把候选表登记回来供换线用，不重新装载。
     * 缩进融合球再复原会把播放页重建一次，照着 [start] 再装一遍会从 0 重新播。
     */
    fun adopt(source: PlaybackSource, factory: OkHttpDataSource.Factory, mediaId: String) {
        this.factory = factory
        this.mediaId = mediaId
        attempts = source.streamAttempts()
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        index = attempts.indexOfFirst { it.videoUrl == currentUri }.coerceAtLeast(0)
    }

    /** 还有下一条可用线路就切过去，返回是否切换成功。 */
    fun next(): Boolean {
        val factory = factory ?: return false
        val next = index + 1
        if (next >= attempts.size) return false
        index = next
        player.loadAttempt(factory, attempts[next], speed(), player.currentPosition, mediaId)
        return true
    }
}

/** 把 ExoPlayer 的「Source error」翻成能定位问题的原因。 */
internal fun describePlaybackError(error: PlaybackException): String {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            val host = cause.dataSpec.uri.host.orEmpty()
            return "播放失败：服务器返回 ${cause.responseCode}" + hostSuffix(host)
        }
        if (cause is HttpDataSource.HttpDataSourceException) {
            val host = cause.dataSpec.uri.host.orEmpty()
            val reason = when (val inner = cause.cause) {
                is ConnectException -> "连接被拒绝"
                is UnknownHostException -> "域名解析失败"
                is SocketTimeoutException -> "连接超时"
                null -> "读取失败"
                else -> inner.javaClass.simpleName
            }
            return "播放失败：$reason" + hostSuffix(host)
        }
        cause = cause.cause
    }
    return "播放失败：${error.errorCodeName}"
}

private fun hostSuffix(host: String): String = if (host.isBlank()) "" else "（$host）"
