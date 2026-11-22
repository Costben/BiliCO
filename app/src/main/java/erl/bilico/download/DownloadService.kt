package erl.bilico.download

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
import androidx.core.content.ContextCompat
import erl.bilico.BiliCoApplication
import erl.bilico.MainActivity
import erl.bilico.data.model.DownloadStatus
import erl.bilico.data.model.DownloadTask
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 离线缓存前台服务：有下载任务时保活并显示进度通知，队列清空后自动退出。
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE_ALL) {
            (application as BiliCoApplication).container.downloads.pauseAll()
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        promoteForeground(null, 0)
        startObserving()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startObserving() {
        if (collectJob?.isActive == true) return
        val downloads = (application as BiliCoApplication).container.downloads
        collectJob = serviceScope.launch {
            downloads.tasks.collectLatest { tasks ->
                val active = tasks.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                    ?: tasks.firstOrNull { it.status == DownloadStatus.QUEUED }
                val activeCount = tasks.count { it.isActive }
                if (active == null || activeCount == 0) {
                    stopSelfCleanly()
                    return@collectLatest
                }
                val now = System.currentTimeMillis()
                if (now - lastNotifyAt >= NOTIFY_INTERVAL_MS || active.status == DownloadStatus.QUEUED) {
                    lastNotifyAt = now
                    promoteForeground(active, activeCount)
                }
            }
        }
    }

    private fun promoteForeground(active: DownloadTask?, activeCount: Int) {
        ensureChannel()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            301,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseIntent = PendingIntent.getService(
            this,
            302,
            Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = active?.displayTitle ?: "正在准备离线缓存…"
        val text = when {
            active == null -> "等待开始"
            active.status == DownloadStatus.QUEUED -> "排队中（共 $activeCount 个任务）"
            active.totalBytes > 0L -> {
                val pct = (active.progress * 100f).toInt().coerceIn(0, 100)
                val speed = if (active.speedBytesPerSec > 0L) {
                    " · ${Formatters.formatSpeed(active.speedBytesPerSec)}"
                } else {
                    ""
                }
                "$pct% · ${Formatters.formatFileSize(active.downloadedBytes)} / ${Formatters.formatFileSize(active.totalBytes)}$speed"
            }
            else -> "${Formatters.formatFileSize(active.downloadedBytes)}"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "全部暂停", pauseIntent)

        if (active != null && active.totalBytes > 0L) {
            builder.setProgress(100, (active.progress * 100f).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        val notification = builder.build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun stopSelfCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "离线缓存", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "bilico_download"
        private const val NOTIFICATION_ID = 1002
        private const val NOTIFY_INTERVAL_MS = 600L
        private const val ACTION_PAUSE_ALL = "erl.bilico.action.PAUSE_ALL_DOWNLOADS"

        fun syncState(context: Context, hasActive: Boolean) {
            val appCtx = context.applicationContext
            if (hasActive) {
                runCatching {
                    ContextCompat.startForegroundService(appCtx, Intent(appCtx, DownloadService::class.java))
                }
            } else {
                runCatching {
                    appCtx.stopService(Intent(appCtx, DownloadService::class.java))
                }
            }
        }
    }
}
