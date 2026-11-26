package erl.bilico.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import erl.bilico.data.network.BiliClient
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.currentAppVersionName
import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.ioDispatcher
import erl.bilico.platform.showToast
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 自动检查更新节流间隔：12 小时内已查过则冷启动不再重复请求。 */
const val UpdateCheckInterval = 12 * 60 * 60 * 1000L

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
    val prerelease: Boolean,
    val publishedAt: String = "",
)

data class DownloadedUpdatePackage(
    val path: String,
    val sizeBytes: Long,
)

expect fun updatePlatformUserAgent(): String

expect fun preferredReleaseAssetExtension(): String

expect fun supportsInAppUpdate(): Boolean

expect fun openPlatformUpdateDownload(context: PlatformContext, info: UpdateInfo)

expect suspend fun downloadPlatformUpdatePackage(
    context: PlatformContext,
    url: String,
    version: String,
    force: Boolean = false,
    onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
): DownloadedUpdatePackage

expect fun installPlatformUpdatePackage(
    context: PlatformContext,
    pkg: DownloadedUpdatePackage,
): Result<Unit>

object AppUpdater {
    private const val REPO = "Costben/BiliCO"
    private const val API = "https://api.github.com/repos/$REPO/releases?per_page=20"

    fun currentVersion(context: PlatformContext): String =
        currentAppVersionName(context).removePrefix("v").removePrefix("V")

    fun supportsInAppUpdate(): Boolean = erl.bilico.update.supportsInAppUpdate()

    suspend fun latest(
        http: HttpClient,
        currentVersion: String,
        includePrerelease: Boolean,
    ): UpdateInfo? = withContext(ioDispatcher) {
        val current = currentVersion.removePrefix("v").removePrefix("V")
        val best = releases(http, includePrerelease).firstOrNull() ?: return@withContext null
        if (compareVersion(best.version, current) > 0) best else null
    }

    suspend fun releases(http: HttpClient, includePrerelease: Boolean): List<UpdateInfo> =
        withContext(ioDispatcher) {
            val resp = http.get(API) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", updatePlatformUserAgent())
            }
            if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")
            val arr = BiliClient.json.parseToJsonElement(resp.bodyAsText()).jsonArray
            val preferredExt = preferredReleaseAssetExtension()
            val candidates = ArrayList<UpdateInfo>(arr.size)
            for (el in arr) {
                val item = el as? JsonObject ?: continue
                if (item["draft"]?.jsonPrimitive?.booleanOrNull == true) continue
                val tag = item["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val version = tag.removePrefix("v").removePrefix("V")
                if (version.isEmpty()) continue
                val prerelease = item["prerelease"]?.jsonPrimitive?.booleanOrNull == true || version.contains('-')
                if (prerelease && !includePrerelease) continue
                val assets = item["assets"] as? JsonArray
                var assetUrl: String? = null
                if (assets != null) {
                    for (aEl in assets) {
                        val asset = aEl as? JsonObject ?: continue
                        val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (name.endsWith(preferredExt, ignoreCase = true) && url.isNotEmpty()) {
                            assetUrl = url
                            break
                        }
                    }
                }
                val pageUrl = item["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty().ifEmpty {
                    "https://github.com/$REPO/releases"
                }
                candidates += UpdateInfo(
                    version = version,
                    notes = item["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                    apkUrl = assetUrl,
                    pageUrl = pageUrl,
                    prerelease = prerelease,
                    publishedAt = item["published_at"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                )
            }
            candidates.sortedWith { a, b -> compareVersion(b.version, a.version) }
        }

    fun openDownload(context: PlatformContext, info: UpdateInfo) {
        openPlatformUpdateDownload(context, info)
    }

    suspend fun downloadPackage(
        context: PlatformContext,
        url: String,
        version: String,
        force: Boolean = false,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadedUpdatePackage = downloadPlatformUpdatePackage(
        context = context,
        url = url,
        version = version,
        force = force,
        onProgress = onProgress,
    )

    fun installPackage(context: PlatformContext, pkg: DownloadedUpdatePackage): Result<Unit> =
        installPlatformUpdatePackage(context, pkg)

    /**
     * 先比主版本数字段；数字一样时正式版高于预发布版（`0.1.2 > 0.1.2-pre`），
     * 两个预发布版之间按后缀字典序比较。
     */
    fun compareVersion(a: String, b: String): Int {
        val (aCore, aPre) = splitPre(a)
        val (bCore, bPre) = splitPre(b)
        val ap = aCore.split('.').map { it.toIntOrNull() ?: 0 }
        val bp = bCore.split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(ap.size, bp.size)
        for (i in 0 until n) {
            val av = ap.getOrElse(i) { 0 }
            val bv = bp.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        if (aPre.isEmpty() && bPre.isEmpty()) return 0
        if (aPre.isEmpty()) return 1
        if (bPre.isEmpty()) return -1
        return aPre.compareTo(bPre)
    }

    private fun splitPre(v: String): Pair<String, String> {
        val idx = v.indexOf('-')
        return if (idx < 0) v to "" else v.substring(0, idx) to v.substring(idx + 1)
    }
}

/**
 * 进程级更新状态持有者（挂在 AppContainer.updater 上）：
 * 统一管理「检查更新中」状态、待展示的更新弹窗及更新日志预览。
 */
class AppUpdateManager(
    private val context: PlatformContext,
    private val settings: SettingsRepository,
    val http: HttpClient,
) {
    var checkingUpdate by mutableStateOf(false)
        private set

    var pendingUpdate by mutableStateOf<UpdateInfo?>(null)
        private set

    var previewingUpdate by mutableStateOf(false)
        private set

    var latestCheckedInfo by mutableStateOf<UpdateInfo?>(null)
        private set

    fun dismissUpdate() {
        pendingUpdate = null
        previewingUpdate = false
    }

    fun previewUpdate(info: UpdateInfo) {
        previewingUpdate = true
        pendingUpdate = info
    }

    suspend fun checkUpdate(manual: Boolean): UpdateInfo? {
        if (checkingUpdate) {
            if (manual) toast("正在检查更新…")
            return pendingUpdate
        }
        checkingUpdate = true
        try {
            val current = AppUpdater.currentVersion(context)
            val includePre = settings.acceptPrerelease.value
            val info = AppUpdater.latest(http, current, includePrerelease = includePre)
            settings.setUpdateCheckedAt(currentTimeMillis())
            latestCheckedInfo = info
            if (info == null) {
                pendingUpdate = null
                if (manual) toast("已是最新版本 v$current")
                return null
            }
            if (!manual && info.version == settings.skippedUpdateVersion.value) {
                return info
            }
            previewingUpdate = false
            pendingUpdate = info
            return info
        } catch (t: Throwable) {
            if (manual) toast(t.message ?: "检查更新失败")
            return null
        } finally {
            checkingUpdate = false
        }
    }

    fun toast(message: String) {
        showToast(context, message)
    }
}
