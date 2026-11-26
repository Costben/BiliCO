package erl.bilico.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.legacy.MediaMetadataCompat
import androidx.media3.session.legacy.MediaSessionCompat
import erl.bilico.BiliCoApplication
import erl.bilico.MainActivity
import erl.bilico.data.util.Formatters
import erl.bilico.platform.PlatformContext
import kotlinx.coroutines.runBlocking

/**
 * 后台播放的系统媒体条。进度、播放和暂停都交给 [MediaSession]，系统按播放器的位置自己画进度条。
 */
@OptIn(UnstableApi::class)
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
            PlaybackBridge.stopPlayback(PlatformContext(this))
            return START_NOT_STICKY
        }
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
        val player: Player = PlaybackBridge.activePlayer?.exoPlayer ?: return
        if (boundPlayer === player && mediaSession != null) {
            attachArtwork(mediaSession)
            return
        }
        mediaSession?.release()
        boundPlayer = player
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

    private fun attachArtwork(session: MediaSession?) {
        val source = PlaybackBridge.activeArtworkUrl
        if (source.isBlank() || source == loadedArtwork) return
        val isLocal = source.startsWith("file://")
        val url = if (isLocal) source else Formatters.sizedImageUrl(source, 480, 270)
        if (!isLocal && !url.startsWith("http")) return
        artworkBytes = null
        loadedArtwork = source
        Thread {
            val bytes = runCatching {
                if (isLocal) java.io.File(java.net.URI(source)).readBytes() else download(url)
            }.getOrElse { error ->
                Log.w(TAG, "封面下载失败 ${error.message}")
                null
            } ?: run {
                mainHandler.post {
                    if (PlaybackBridge.activeArtworkUrl == source) loadedArtwork = ""
                }
                return@Thread
            }
            if (mediaSession !== session || PlaybackBridge.activeArtworkUrl != source) return@Thread
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            mainHandler.post {
                if (mediaSession !== session || PlaybackBridge.activeArtworkUrl != source) return@post
                artworkBytes = bytes
                publishArtwork(bytes)
                if (bitmap != null) publishLegacyArtwork(bitmap)
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
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
        runCatching { player.replaceMediaItem(index, updated) }
    }

    private fun publishLegacyArtwork(bitmap: Bitmap) {
        val session = mediaSession ?: return
        val compat = runCatching { legacySession(session) }.getOrNull() ?: return
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, PlaybackBridge.activeTitle.ifBlank { "正在后台播放" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, PlaybackBridge.activeSubtitle.ifBlank { "BiliCO" })
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
            .build()
        runCatching { compat.setMetadata(metadata) }
    }

    private fun legacySession(session: MediaSession): MediaSessionCompat {
        val impl = MediaSession::class.java.getDeclaredMethod("getImpl").apply { isAccessible = true }
            .invoke(session)
        val stub = impl.javaClass.getDeclaredMethod("getMediaSessionLegacyStub").apply { isAccessible = true }
            .invoke(impl)
        return stub.javaClass.getMethod("getSessionCompat").invoke(stub) as MediaSessionCompat
    }

    private fun download(url: String): ByteArray = runBlocking {
        (applicationContext as BiliCoApplication).container.client.fetchBytes(url)
    }

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

        fun start(context: Context) {
            val appCtx = context.applicationContext
            runCatching { appCtx.startService(Intent(appCtx, PlaybackService::class.java)) }
        }

        fun promote(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, PlaybackService::class.java).putExtra(EXTRA_PROMOTE, true)
            runCatching { appCtx.startForegroundService(intent) }
        }

        fun refreshIfRunning(context: Context) {
            val appCtx = context.applicationContext
            runCatching { appCtx.startService(Intent(appCtx, PlaybackService::class.java)) }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            runCatching { appCtx.stopService(Intent(appCtx, PlaybackService::class.java)) }
        }
    }
}
