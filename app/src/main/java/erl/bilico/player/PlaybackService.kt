package erl.bilico.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import erl.bilico.MainActivity

class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台播放",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val player = PlaybackBridge.activePlayer
        val playing = player?.isPlaying == true
        val title = PlaybackBridge.activeTitle.ifBlank { "正在后台播放" }
        val subtitle = PlaybackBridge.activeSubtitle.ifBlank { "BiliCO" }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            201,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val togglePending = PendingIntent.getBroadcast(
            this,
            202,
            Intent(PlaybackBridge.ACTION_TOGGLE_PLAY).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPending = PendingIntent.getBroadcast(
            this,
            203,
            Intent(PlaybackBridge.ACTION_STOP_BG).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentPending)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "暂停" else "播放",
                togglePending,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopPending,
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "bilico_playback"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var running = false

        fun start(context: Context, title: String, subtitle: String) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, PlaybackService::class.java)
            runCatching {
                appCtx.startForegroundService(intent)
            }
        }

        fun refreshIfRunning(context: Context) {
            if (!running) return
            val appCtx = context.applicationContext
            runCatching {
                appCtx.startService(Intent(appCtx, PlaybackService::class.java))
            }
        }

        fun stop(context: Context) {
            if (!running) return
            running = false
            val appCtx = context.applicationContext
            runCatching {
                appCtx.stopService(Intent(appCtx, PlaybackService::class.java))
            }
        }
    }
}
