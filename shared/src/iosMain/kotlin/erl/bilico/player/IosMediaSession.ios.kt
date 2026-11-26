package erl.bilico.player

import erl.bilico.platform.PlatformContext
import erl.bilico.platform.dlFileUriToPath
import erl.bilico.platform.logDebug
import erl.bilico.platform.logWarn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.rate
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import platform.Foundation.dataWithContentsOfFile
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyDefaultPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyIsLiveStream
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.MediaPlayer.MPSkipIntervalCommandEvent
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS 系统媒体会话与画中画管理器：
 * 1. 通过 [MPNowPlayingInfoCenter] + [MPRemoteCommandCenter] 接入原生灵动岛媒体胶囊、锁屏与控制中心播控；
 * 2. 通过 [AVPictureInPictureController] 绑定当前活跃 [AVPlayerLayer]，支持手动进入与退后台自动画中画；
 * 3. 遵循 iOS 原生协同规范：进入画中画浮窗时由系统接管浮窗并隐藏灵动岛胶囊，纯后台听音（关闭自动 PiP 或黑听）
 *    时自动解绑视频层维持后台音频并展示灵动岛胶囊与封面声波。
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosMediaSession {
    private const val TAG = "IosMediaSession"

    private var installed = false
    private var sessionActive = false
    private var activePlayer: BiliPlayer? = null
    private var activeLayer: AVPlayerLayer? = null
    private var detachedLayerForBg: AVPlayerLayer? = null
    private var pipController: AVPictureInPictureController? = null
    private var pipDelegate: PipDelegate? = null
    private var pipActive = false
    private var pipStarting = false
    private var autoPipAllowed = false

    private var cachedArtworkUrl: String = ""
    private var cachedArtwork: MPMediaItemArtwork? = null
    private var loadingArtworkUrl: String = ""
    private var refreshTimer: NSTimer? = null

    fun install() {
        if (installed) return
        installed = true
        PlaybackBridge.onSessionStart = { onSessionStart() }
        PlaybackBridge.onSessionPromote = { onSessionPromote() }
        PlaybackBridge.onSessionRefresh = { refreshNowPlaying() }
        PlaybackBridge.onSessionStop = { onSessionStop() }
        PlaybackBridge.isPlatformPipActive = { pipActive || pipStarting }

        setupRemoteCommands()
        observeAppLifecycle()
    }

    fun isPipSupported(): Boolean = AVPictureInPictureController.isPictureInPictureSupported()

    fun isPipActiveOrStarting(): Boolean = pipActive || pipStarting

    fun attachPlayerLayer(player: BiliPlayer, layer: AVPlayerLayer) {
        install()
        dispatch_async(dispatch_get_main_queue()) {
            if (activeLayer === layer && activePlayer === player) {
                syncAutoPipFlag()
                return@dispatch_async
            }
            if (detachedLayerForBg === layer) {
                layer.player = player.avPlayer
                detachedLayerForBg = null
            }
            activePlayer = player
            activeLayer = layer
            rebuildPipController(layer)
        }
    }

    fun detachPlayerLayer(layer: AVPlayerLayer) {
        dispatch_async(dispatch_get_main_queue()) {
            if (activeLayer !== layer) return@dispatch_async
            if (pipActive || pipStarting) return@dispatch_async
            if (detachedLayerForBg === layer) detachedLayerForBg = null
            pipController?.delegate = null
            pipController = null
            pipDelegate = null
            activeLayer = null
            activePlayer = null
        }
    }

    fun updatePipParams(activePlayback: Boolean, isPlaying: Boolean, autoPip: Boolean) {
        install()
        dispatch_async(dispatch_get_main_queue()) {
            autoPipAllowed = activePlayback && isPlaying && autoPip
            syncAutoPipFlag()
            if (sessionActive) refreshNowPlayingOnMain()
        }
    }

    fun startPipNow(): Boolean {
        install()
        if (!isPipSupported()) return false
        val controller = pipController ?: activeLayer?.let { rebuildPipController(it) } ?: return false
        if (controller.isPictureInPictureActive()) return true
        if (!controller.isPictureInPicturePossible()) {
            logWarn(TAG, "startPipNow skipped: isPictureInPicturePossible=false")
            return false
        }
        ensurePlaybackAudioSession()
        pipStarting = true
        controller.startPictureInPicture()
        return true
    }

    fun refreshNowPlaying() {
        if (!installed) return
        dispatch_async(dispatch_get_main_queue()) {
            if (sessionActive) refreshNowPlayingOnMain()
        }
    }

    private fun onSessionStart() {
        dispatch_async(dispatch_get_main_queue()) {
            sessionActive = true
            ensurePlaybackAudioSession()
            startRefreshTimer()
            refreshNowPlayingOnMain()
        }
    }

    private fun onSessionPromote() {
        dispatch_async(dispatch_get_main_queue()) {
            sessionActive = true
            ensurePlaybackAudioSession()
            startRefreshTimer()
            refreshNowPlayingOnMain()
        }
    }

    private fun onSessionStop() {
        dispatch_async(dispatch_get_main_queue()) {
            sessionActive = false
            stopRefreshTimer()
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
        }
    }

    private fun rebuildPipController(layer: AVPlayerLayer): AVPictureInPictureController? {
        if (!isPipSupported()) return null
        pipController?.delegate = null
        val controller = AVPictureInPictureController(playerLayer = layer)
        val delegate = PipDelegate()
        controller.delegate = delegate
        pipDelegate = delegate
        pipController = controller
        syncAutoPipFlag()
        return controller
    }

    private fun syncAutoPipFlag() {
        val controller = pipController ?: return
        val allow = autoPipAllowed && PlaybackBridge.autoPipEnabled && activeLayer != null
        controller.canStartPictureInPictureAutomaticallyFromInline = allow
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
        nc.addObserverForName(
            UIApplicationDidEnterBackgroundNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { _ ->
            handleDidEnterBackground()
        }
        nc.addObserverForName(
            UIApplicationWillEnterForegroundNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { _ ->
            handleWillEnterForeground()
        }
    }

    private fun handleDidEnterBackground() {
        val player = PlaybackBridge.activePlayer ?: activePlayer ?: return
        val inPip = pipActive || pipStarting || pipController?.isPictureInPictureActive() == true
        if (inPip) {
            refreshNowPlayingOnMain()
            return
        }
        val shouldKeepBgAudio = PlaybackBridge.backgroundPlayEnabled && (player.isPlaying || player.playWhenReady)
        if (!shouldKeepBgAudio) return

        // Apple QA1668：非 PiP 场景进后台时需暂时从 AVPlayerLayer 解绑 AVPlayer，
        // 避免系统因视频层不可见而强制暂停播放；随后刷新 Now Playing 由系统自动上灵动岛与锁屏。
        val layer = activeLayer
        if (layer != null && layer.player === player.avPlayer) {
            layer.player = null
            detachedLayerForBg = layer
        }
        ensurePlaybackAudioSession()
        if (player.avPlayer.rate == 0f) {
            player.play()
        }
        sessionActive = true
        refreshNowPlayingOnMain()
    }

    private fun handleWillEnterForeground() {
        val layer = detachedLayerForBg
        val player = activePlayer ?: PlaybackBridge.activePlayer
        if (layer != null && player != null) {
            layer.player = player.avPlayer
            detachedLayerForBg = null
        }
        if (sessionActive) refreshNowPlayingOnMain()
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

    private fun refreshNowPlayingOnMain() {
        val player = PlaybackBridge.activePlayer ?: activePlayer ?: run {
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
            return
        }
        val title = PlaybackBridge.activeTitle.ifBlank { "BiliCO" }
        val subtitle = PlaybackBridge.activeSubtitle
        val artworkUrl = PlaybackBridge.activeArtworkUrl
        ensureArtworkLoaded(artworkUrl)

        val durationSec = player.duration.coerceAtLeast(0L) / 1000.0
        val elapsedSec = player.currentPosition.coerceAtLeast(0L) / 1000.0
        val isLive = durationSec <= 0.0
        val playingNow = player.isPlaying || player.playWhenReady
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
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
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
        val normalized = if (url.startsWith("//")) "https:$url" else url
        val nsUrl = NSURL.URLWithString(normalized) ?: run {
            loadingArtworkUrl = ""
            return
        }
        NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, _, _ ->
            val img = data?.let { UIImage(data = it) }
            dispatch_async(dispatch_get_main_queue()) {
                if (loadingArtworkUrl == url) loadingArtworkUrl = ""
                if (img != null) {
                    cachedArtworkUrl = url
                    cachedArtwork = MPMediaItemArtwork(boundsSize = img.size) { _ -> img }
                    if (sessionActive) refreshNowPlayingOnMain()
                }
            }
        }.resume()
    }

    internal fun ensurePlaybackAudioSession() {
        runCatching {
            memScoped {
                val session = AVAudioSession.sharedInstance()
                val err = alloc<ObjCObjectVar<NSError?>>()
                var ok = session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeMoviePlayback, 0u, err.ptr)
                if (!ok) {
                    err.value = null
                    session.setCategory(AVAudioSessionCategoryPlayback, platform.AVFAudio.AVAudioSessionModeDefault, 0u, err.ptr)
                }
                val actErr = alloc<ObjCObjectVar<NSError?>>()
                session.setActive(true, error = actErr.ptr)
            }
        }
    }

    private class PipDelegate : NSObject(), AVPictureInPictureControllerDelegateProtocol {
        override fun pictureInPictureControllerWillStartPictureInPicture(
            pictureInPictureController: AVPictureInPictureController,
        ) {
            pipStarting = true
            pipActive = true
            ensurePlaybackAudioSession()
            logDebug(TAG, "PiP willStart")
        }

        override fun pictureInPictureControllerDidStartPictureInPicture(
            pictureInPictureController: AVPictureInPictureController,
        ) {
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
            logWarn(TAG, "PiP failedToStart err=${failedToStartPictureInPictureWithError.localizedDescription}")
        }

        override fun pictureInPictureControllerWillStopPictureInPicture(
            pictureInPictureController: AVPictureInPictureController,
        ) {
            logDebug(TAG, "PiP willStop")
        }

        override fun pictureInPictureControllerDidStopPictureInPicture(
            pictureInPictureController: AVPictureInPictureController,
        ) {
            pipStarting = false
            pipActive = false
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
