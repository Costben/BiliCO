package erl.bilico.player

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.logDebug
import erl.bilico.platform.logWarn
import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTimer
import platform.Foundation.NSValue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.abs
import kotlin.math.max
import kotlin.native.ref.WeakReference

private const val TAG = "BiliPlayer"

@Stable
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalNativeApi::class)
actual class BiliPlayer actual constructor(context: PlatformContext) {
    internal val avPlayer: AVPlayer = AVPlayer().apply {
        automaticallyWaitsToMinimizeStalling = true
        audiovisualBackgroundPlaybackPolicy = AVPlayerAudiovisualBackgroundPlaybackPolicyContinuesIfPossible
        muted = false
        volume = 1f
    }
    internal var audioPlayer: AVPlayer? = null
    private var videoOutput: AVPlayerItemVideoOutput? = null
    private var hlsLoader: DashHlsResourceLoader? = null
    private var localHttpServer: LocalMediaHttpServer? = null
    private val hlsLoaderQueue = dispatch_queue_create("erl.bilico.dash.hls", null)
    private var probingDashHls = false
    private var released = false
    private var lastAudioSeekAtMs = 0L
    private var timeObserver: Any? = null
    private var endObserver: Any? = null
    private var failedObserver: Any? = null
    private var statusTimer: NSTimer? = null
    private val listeners = ArrayList<BiliPlayerListener>()

    private var desiredPlayWhenReady = false
    private var desiredSpeed = 1f
    private var desiredVolume = 1f
    private var pendingSeekMs = 0L
    // AVPlayerItem 的对象身份不可靠：同一个 item 每次取回来可能是不同的包装对象，
    // 直接比 item 会让「这个 item 已经报过 ready」永远为假，pollState 每 0.2s 就重新
    // 配置一次 AVAudioSession（真机上实测 ~8 次/秒），系统音量 HUD 会被反复顶掉/重弹。
    // 用换源代数去重：setMedia / 清空源各推一代，一代只报一次。
    private var mediaGeneration = 0
    private var readyReportedGeneration = -1
    private var startedReportedGeneration = -1
    private var audioReadyReportedGeneration = -1
    private var failedReportedGeneration = -1
    private var failedReportedForAudioGeneration = -1
    private var isLiveMedia = false
    private var mediaSetAtMs = 0L
    private var readyAtMs = 0L
    private var lastReportedState = PlayerStates.STATE_IDLE
    private var lastReportedPlaying = false
    private var lastReportedPlayIntent = false
    private var lastReportedWidth = 0
    private var lastReportedHeight = 0

    init {
        ensureAudioSession()
        val weakSelf = WeakReference(this)
        dispatch_async(dispatch_get_main_queue()) {
            val self = weakSelf.get() ?: return@dispatch_async
            if (self.released) return@dispatch_async
            val interval = CMTimeMake(value = 1, timescale = 5)
            self.timeObserver = self.avPlayer.addPeriodicTimeObserverForInterval(interval, queue = dispatch_get_main_queue()) { _ ->
                val current = weakSelf.get() ?: return@addPeriodicTimeObserverForInterval
                current.syncAudioIfNeeded()
                current.pollState()
            }
            self.statusTimer = NSTimer.scheduledTimerWithTimeInterval(interval = 0.2, repeats = true) { timer ->
                val current = weakSelf.get()
                if (current == null || current.released) {
                    timer?.invalidate()
                } else {
                    current.pollState()
                }
            }
        }
    }

    actual val currentPosition: Long
        get() {
            val secs = CMTimeGetSeconds(avPlayer.currentTime())
            return if (secs.isFinite() && secs >= 0.0) (secs * 1000.0).toLong() else 0L
        }

    actual val duration: Long
        get() {
            val item = avPlayer.currentItem ?: return 0L
            val secs = CMTimeGetSeconds(item.duration)
            return if (secs.isFinite() && secs > 0.0) (secs * 1000.0).toLong() else 0L
        }

    actual val bufferedPosition: Long
        get() {
            val item = avPlayer.currentItem ?: return currentPosition
            var maxEnd = currentPosition
            item.loadedTimeRanges.forEach { raw ->
                val value = raw as? NSValue ?: return@forEach
                val endSec = value.CMTimeRangeValue.useContents {
                    CMTimeGetSeconds(start.readValue()) + CMTimeGetSeconds(duration.readValue())
                }
                if (endSec.isFinite() && endSec > 0.0) maxEnd = max(maxEnd, (endSec * 1000.0).toLong())
            }
            return maxEnd
        }

    actual val isPlaying: Boolean
        get() = avPlayer.timeControlStatus == AVPlayerTimeControlStatusPlaying || avPlayer.rate > 0f

    actual val playWhenReady: Boolean
        get() = desiredPlayWhenReady

    actual val playbackState: Int
        get() {
            if (probingDashHls) return PlayerStates.STATE_BUFFERING
            val item = avPlayer.currentItem ?: return PlayerStates.STATE_IDLE
            if (item.status == AVPlayerItemStatusFailed) return PlayerStates.STATE_IDLE
            if (lastReportedState == PlayerStates.STATE_ENDED) return PlayerStates.STATE_ENDED
            if (item.status != AVPlayerItemStatusReadyToPlay) return PlayerStates.STATE_BUFFERING
            if (avPlayer.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate && desiredPlayWhenReady) {
                return PlayerStates.STATE_BUFFERING
            }
            return PlayerStates.STATE_READY
        }

    actual val speed: Float
        get() = desiredSpeed

    actual var volume: Float
        get() = desiredVolume
        set(value) {
            desiredVolume = value.coerceIn(0f, 1f)
            applyVolume(avPlayer)
            audioPlayer?.let(::applyVolume)
        }

    actual val videoSize: VideoDimensions
        get() {
            val item = avPlayer.currentItem ?: return VideoDimensions()
            return item.presentationSize.useContents { VideoDimensions(width.toInt(), height.toInt()) }
        }

    actual fun setMedia(
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>,
        startPositionMs: Long,
        playWhenReady: Boolean,
        isLive: Boolean,
    ) {
        desiredPlayWhenReady = playWhenReady
        pendingSeekMs = if (startPositionMs > 0L && !isLive) startPositionMs else 0L
        mediaGeneration++
        isLiveMedia = isLive
        mediaSetAtMs = currentTimeMillis()
        readyAtMs = 0L
        audioReadyReportedGeneration = -1
        lastReportedState = PlayerStates.STATE_BUFFERING
        notifyPlayIntentIfChanged()

        val generation = mediaGeneration
        val weakSelf = WeakReference(this)
        dispatch_async(dispatch_get_main_queue()) {
            val self = weakSelf.get() ?: return@dispatch_async
            if (self.released || generation != self.mediaGeneration) return@dispatch_async
            self.ensureAudioSession()
            self.resetActiveMedia()

            val nsUrl = resolvePlayerNsUrl(videoUrl) ?: run {
                self.notifyError("无效的视频地址")
                return@dispatch_async
            }
            val isLocalVideo = videoUrl.startsWith("file://", ignoreCase = true)
            val lowerVideoPath = videoUrl.substringBefore('?').lowercase()
            val shouldProbeDashHls = !isLive &&
                !lowerVideoPath.endsWith(".m3u8") &&
                !lowerVideoPath.endsWith(".flv") &&
                (!isLocalVideo || !audioUrl.isNullOrBlank() || lowerVideoPath.endsWith(".m4s"))

            logDebug(
                TAG,
                "setMedia gen=$generation host=${nsUrl.host ?: (if (isLocalVideo) "local" else "?")} hasAudio=${!audioUrl.isNullOrBlank()} probeHls=$shouldProbeDashHls",
            )
            if (!shouldProbeDashHls) {
                self.probingDashHls = false
                self.attachDirectMedia(generation, videoUrl, audioUrl, headers, playWhenReady)
                return@dispatch_async
            }

            self.probingDashHls = true
            // B 站 DASH .m4s 为 Fragmented MP4（ftyp+moov+sidx+moof/mdat）。
            // 先并行探测头部 32KB 解析 sidx，封装为内存虚拟 HLS v7 fMP4 播放列表，
            // 让单实例 AVPlayer 仅按需请求首段 ByteRange，实现秒开、硬件级音画同步与拖拽。
            val probeStartMs = currentTimeMillis()
            probeDashHlsAsync(videoUrl, audioUrl, headers, self.hlsLoaderQueue) { outcome ->
                val current = weakSelf.get() ?: return@probeDashHlsAsync
                if (current.released || generation != current.mediaGeneration) return@probeDashHlsAsync
                current.probingDashHls = false
                val probeMs = currentTimeMillis() - probeStartMs
                when (outcome) {
                    is DashHlsProbeOutcome.Ready -> {
                        logDebug(
                            TAG,
                            "hlsReady gen=$generation probeMs=${probeMs}ms vSegs=${outcome.videoIndex.segments.size} aSegs=${outcome.audioIndex?.segments?.size ?: 0}",
                        )
                        current.attachVirtualHlsMedia(
                            generation, videoUrl, outcome.videoIndex, audioUrl, outcome.audioIndex, headers, playWhenReady,
                        )
                    }
                    is DashHlsProbeOutcome.Failed -> {
                        if (current.failedReportedGeneration != generation) {
                            current.failedReportedGeneration = generation
                            current.notifyError(outcome.message)
                        }
                    }
                    DashHlsProbeOutcome.FallbackDirect -> {
                        logDebug(TAG, "hlsFallbackDirect gen=$generation probeMs=${probeMs}ms")
                        current.attachDirectMedia(generation, videoUrl, audioUrl, headers, playWhenReady)
                    }
                }
            }
        }
    }

    private fun resetActiveMedia() {
        detachObservers()
        videoOutput = null
        hlsLoader = null
        localHttpServer?.stop()
        localHttpServer = null
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        clearAudioPlayer()
    }

    private fun clearAudioPlayer() { audioPlayer?.pause(); audioPlayer?.replaceCurrentItemWithPlayerItem(null) }

    private fun detachObservers() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }; endObserver = null
        failedObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }; failedObserver = null
    }

    private fun attachItemObservers(item: AVPlayerItem, generation: Int) {
        detachObservers()
        val weakSelf = WeakReference(this)
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification, item, NSOperationQueue.mainQueue,
        ) { _ ->
            val self = weakSelf.get() ?: return@addObserverForName
            if (generation != self.mediaGeneration) return@addObserverForName
            self.desiredPlayWhenReady = false
            self.lastReportedState = PlayerStates.STATE_ENDED
            self.listeners.toList().forEach { it.onPlaybackStateChanged(PlayerStates.STATE_ENDED) }
        }
        failedObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemFailedToPlayToEndTimeNotification, item, NSOperationQueue.mainQueue,
        ) { _ ->
            val self = weakSelf.get() ?: return@addObserverForName
            if (generation != self.mediaGeneration) return@addObserverForName
            if (self.failedReportedGeneration != generation) {
                self.failedReportedGeneration = generation
                self.notifyError(item.error?.localizedDescription ?: "视频播放失败")
            }
        }
    }

    private fun installPrimaryItem(
        item: AVPlayerItem,
        generation: Int,
        playWhenReady: Boolean,
        controlAudioPlayer: Boolean,
    ) {
        val output = createBiliPlayerVideoOutput()
        item.addOutput(output)
        videoOutput = output
        applyVolume(avPlayer)
        avPlayer.replaceCurrentItemWithPlayerItem(item)
        attachItemObservers(item, generation)
        if (playWhenReady) {
            avPlayer.play()
            avPlayer.rate = desiredSpeed
            if (controlAudioPlayer) {
                audioPlayer?.play()
                audioPlayer?.rate = desiredSpeed
            }
        } else {
            avPlayer.pause()
            if (controlAudioPlayer) audioPlayer?.pause()
        }
        pollState()
    }

    private fun attachVirtualHlsMedia(
        generation: Int,
        videoUrl: String,
        videoIndex: Fmp4SidxIndex,
        audioUrl: String?,
        audioIndex: Fmp4SidxIndex?,
        headers: Map<String, String>,
        playWhenReady: Boolean,
    ) {
        if (generation != mediaGeneration) return
        val playback = buildVirtualHlsPlayback(
            generation, videoUrl, videoIndex, audioUrl, audioIndex, headers, hlsLoaderQueue,
        ) ?: run {
            notifyError("无法创建播放列表地址")
            return
        }
        hlsLoader = playback.loader
        localHttpServer = playback.localServer
        clearAudioPlayer()
        installPrimaryItem(playback.item, generation, playWhenReady, controlAudioPlayer = false)
    }

    private fun obtainAudioPlayer(): AVPlayer = audioPlayer ?: AVPlayer().also {
        it.automaticallyWaitsToMinimizeStalling = true
        it.audiovisualBackgroundPlaybackPolicy = avPlayer.audiovisualBackgroundPlaybackPolicy
        audioPlayer = it
    }

    private fun attachDirectMedia(
        generation: Int,
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>,
        playWhenReady: Boolean,
    ) {
        if (generation != mediaGeneration) return
        val localDirect = buildLocalDirectPlayback(videoUrl, audioUrl)
        if (localDirect != null) {
            localHttpServer = localDirect.localServer
            if (localDirect.audioItem != null) {
                val ap = obtainAudioPlayer()
                applyVolume(ap)
                ap.replaceCurrentItemWithPlayerItem(localDirect.audioItem)
            } else {
                clearAudioPlayer()
            }
            installPrimaryItem(localDirect.videoItem, generation, playWhenReady, controlAudioPlayer = localDirect.audioItem != null)
            return
        }
        val item = buildDirectPlayerItem(videoUrl, isAudio = false, headers = headers) ?: run {
            notifyError("无效的视频地址")
            return
        }
        if (!audioUrl.isNullOrBlank()) {
            buildDirectPlayerItem(audioUrl, isAudio = true, headers = headers)?.let { aItem ->
                val ap = obtainAudioPlayer()
                applyVolume(ap)
                ap.replaceCurrentItemWithPlayerItem(aItem)
            }
        } else {
            clearAudioPlayer()
        }
        installPrimaryItem(item, generation, playWhenReady, controlAudioPlayer = true)
    }

    actual fun clearMediaItems() {
        desiredPlayWhenReady = false
        pendingSeekMs = 0L
        probingDashHls = false
        mediaGeneration++
        lastReportedState = PlayerStates.STATE_IDLE
        dispatch_async(dispatch_get_main_queue()) {
            resetActiveMedia()
            pollState()
        }
    }

    actual fun stop() = clearMediaItems()

    actual fun play() {
        desiredPlayWhenReady = true
        if (lastReportedState == PlayerStates.STATE_ENDED) lastReportedState = PlayerStates.STATE_READY
        notifyPlayIntentIfChanged()
        dispatch_async(dispatch_get_main_queue()) {
            ensureAudioSession()
            applyVolume(avPlayer)
            avPlayer.play()
            avPlayer.rate = desiredSpeed
            audioPlayer?.let { ap ->
                applyVolume(ap)
                ap.play()
                ap.rate = desiredSpeed
            }
            pollState()
        }
    }

    actual fun pause() {
        desiredPlayWhenReady = false
        notifyPlayIntentIfChanged()
        dispatch_async(dispatch_get_main_queue()) {
            avPlayer.pause()
            audioPlayer?.pause()
            pollState()
        }
    }

    actual fun seekTo(positionMs: Long) {
        val target = CMTimeMakeWithSeconds(positionMs.coerceAtLeast(0L) / 1000.0, preferredTimescale = 1000)
        dispatch_async(dispatch_get_main_queue()) {
            avPlayer.seekToTime(target)
            audioPlayer?.seekToTime(target)
            IosMediaSession.refreshNowPlaying()
        }
    }

    actual fun setPlaybackSpeed(speed: Float) {
        desiredSpeed = speed.coerceIn(0.25f, 4f)
        dispatch_async(dispatch_get_main_queue()) {
            if (desiredPlayWhenReady) {
                if (avPlayer.rate != desiredSpeed) avPlayer.rate = desiredSpeed
                audioPlayer?.let { ap -> if (ap.rate != desiredSpeed) ap.rate = desiredSpeed }
            }
        }
    }

    actual fun addListener(listener: BiliPlayerListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    actual fun removeListener(listener: BiliPlayerListener) {
        listeners.remove(listener)
    }

    actual fun captureCurrentFrame(): ImageBitmap? = captureBiliPlayerFrame(avPlayer, videoOutput)

    actual fun release() {
        released = true
        dispatch_async(dispatch_get_main_queue()) {
            statusTimer?.invalidate()
            statusTimer = null
            timeObserver?.let { avPlayer.removeTimeObserver(it) }
            timeObserver = null
            resetActiveMedia()
            audioPlayer = null
            listeners.clear()
        }
    }

    private fun applyVolume(target: AVPlayer) {
        target.muted = desiredVolume <= 0f
        target.volume = desiredVolume
    }

    private fun notifyPlayIntentIfChanged() {
        val playIntentNow = hasPlayIntent
        if (playIntentNow != lastReportedPlayIntent) {
            lastReportedPlayIntent = playIntentNow
            listeners.toList().forEach { it.onPlayIntentChanged(playIntentNow) }
        }
    }

    private fun syncAudioIfNeeded() {
        val ap = audioPlayer ?: return
        val aItem = ap.currentItem ?: return
        if (aItem.status != AVPlayerItemStatusReadyToPlay) return
        applyVolume(ap)
        val vSecs = CMTimeGetSeconds(avPlayer.currentTime())
        val aSecs = CMTimeGetSeconds(ap.currentTime())
        val nowMs = currentTimeMillis()
        if (vSecs.isFinite() && aSecs.isFinite() && abs(vSecs - aSecs) > 0.45 && nowMs - lastAudioSeekAtMs > 1_500L) {
            lastAudioSeekAtMs = nowMs
            ap.seekToTime(CMTimeMakeWithSeconds(vSecs, preferredTimescale = 1000))
        }
        if (isPlaying && ap.rate == 0f) {
            ap.play()
            ap.rate = desiredSpeed
        } else if (!isPlaying && ap.rate > 0f) {
            ap.pause()
        }
    }

    private fun pollState() {
        val nowMs = currentTimeMillis()
        val item = avPlayer.currentItem
        if (item?.status == AVPlayerItemStatusFailed && failedReportedGeneration != mediaGeneration) {
            failedReportedGeneration = mediaGeneration
            notifyError(item.error?.localizedDescription ?: "视频加载失败")
        }
        val audioItem = audioPlayer?.currentItem
        if (audioItem?.status == AVPlayerItemStatusFailed && failedReportedForAudioGeneration != mediaGeneration) {
            failedReportedForAudioGeneration = mediaGeneration
            val err = audioItem.error?.localizedDescription ?: "音频流加载失败"
            logWarn(TAG, "audioItemFailed err=$err")
            notifyError(err)
        }
        if (audioItem?.status == AVPlayerItemStatusReadyToPlay && audioReadyReportedGeneration != mediaGeneration) {
            audioReadyReportedGeneration = mediaGeneration
            audioPlayer?.let { ap ->
                applyVolume(ap)
                val vSecs = CMTimeGetSeconds(avPlayer.currentTime())
                if (vSecs.isFinite() && vSecs > 0.1) ap.seekToTime(CMTimeMakeWithSeconds(vSecs, preferredTimescale = 1000))
                if (desiredPlayWhenReady && lastReportedState != PlayerStates.STATE_ENDED) {
                    ap.play()
                    ap.rate = desiredSpeed
                }
            }
        }
        // 起播超时看门狗：部分 B 站镜像节点会长时间停在 Unknown 不报错，8.5s 未就绪自动切备用线路/编码。
        if (!isLiveMedia && item != null && mediaSetAtMs > 0L && failedReportedGeneration != mediaGeneration &&
            nowMs - mediaSetAtMs > 8_500L &&
            (item.status == AVPlayerItemStatusUnknown || audioItem?.status == AVPlayerItemStatusUnknown)
        ) {
            failedReportedGeneration = mediaGeneration
            notifyError("媒体流加载超时")
        }
        if (item?.status == AVPlayerItemStatusReadyToPlay) {
            if (readyAtMs == 0L) readyAtMs = nowMs
            if (readyReportedGeneration != mediaGeneration) {
                readyReportedGeneration = mediaGeneration
                ensureAudioSession()
                val session = AVAudioSession.sharedInstance()
                val trackDesc = item.tracks.joinToString(",") { raw ->
                    val tr = raw as? AVPlayerItemTrack ?: return@joinToString "?"
                    if (!tr.enabled) tr.enabled = true
                    "${tr.assetTrack?.mediaType ?: "?"}:${tr.enabled}"
                }
                val outputs = session.currentRoute.outputs.joinToString(",") { raw ->
                    (raw as? AVAudioSessionPortDescription)?.portType ?: "?"
                }
                logDebug(
                    TAG,
                    "ready tracks=[$trackDesc] vol=${avPlayer.volume} muted=${avPlayer.muted} outVol=${session.outputVolume} cat=${session.category ?: "?"} mode=${session.mode ?: "?"} out=[$outputs]",
                )
            }
            if (pendingSeekMs > 0L) {
                val target = CMTimeMakeWithSeconds(pendingSeekMs / 1000.0, preferredTimescale = 1000)
                pendingSeekMs = 0L
                avPlayer.seekToTime(target)
                audioPlayer?.seekToTime(target)
            }
            if (desiredPlayWhenReady && avPlayer.rate == 0f && lastReportedState != PlayerStates.STATE_ENDED) {
                if (startedReportedGeneration != mediaGeneration) {
                    startedReportedGeneration = mediaGeneration
                    avPlayer.play()
                    avPlayer.rate = desiredSpeed
                    if (audioItem?.status == AVPlayerItemStatusReadyToPlay) {
                        audioPlayer?.play()
                        audioPlayer?.rate = desiredSpeed
                    }
                } else if (avPlayer.timeControlStatus == AVPlayerTimeControlStatusPaused && !IosMediaSession.isTransitioningToBackground()) {
                    // PiP 浮窗、控制中心或耳机断开由系统暂停时同步期望态，避免被轮询强行恢复播放。
                    desiredPlayWhenReady = false
                }
            } else if (avPlayer.rate > 0f) {
                startedReportedGeneration = mediaGeneration
                if (!desiredPlayWhenReady) desiredPlayWhenReady = true
            }
            // 黑屏看门狗：ReadyToPlay 后超过 3s 仍无有效视频分辨率（如 AV1 不可解码），自动换线/换编码。
            if (!isLiveMedia && readyAtMs > 0L && nowMs - readyAtMs > 3_000L &&
                videoSize.width <= 0 && failedReportedGeneration != mediaGeneration
            ) {
                failedReportedGeneration = mediaGeneration
                notifyError("视频画面解码异常")
            }
        }
        val state = playbackState
        if (state != lastReportedState) {
            lastReportedState = state
            listeners.toList().forEach { it.onPlaybackStateChanged(state) }
        }
        val playing = isPlaying
        if (playing != lastReportedPlaying) {
            lastReportedPlaying = playing
            listeners.toList().forEach { it.onIsPlayingChanged(playing) }
            IosMediaSession.refreshNowPlaying()
        }
        notifyPlayIntentIfChanged()
        val dims = videoSize
        if (dims.width > 0 && dims.height > 0 && (dims.width != lastReportedWidth || dims.height != lastReportedHeight)) {
            lastReportedWidth = dims.width
            lastReportedHeight = dims.height
            listeners.toList().forEach { it.onVideoSizeChanged(dims.width, dims.height) }
        }
    }

    private fun ensureAudioSession() = IosMediaSession.ensurePlaybackAudioSession()

    private fun notifyError(msg: String) { logWarn(TAG, "error msg=$msg"); listeners.toList().forEach { it.onPlayerError(msg) } }
}
