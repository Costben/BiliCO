package erl.bilico.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.session.legacy.MediaMetadataCompat
import androidx.media3.session.legacy.MediaSessionCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import erl.bilico.BiliCoApplication
import erl.bilico.MainActivity
import erl.bilico.data.util.Formatters

/**
 * 后台播放的系统媒体条。进度、播放和暂停都交给 [MediaSession]，系统按播放器的位置自己画进度条。
 * 只留播放/暂停和停止，不接上下一首。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var boundPlayer: Player? = null
    private var sessionPlayer: ForwardingPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var artworkBytes: ByteArray? = null
    private var loadedArtwork: String = ""

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PlaybackBridge.ACTION_STOP_BG) {
            PlaybackBridge.stopPlayback(this)
            return START_NOT_STICKY
        }
        // 前台服务必须在这次启动里立刻 startForeground。封面还没到、会话建不成时也先占住，避免超时被杀。
        if (intent?.getBooleanExtra(EXTRA_PROMOTE, false) == true) holdForeground()
        ensureSession()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun holdForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "后台播放", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(PlaybackBridge.activeTitle.ifBlank { "正在后台播放" })
            .setContentText(PlaybackBridge.activeSubtitle.ifBlank { "BiliCO" })
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(HOLD_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(HOLD_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        sessionPlayer = null
        boundPlayer = null
        super.onDestroy()
    }

    private fun ensureSession() {
        val player = PlaybackBridge.activePlayer ?: return
        // 封面晚到只补一次。会话已经在前台就不再拆，拆掉会把 startForeground 打断。
        if (boundPlayer === player && mediaSession != null) {
            attachArtwork(mediaSession)
            return
        }
        mediaSession?.release()
        boundPlayer = player
        // 只改媒体条上看到的标题。正在播的地址由播放页自己设，这里换媒体项会把地址换成空的，直接崩。
        val wrapped = object : ForwardingPlayer(player) {
            override fun getMediaMetadata(): MediaMetadata {
                val builder = super.getMediaMetadata().buildUpon()
                    .setTitle(PlaybackBridge.activeTitle.ifBlank { "正在后台播放" })
                    .setArtist(PlaybackBridge.activeSubtitle.ifBlank { "BiliCO" })
                artworkBytes?.let { builder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                return builder.build()
            }

            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands()
                    .buildUpon()
                    .remove(COMMAND_SEEK_TO_NEXT)
                    .remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_TO_PREVIOUS)
                    .remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean =
                command != COMMAND_SEEK_TO_NEXT &&
                    command != COMMAND_SEEK_TO_NEXT_MEDIA_ITEM &&
                    command != COMMAND_SEEK_TO_PREVIOUS &&
                    command != COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM &&
                    super.isCommandAvailable(command)
        }
        sessionPlayer = wrapped
        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            201,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val session = MediaSession.Builder(this, wrapped)
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .setCallback(Callback())
            .build()
        mediaSession = session
        attachArtwork(session)
    }

    /**
     * B 站图床不认系统自己的请求。封面自己下，下好叠到会话读到的元数据上。
     * 系统媒体会话只解码这一层的封面字节。不改正在播的那一条，地址不动。
     */
    private fun attachArtwork(session: MediaSession?) {
        val source = PlaybackBridge.activeArtworkUrl
        val url = Formatters.sizedImageUrl(source, 480, 270)
        if (!url.startsWith("http") || source == loadedArtwork) return
        artworkBytes = null
        loadedArtwork = source
        Thread {
            val bytes = runCatching { download(url) }.getOrElse { error ->
                Log.w(TAG, "封面下载失败 ${error.message}")
                null
            } ?: run {
                loadedArtwork = ""
                return@Thread
            }
            Log.i(TAG, "封面已下载 ${bytes.size}")
            mainHandler.post {
                if (mediaSession !== session || PlaybackBridge.activeArtworkUrl != source) return@post
                artworkBytes = bytes
                // 标题不变时播放器不会再发元数据变化。补一次替换，让会话重新读封面。
                publishArtwork(bytes)
                publishLegacyArtwork(bytes)
                val target = mediaSession ?: return@post
                runCatching { onUpdateNotification(target, true) }
            }
        }.start()
    }

    private fun publishArtwork(bytes: ByteArray) {
        val player = boundPlayer ?: return
        val index = player.currentMediaItemIndex
        val current = player.currentMediaItem ?: return
        if (current.localConfiguration == null) return
        val metadata = current.mediaMetadata.buildUpon()
            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        val updated = current.buildUpon().setMediaMetadata(metadata).build()
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
            Log.w(TAG, "封面写不进当前项")
            return
        }
        runCatching { player.replaceMediaItem(index, updated) }
            .onFailure { Log.w(TAG, "封面写入失败 ${it.javaClass.simpleName}") }
    }

    /**
     * 流体云读的是系统媒体会话里的封面位图，不读播放器元数据。
     * 封面下好后直接写进这一层，标题和作者保持不变。
     */
    private fun publishLegacyArtwork(bytes: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
            Log.w(TAG, "封面解码失败")
            return
        }
        val session = mediaSession ?: return
        val compat = runCatching { legacySession(session) }.getOrElse {
            Log.w(TAG, "系统会话取不到 ${it.javaClass.simpleName}: ${it.cause?.message ?: it.message}")
            return
        }
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, PlaybackBridge.activeTitle.ifBlank { "正在后台播放" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, PlaybackBridge.activeSubtitle.ifBlank { "BiliCO" })
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
            .build()
        runCatching { compat.setMetadata(metadata) }
            .onFailure { Log.w(TAG, "系统会话封面写入失败 ${it.javaClass.simpleName}") }
        val written = compat.controller.metadata?.getMostRelevantArtworkBitmapData()?.size ?: 0
        Log.i(TAG, "系统会话封面字节 $written")
    }

    private fun legacySession(session: MediaSession): MediaSessionCompat {
        val impl = MediaSession::class.java.getDeclaredMethod("getImpl").apply { isAccessible = true }
            .invoke(session)
        val stub = impl.javaClass.getDeclaredMethod("getMediaSessionLegacyStub").apply { isAccessible = true }
            .invoke(impl)
        return stub.javaClass.getMethod("getSessionCompat").invoke(stub) as MediaSessionCompat
    }

    /** 走 App 自己的网络栈。系统自己去拉图床会被拒，界面里的封面就是这条栈下的。 */
    private fun download(url: String): ByteArray =
        (applicationContext as BiliCoApplication).container.client.fetchBytes(url)

    private class Callback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val hints = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(hints)
                .build()
        }
    }

    companion object {
        private const val TAG = "BiliPlayback"
        private const val SESSION_ID = "bilico_playback"
        private const val CHANNEL_ID = "bilico_playback"
        private const val HOLD_NOTIFICATION_ID = 1001
        private const val EXTRA_PROMOTE = "promote"

        fun start(context: android.content.Context) {
            val appCtx = context.applicationContext
            runCatching {
                // 应用还在前台时走普通启动。前台服务那条必须马上 startForeground，封面没到时建不成会话就会超时闪退。
                appCtx.startService(Intent(appCtx, PlaybackService::class.java))
            }
        }

        fun promote(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, PlaybackService::class.java).putExtra(EXTRA_PROMOTE, true)
            runCatching { appCtx.startForegroundService(intent) }
        }

        fun refreshIfRunning(context: android.content.Context) {
            val appCtx = context.applicationContext
            runCatching {
                appCtx.startService(Intent(appCtx, PlaybackService::class.java))
            }
        }

        fun stop(context: android.content.Context) {
            val appCtx = context.applicationContext
            runCatching {
                appCtx.stopService(Intent(appCtx, PlaybackService::class.java))
            }
        }
    }
}
