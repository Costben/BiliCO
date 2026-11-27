package erl.bilico.player

import erl.bilico.data.network.BILI_USER_AGENT
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.dlFileUriToPath
import erl.bilico.platform.logDebug
import erl.bilico.platform.logWarn
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.Foundation.*
import platform.MediaPlayer.*
import platform.UIKit.*
import platform.darwin.*

/**
 * iOS 系统媒体会话与画中画管理器：
 * 1. 通过 [MPNowPlayingInfoCenter] + [MPRemoteCommandCenter] 接入原生灵动岛媒体胶囊、锁屏与控制中心播控；
 * 2. 通过 [AVPictureInPictureController] 绑定当前活跃 [AVPlayerLayer]，支持手动进入与退后台自动画中画；
 * 3. 遵循 iOS 原生协同规范：进入画中画浮窗时由系统接管浮窗并隐藏灵动岛胶囊，纯后台听音（关闭自动 PiP 或黑听）
 *    时自动解绑视频层维持后台音频并展示灵动岛胶囊与封面声波。
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosMediaSession {
    private const val TAG = "IosMediaSession"

    private var installed = false
    private var sessionActive = false
    private var audioSessionActive = false
    private var activePlayer: BiliPlayer? = null
    private var activeLayer: AVPlayerLayer? = null
    private var detachedLayerForBg: AVPlayerLayer? = null
    private var wasPlayingBeforeBackground = false
    private var transitioningToBackground = false
    private var pipController: AVPictureInPictureController? = null
    private var pipDelegate: PipDelegate? = null
    private var pipActive = false
    private var pipStarting = false
    private var autoPipAllowed = false

    private var cachedArtworkUrl: String = ""
    private var cachedArtwork: MPMediaItemArtwork? = null
    private var loadingArtworkUrl: String = ""
    private var refreshTimer: NSTimer? = null

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
    }

    fun install() {
        if (installed) return
        installed = true
        PlaybackBridge.onSessionStart = { onSessionStart() }
        PlaybackBridge.onSessionPromote = { onSessionPromote() }
        PlaybackBridge.onSessionRefresh = { refreshNowPlaying() }
        PlaybackBridge.onSessionStop = { onSessionStop() }
        PlaybackBridge.isPlatformPipActive = { pipActive || pipStarting }

        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.beginReceivingRemoteControlEvents()
        }
        setupRemoteCommands()
        observeAppLifecycle()
    }

    fun isPipSupported(): Boolean = AVPictureInPictureController.isPictureInPictureSupported()

    fun isPipActiveOrStarting(): Boolean = pipActive || pipStarting

    fun isTransitioningToBackground(): Boolean = transitioningToBackground

    fun attachPlayerLayer(player: BiliPlayer, layer: AVPlayerLayer) {
        install()
        runOnMain {
            if (activeLayer === layer && activePlayer === player) {
                syncAutoPipFlag()
                return@runOnMain
            }
            if (detachedLayerForBg === layer) {
                layer.player = player.avPlayer
                detachedLayerForBg = null
            }
            activePlayer = player
            activeLayer = layer
            destroyPipController()
            syncAutoPipFlag()
        }
    }

    fun detachPlayerLayer(layer: AVPlayerLayer) {
        runOnMain {
            if (activeLayer !== layer) return@runOnMain
            if (pipActive || pipStarting) return@runOnMain
            if (detachedLayerForBg === layer) detachedLayerForBg = null
            destroyPipController()
            if (layer.player != null) layer.player = null
            activeLayer = null
            activePlayer = null
        }
    }

    fun updatePipParams(activePlayback: Boolean, isPlaying: Boolean, autoPip: Boolean) {
        install()
        runOnMain {
            autoPipAllowed = activePlayback && isPlaying && autoPip
            syncAutoPipFlag()
            if (sessionActive) refreshNowPlayingOnMain()
        }
    }

    fun startPipNow(): Boolean {
        install()
        if (!isPipSupported()) return false
        val layer = activeLayer ?: return false
        val controller = pipController ?: rebuildPipController(layer) ?: return false
        if (controller.isPictureInPictureActive()) return true
        ensurePlaybackAudioSession()
        pipStarting = true
        if (controller.isPictureInPicturePossible()) {
            controller.startPictureInPicture()
        } else {
            attemptManualPipStart(controller, retriesLeft = 12)
        }
        return true
    }

    private fun attemptManualPipStart(controller: AVPictureInPictureController, retriesLeft: Int) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 50_000_000L), dispatch_get_main_queue()) {
            if (pipController !== controller || !pipStarting) return@dispatch_after
            if (controller.isPictureInPictureActive()) return@dispatch_after
            if (controller.isPictureInPicturePossible()) {
                controller.startPictureInPicture()
            } else if (retriesLeft > 0) {
                attemptManualPipStart(controller, retriesLeft - 1)
            } else {
                pipStarting = false
                logWarn(TAG, "startPipNow timeout: isPictureInPicturePossible=false")
                syncAutoPipFlag()
            }
        }
    }

    fun refreshNowPlaying() {
        if (!installed) return
        dispatch_async(dispatch_get_main_queue()) {
            if (sessionActive) refreshNowPlayingOnMain()
        }
    }

    private fun onSessionStart() {
        runOnMain {
            sessionActive = true
            UIApplication.sharedApplication.beginReceivingRemoteControlEvents()
            PlaybackBridge.activePlayer?.setBackgroundPlaybackAllowed(PlaybackBridge.backgroundPlayEnabled)
            syncAutoPipFlag()
            ensurePlaybackAudioSession()
            startRefreshTimer()
            refreshNowPlayingOnMain()
        }
    }

    private fun onSessionPromote() = onSessionStart()

    private fun onSessionStop() {
        runOnMain {
            sessionActive = false
            wasPlayingBeforeBackground = false
            stopRefreshTimer()
            val center = MPNowPlayingInfoCenter.defaultCenter()
            center.playbackState = MPNowPlayingPlaybackStateStopped
            center.nowPlayingInfo = null
        }
    }

    private fun destroyPipController() {
        pipController?.canStartPictureInPictureAutomaticallyFromInline = false
        pipController?.delegate = null
        pipController = null
        pipDelegate = null
    }

    private fun rebuildPipController(layer: AVPlayerLayer): AVPictureInPictureController? {
        if (!isPipSupported()) return null
        destroyPipController()
        val controller = AVPictureInPictureController(playerLayer = layer)
        val delegate = PipDelegate()
        controller.delegate = delegate
        controller.canStartPictureInPictureAutomaticallyFromInline = autoPipAllowed && PlaybackBridge.autoPipEnabled
        pipDelegate = delegate
        pipController = controller
        return controller
    }

    // 关闭「自动画中画」时不常驻 AVPictureInPictureController，彻底杜绝 iOS 系统上滑回桌面时自动抢占进画中画；
    // 用户点击手动画中画按钮时再按需创建。
    private fun syncAutoPipFlag() {
        val allowAuto = autoPipAllowed && PlaybackBridge.autoPipEnabled && activeLayer != null
        if (!allowAuto && !pipActive && !pipStarting) {
            destroyPipController()
            return
        }
        val layer = activeLayer ?: return
        val controller = pipController ?: rebuildPipController(layer) ?: return
        controller.canStartPictureInPictureAutomaticallyFromInline = allowAuto
    }

    private fun setupRemoteCommands() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.playCommand.enabled = true
        center.playCommand.addTargetWithHandler { _ ->
            val p = PlaybackBridge.activePlayer ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            ensurePlaybackAudioSession()
            p.play()
            PlaybackBridge.refreshSession(PlatformContext())
            MPRemoteCommandHandlerStatusSuccess
        }
        center.pauseCommand.enabled = true
        center.pauseCommand.addTargetWithHandler { _ ->
            val p = PlaybackBridge.activePlayer ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            p.pause()
            PlaybackBridge.refreshSession(PlatformContext())
            MPRemoteCommandHandlerStatusSuccess
        }
        center.togglePlayPauseCommand.enabled = true
        center.togglePlayPauseCommand.addTargetWithHandler { _ ->
            if (PlaybackBridge.activePlayer == null) return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            ensurePlaybackAudioSession()
            PlaybackBridge.togglePlayPause(PlatformContext())
            MPRemoteCommandHandlerStatusSuccess
        }
        center.changePlaybackPositionCommand.enabled = true
        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val p = PlaybackBridge.activePlayer ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            val posEvent = event as? MPChangePlaybackPositionCommandEvent
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            val targetMs = (posEvent.positionTime * 1000.0).toLong().coerceAtLeast(0L)
            p.seekTo(targetMs)
            refreshNowPlaying()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.skipForwardCommand.enabled = true
        center.skipForwardCommand.preferredIntervals = listOf(NSNumber(double = 15.0))
        center.skipForwardCommand.addTargetWithHandler { event ->
            val p = PlaybackBridge.activePlayer ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            val delta = ((event as? MPSkipIntervalCommandEvent)?.interval ?: 15.0) * 1000.0
            val dur = p.duration
            val next = (p.currentPosition + delta.toLong()).let { if (dur > 0L) it.coerceAtMost(dur) else it }
            p.seekTo(next)
            refreshNowPlaying()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.skipBackwardCommand.enabled = true
        center.skipBackwardCommand.preferredIntervals = listOf(NSNumber(double = 15.0))
        center.skipBackwardCommand.addTargetWithHandler { event ->
            val p = PlaybackBridge.activePlayer ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            val delta = ((event as? MPSkipIntervalCommandEvent)?.interval ?: 15.0) * 1000.0
            p.seekTo((p.currentPosition - delta.toLong()).coerceAtLeast(0L))
            refreshNowPlaying()
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun observeAppLifecycle() {
        val nc = NSNotificationCenter.defaultCenter
        val q = NSOperationQueue.mainQueue
        // Scene 通知早于 Application 通知派发，抢在 AVPlayerLayer 内部退后台自动暂停之前先解绑图层。
        nc.addObserverForName(UISceneWillDeactivateNotification, null, q) { _ -> handleWillResignActive() }
        nc.addObserverForName(UIApplicationWillResignActiveNotification, null, q) { _ -> handleWillResignActive() }
        nc.addObserverForName(UISceneDidEnterBackgroundNotification, null, q) { _ -> handleDidEnterBackground() }
        nc.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, q) { _ -> handleDidEnterBackground() }
        nc.addObserverForName(UISceneWillEnterForegroundNotification, null, q) { _ -> handleWillEnterForeground() }
        nc.addObserverForName(UIApplicationWillEnterForegroundNotification, null, q) { _ -> handleWillEnterForeground() }
        nc.addObserverForName(UISceneDidActivateNotification, null, q) { _ -> handleWillEnterForeground() }
        nc.addObserverForName(UIApplicationDidBecomeActiveNotification, null, q) { _ -> handleWillEnterForeground() }
        nc.addObserverForName(AVAudioSessionInterruptionNotification, null, q) { note -> handleAudioInterruption(note) }
    }

    private fun handleAudioInterruption(note: NSNotification?) {
        val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue ?: return
        when (type) {
            AVAudioSessionInterruptionTypeBegan -> audioSessionActive = false
            AVAudioSessionInterruptionTypeEnded -> {
                val opts = (note.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.unsignedLongValue ?: 0uL
                val player = PlaybackBridge.activePlayer ?: activePlayer
                if ((opts and AVAudioSessionInterruptionOptionShouldResume) != 0uL && player?.playWhenReady == true) {
                    ensurePlaybackAudioSession(force = true)
                    player.resumeRateIfNeeded()
                }
            }
        }
    }

    private fun handleWillResignActive() {
        val player = PlaybackBridge.activePlayer ?: activePlayer ?: return
        wasPlayingBeforeBackground = player.isPlaying || player.playWhenReady
        player.setBackgroundPlaybackAllowed(PlaybackBridge.backgroundPlayEnabled)
        syncAutoPipFlag()
        if (wasPlayingBeforeBackground && PlaybackBridge.backgroundPlayEnabled) {
            transitioningToBackground = true
            sessionActive = true
            ensurePlaybackAudioSession()
            refreshNowPlayingOnMain(forcePlayingState = true)
        }
    }

    private fun handleDidEnterBackground() {
        val player = PlaybackBridge.activePlayer ?: activePlayer ?: return
        val inPip = pipActive || pipStarting || pipController?.isPictureInPictureActive() == true
        if (inPip) {
            transitioningToBackground = false
            refreshNowPlayingOnMain()
            return
        }
        val shouldKeepBgAudio = PlaybackBridge.backgroundPlayEnabled &&
            (wasPlayingBeforeBackground || player.isPlaying || player.playWhenReady)
        if (!shouldKeepBgAudio) {
            transitioningToBackground = false
            return
        }

        // 遵循 Apple QA1668：同步解绑 AVPlayerLayer 并保持 ContinuesIfPossible 策略即可无缝维持后台音频，
        // 严禁在播控切换时修改 AVPlayerItemTrack.enabled 或切换 AVAudioSession.mode，否则会触发音频管线重建而卡顿一下。
        transitioningToBackground = true
        player.setBackgroundPlaybackAllowed(true)
        val layer = activeLayer
        if (layer != null && layer.player === player.avPlayer) {
            layer.player = null
            detachedLayerForBg = layer
        }
        ensurePlaybackAudioSession()
        player.resumeRateIfNeeded()
        sessionActive = true
        startRefreshTimer()
        refreshNowPlayingOnMain(forcePlayingState = true)
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (1L * NSEC_PER_SEC.toLong())), dispatch_get_main_queue()) {
            transitioningToBackground = false
            if (PlaybackBridge.backgroundPlayEnabled && player.playWhenReady) {
                player.resumeRateIfNeeded()
            }
            if (sessionActive) refreshNowPlayingOnMain()
        }
    }

    private fun handleWillEnterForeground() {
        transitioningToBackground = false
        wasPlayingBeforeBackground = false
        val player = activePlayer ?: PlaybackBridge.activePlayer
        val layer = detachedLayerForBg
        if (layer != null && player != null) {
            layer.player = player.avPlayer
            detachedLayerForBg = null
            destroyPipController()
            syncAutoPipFlag()
        }
        if (sessionActive) {
            ensurePlaybackAudioSession()
            refreshNowPlayingOnMain()
        }
    }

    private fun startRefreshTimer() {
        if (refreshTimer != null) return
        refreshTimer = NSTimer.scheduledTimerWithTimeInterval(interval = 1.5, repeats = true) { _ ->
            if (sessionActive) refreshNowPlayingOnMain()
        }
    }

    private fun stopRefreshTimer() {
        refreshTimer?.invalidate()
        refreshTimer = null
    }

    private fun refreshNowPlayingOnMain(forcePlayingState: Boolean = false) {
        val player = PlaybackBridge.activePlayer ?: activePlayer ?: run {
            val center = MPNowPlayingInfoCenter.defaultCenter()
            center.playbackState = MPNowPlayingPlaybackStateStopped
            center.nowPlayingInfo = null
            return
        }
        val title = PlaybackBridge.activeTitle.ifBlank { "BiliCO" }
        val subtitle = PlaybackBridge.activeSubtitle
        val artworkUrl = PlaybackBridge.activeArtworkUrl
        ensureArtworkLoaded(artworkUrl)

        val durationSec = player.duration.coerceAtLeast(0L) / 1000.0
        val elapsedSec = player.currentPosition.coerceAtLeast(0L) / 1000.0
        val isLive = durationSec <= 0.0
        val playingNow = forcePlayingState || player.isPlaying || player.playWhenReady
        val rate = if (playingNow) player.speed.toDouble() else 0.0

        val info = mutableMapOf<Any?, Any?>()
        info[MPMediaItemPropertyTitle] = title
        if (subtitle.isNotBlank()) info[MPMediaItemPropertyArtist] = subtitle
        if (!isLive) {
            info[MPMediaItemPropertyPlaybackDuration] = NSNumber(double = durationSec)
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = NSNumber(double = elapsedSec)
        }
        info[MPNowPlayingInfoPropertyIsLiveStream] = NSNumber(bool = isLive)
        info[MPNowPlayingInfoPropertyPlaybackRate] = NSNumber(double = rate)
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = NSNumber(double = player.speed.toDouble())
        if (artworkUrl.isNotBlank() && artworkUrl == cachedArtworkUrl) {
            cachedArtwork?.let { info[MPMediaItemPropertyArtwork] = it }
        }
        val center = MPNowPlayingInfoCenter.defaultCenter()
        center.nowPlayingInfo = info
        center.playbackState = if (playingNow) MPNowPlayingPlaybackStatePlaying else MPNowPlayingPlaybackStatePaused
    }

    private fun ensureArtworkLoaded(url: String) {
        if (url.isBlank() || url == cachedArtworkUrl || url == loadingArtworkUrl) return
        loadingArtworkUrl = url
        if (url.startsWith("file://", ignoreCase = true)) {
            val path = dlFileUriToPath(url)
            val data = NSData.dataWithContentsOfFile(path)
            val img = data?.let { UIImage(data = it) }
            if (img != null) {
                cachedArtworkUrl = url
                cachedArtwork = MPMediaItemArtwork(boundsSize = img.size) { _ -> img }
            }
            loadingArtworkUrl = ""
            return
        }
        val normalized = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://", ignoreCase = true) -> "https://${url.removePrefix("http://").removePrefix("HTTP://")}"
            else -> url
        }
        val nsUrl = NSURL.URLWithString(normalized) ?: run {
            loadingArtworkUrl = ""
            return
        }
        val req = NSMutableURLRequest(
            uRL = nsUrl,
            cachePolicy = NSURLRequestUseProtocolCachePolicy,
            timeoutInterval = 10.0,
        ).apply {
            setValue("https://www.bilibili.com/", forHTTPHeaderField = "Referer")
            setValue(BILI_USER_AGENT, forHTTPHeaderField = "User-Agent")
        }
        NSURLSession.sharedSession.dataTaskWithRequest(req) { data, _, err ->
            val img = data?.let { UIImage(data = it) }
            dispatch_async(dispatch_get_main_queue()) {
                if (loadingArtworkUrl == url) loadingArtworkUrl = ""
                if (img != null) {
                    cachedArtworkUrl = url
                    cachedArtwork = MPMediaItemArtwork(boundsSize = img.size) { _ -> img }
                    if (sessionActive) refreshNowPlayingOnMain()
                } else if (err != null) {
                    logWarn(TAG, "artwork load failed: ${err.localizedDescription}")
                }
            }
        }.resume()
    }

    internal fun ensurePlaybackAudioSession(force: Boolean = false) {
        runOnMain {
            val session = AVAudioSession.sharedInstance()
            val matches = session.category == AVAudioSessionCategoryPlayback &&
                session.mode == AVAudioSessionModeMoviePlayback
            if (!force && audioSessionActive && matches) return@runOnMain
            runCatching {
                memScoped {
                    if (force || !matches) {
                        val err = alloc<ObjCObjectVar<NSError?>>()
                        val ok = session.setCategory(
                            AVAudioSessionCategoryPlayback,
                            AVAudioSessionModeMoviePlayback,
                            0u,
                            err.ptr,
                        )
                        if (!ok) {
                            err.value = null
                            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0u, err.ptr)
                        }
                    }
                    val actErr = alloc<ObjCObjectVar<NSError?>>()
                    if (session.setActive(true, error = actErr.ptr)) {
                        audioSessionActive = true
                    }
                }
            }
        }
    }

    private class PipDelegate : NSObject(), AVPictureInPictureControllerDelegateProtocol {
        override fun pictureInPictureControllerWillStartPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
            pipStarting = true
            pipActive = true
            ensurePlaybackAudioSession()
            logDebug(TAG, "PiP willStart")
        }

        override fun pictureInPictureControllerDidStartPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
            pipStarting = false
            pipActive = true
            refreshNowPlaying()
            logDebug(TAG, "PiP didStart")
        }

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            failedToStartPictureInPictureWithError: NSError,
        ) {
            pipStarting = false
            pipActive = false
            syncAutoPipFlag()
            logWarn(TAG, "PiP failedToStart err=${failedToStartPictureInPictureWithError.localizedDescription}")
        }

        override fun pictureInPictureControllerWillStopPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
            logDebug(TAG, "PiP willStop")
        }

        override fun pictureInPictureControllerDidStopPictureInPicture(pictureInPictureController: AVPictureInPictureController) {
            pipStarting = false
            pipActive = false
            syncAutoPipFlag()
            refreshNowPlaying()
            logDebug(TAG, "PiP didStop")
        }

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit,
        ) {
            restoreUserInterfaceForPictureInPictureStopWithCompletionHandler(true)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun BiliPlayer.resumeRateIfNeeded() {
    if (avPlayer.rate == 0f) {
        avPlayer.playImmediatelyAtRate(speed)
    }
    audioPlayer?.let { ap ->
        if (ap.rate == 0f) ap.playImmediatelyAtRate(speed)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun BiliPlayer.setBackgroundPlaybackAllowed(allowed: Boolean) {
    val policy = if (allowed) {
        AVPlayerAudiovisualBackgroundPlaybackPolicyContinuesIfPossible
    } else {
        AVPlayerAudiovisualBackgroundPlaybackPolicyPauses
    }
    if (avPlayer.audiovisualBackgroundPlaybackPolicy != policy) {
        avPlayer.audiovisualBackgroundPlaybackPolicy = policy
    }
    audioPlayer?.let { ap ->
        if (ap.audiovisualBackgroundPlaybackPolicy != policy) {
            ap.audiovisualBackgroundPlaybackPolicy = policy
        }
    }
}
