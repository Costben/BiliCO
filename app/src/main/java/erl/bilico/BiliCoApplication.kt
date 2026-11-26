package erl.bilico

import android.app.Application
import erl.bilico.di.AppContainer
import erl.bilico.download.DownloadService
import erl.bilico.platform.AndroidPlatformBridge
import erl.bilico.platform.PlatformContext
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.PlaybackService

class BiliCoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AndroidPlatformBridge.init(this)
        container = AppContainer(PlatformContext(this))
        container.downloads.onActiveDownloadsChanged = { hasActive ->
            DownloadService.syncState(this, hasActive)
        }
        PlaybackBridge.onSessionStart = { PlaybackService.start(this) }
        PlaybackBridge.onSessionPromote = { PlaybackService.promote(this) }
        PlaybackBridge.onSessionRefresh = { PlaybackService.refreshIfRunning(this) }
        PlaybackBridge.onSessionStop = { PlaybackService.stop(this) }
    }
}
