package erl.bilico.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Xml
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import erl.bilico.BuildConfig
import erl.bilico.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

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

/**
 * GitHub Releases 更新检查与应用内安装包下载器（对齐 Cobo Updater + c001apk InAppUpdater）：
 * - 支持正式版与预发布版（`-pre` / `-beta` / `prerelease=true`）过滤与语义化版本比较
 * - API 限流（403）时自动降级至 releases.atom + expanded_assets，避免共享出口无法检更新
 * - 多镜像（gh-proxy / ghfast / 直连）并发测速择优 + HTTP Range 4 路分片断点续传
 * - 下载完成后校验 APK 归档包名，通过 FileProvider 调起系统安装器
 */
object AppUpdater {
    private const val REPO = "Costben/BiliCO"
    private const val REPOSITORY_URL = "https://github.com/$REPO"
    private const val API = "https://api.github.com/repos/$REPO/releases?per_page=20"
    private const val ATOM_FEED = "$REPOSITORY_URL/releases.atom"
    private val EXPANDED_ASSET_HREF = Regex(
        """href="(/[^"]+/releases/download/[^"]+\.apk)"""",
        RegexOption.IGNORE_CASE,
    )

    private val MIRRORS = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "",
    )
    private const val SEGMENTS = 4
    private const val PROBE_BYTES = 64 * 1024L
    private const val PROBE_TIMEOUT_MS = 1500L

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 16
            },
        )
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault(BuildConfig.VERSION_NAME).ifBlank { BuildConfig.VERSION_NAME }
        .removePrefix("v")
        .removePrefix("V")

    suspend fun latest(
        currentVersion: String,
        includePrerelease: Boolean,
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = currentVersion.removePrefix("v").removePrefix("V")
        val best = releases(includePrerelease).firstOrNull() ?: return@withContext null
        if (compareVersion(best.version, current) > 0) best else null
    }

    suspend fun releases(includePrerelease: Boolean): List<UpdateInfo> = withContext(Dispatchers.IO) {
        val apiResult = runCatching { fetchReleasesFromApi(includePrerelease) }
        apiResult.getOrNull()?.let { return@withContext it }
        // api.github.com 未认证接口在共享代理 IP 下极易触发 60 次/小时 403 限流，
        // 自动降级到不受 API 配额限制的 github.com/releases.atom + expanded_assets。
        runCatching { fetchReleasesFromAtom(includePrerelease) }.getOrNull()
            ?: throw (apiResult.exceptionOrNull() ?: IOException("检查更新失败"))
    }

    private fun fetchReleasesFromApi(includePrerelease: Boolean): List<UpdateInfo> {
        val req = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BiliCO-Android")
            .build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body.string()
        }
        val arr = JSONArray(body)
        val candidates = ArrayList<UpdateInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optBoolean("draft", false)) continue
            val tag = item.optString("tag_name").trim()
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isEmpty()) continue
            val prerelease = item.optBoolean("prerelease", false) || version.contains('-')
            if (prerelease && !includePrerelease) continue
            val assets = item.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                        apkUrl = url
                        break
                    }
                }
            }
            val pageUrl = item.optString("html_url").ifEmpty {
                "$REPOSITORY_URL/releases"
            }
            candidates += UpdateInfo(
                version = version,
                notes = item.optString("body").trim(),
                apkUrl = apkUrl,
                pageUrl = pageUrl,
                prerelease = prerelease,
                publishedAt = item.optString("published_at").trim(),
            )
        }
        return candidates.sortedWith { a, b -> compareVersion(b.version, a.version) }
    }

    private suspend fun fetchReleasesFromAtom(includePrerelease: Boolean): List<UpdateInfo> {
        val req = Request.Builder()
            .url(ATOM_FEED)
            .header("Accept", "application/atom+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("User-Agent", "BiliCO-Android")
            .build()
        val xml = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body.string()
        }
        val parsed = parseAtomReleases(xml, includePrerelease)
        if (parsed.isEmpty()) return emptyList()
        return coroutineScope {
            parsed.mapIndexed { index, entry ->
                if (index < 3) {
                    async(Dispatchers.IO) {
                        val apkUrl = runCatching { fetchApkUrlFromExpandedAssets(entry.tag, entry.info.version) }.getOrNull()
                        entry.info.copy(apkUrl = apkUrl)
                    }
                } else {
                    async { entry.info }
                }
            }.awaitAll()
        }
    }

    private data class AtomReleaseEntry(
        val tag: String,
        val info: UpdateInfo,
    )

    private fun parseAtomReleases(xml: String, includePrerelease: Boolean): List<AtomReleaseEntry> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val entries = mutableListOf<AtomReleaseEntry>()
        var inEntry = false
        var currentTag: String? = null
        var linkHref = ""
        var updated = ""
        var contentHtml = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name == "entry") {
                        inEntry = true
                        linkHref = ""
                        updated = ""
                        contentHtml = ""
                    } else if (inEntry) {
                        currentTag = name
                        if (name == "link") {
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                            if (href.contains("/releases/tag/")) linkHref = href
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inEntry) {
                    when (currentTag) {
                        "updated" -> updated += parser.text.orEmpty()
                        "content" -> contentHtml += parser.text.orEmpty()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        inEntry = false
                        val rawTag = linkHref.substringAfter("/releases/tag/", "")
                            .substringBefore('?')
                            .substringBefore('#')
                            .trim()
                        val tag = runCatching { URLDecoder.decode(rawTag, "UTF-8") }.getOrDefault(rawTag)
                        val version = tag.removePrefix("v").removePrefix("V")
                        if (version.isNotEmpty()) {
                            val prerelease = version.contains('-')
                            if (includePrerelease || !prerelease) {
                                val notes = if (contentHtml.isNotBlank()) {
                                    HtmlCompat.fromHtml(contentHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
                                        .toString()
                                        .trim()
                                } else {
                                    ""
                                }
                                entries += AtomReleaseEntry(
                                    tag = tag,
                                    info = UpdateInfo(
                                        version = version,
                                        notes = notes,
                                        apkUrl = null,
                                        pageUrl = linkHref.ifBlank { "$REPOSITORY_URL/releases/tag/$tag" },
                                        prerelease = prerelease,
                                        publishedAt = updated.trim(),
                                    ),
                                )
                            }
                        }
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }
        return entries.sortedWith { a, b -> compareVersion(b.info.version, a.info.version) }
    }

    private fun fetchApkUrlFromExpandedAssets(tag: String, version: String): String? {
        val req = Request.Builder()
            .url("$REPOSITORY_URL/releases/expanded_assets/$tag")
            .header("User-Agent", "BiliCO-Android")
            .build()
        val html = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body.string()
        }
        val preferredName = "BiliCO-$version.apk"
        val candidates = buildList {
            for (match in EXPANDED_ASSET_HREF.findAll(html)) {
                val path = match.groupValues[1]
                val fullUrl = "https://github.com$path"
                val fileName = path.substringAfterLast('/')
                if (fileName.contains("unsigned", ignoreCase = true) ||
                    !fullUrl.startsWith("$REPOSITORY_URL/releases/download/")
                ) continue
                if (fileName == preferredName) return fullUrl
                add(fullUrl)
            }
        }
        return candidates.firstOrNull()
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        version: String,
        force: Boolean = false,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, "BiliCO-$safeVersion.apk")

        dir.listFiles()?.forEach { file ->
            if (!file.name.startsWith("BiliCO-$safeVersion.apk")) {
                file.delete()
            } else if (force) {
                file.delete()
            }
        }
        if (!force && target.exists() && target.length() > 1_000_000L && isValidApk(context, target)) {
            val len = target.length()
            onProgress(1f, len, len)
            return@withContext target
        }

        val candidates = raceMirrors(url)
        var lastError: Throwable? = null
        for (candidate in candidates) {
            coroutineContext.ensureActive()
            try {
                downloadFromCandidate(candidate, target, onProgress)
                verifyApkOrThrow(context, target)
                return@withContext target
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IOException("下载更新包失败")
    }

    private fun isValidApk(context: Context, file: File): Boolean = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        info != null && info.packageName == context.packageName
    }.getOrDefault(false)

    private fun verifyApkOrThrow(context: Context, file: File) {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (archive == null) {
            file.delete()
            throw IOException("安装包已损坏或下载不完整")
        }
        if (archive.packageName != context.packageName) {
            file.delete()
            throw IOException("安装包签名或包名不匹配 (${archive.packageName})")
        }
    }

    private suspend fun raceMirrors(url: String): List<String> = coroutineScope {
        val all = MIRRORS.map { prefix -> if (prefix.isEmpty()) url else prefix + url }
        val jobs = all.map { candidate ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    runCatching {
                        val req = Request.Builder()
                            .url(candidate)
                            .header("User-Agent", "BiliCO-Android")
                            .header("Range", "bytes=0-${PROBE_BYTES - 1}")
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@runCatching null
                            val stream = resp.body?.byteStream() ?: return@runCatching null
                            val buf = ByteArray(16 * 1024)
                            var got = 0L
                            while (got < PROBE_BYTES) {
                                val n = stream.read(buf)
                                if (n < 0) break
                                got += n
                            }
                            if (got >= PROBE_BYTES / 2) candidate else null
                        }
                    }.getOrNull()
                }
            }
        }
        val pending = jobs.toMutableList()
        var winner: String? = null
        while (pending.isNotEmpty() && winner == null) {
            val finished = select {
                pending.forEach { job -> job.onAwait { job to it } }
            }
            pending.remove(finished.first)
            if (finished.second != null) winner = finished.second
        }
        pending.forEach { it.cancel() }
        if (winner != null) listOf(winner) + all.filter { it != winner } else all
    }

    private data class ProbeResult(val total: Long, val ranged: Boolean)

    private fun probe(url: String): ProbeResult {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "BiliCO-Android")
            .header("Range", "bytes=0-0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            if (resp.code == 206) {
                val cr = resp.header("Content-Range").orEmpty()
                val slash = cr.lastIndexOf('/')
                val total = if (slash >= 0) cr.substring(slash + 1).trim().toLongOrNull() ?: -1L else -1L
                if (total > 0L) return ProbeResult(total, true)
            }
            val len = resp.body?.contentLength() ?: -1L
            return ProbeResult(len, false)
        }
    }

    private suspend fun downloadFromCandidate(
        url: String,
        target: File,
        onProgress: (Float, Long, Long) -> Unit,
    ) {
        val info = probe(url)
        if (info.ranged && info.total > SEGMENTS * 256 * 1024L) {
            downloadSegmented(url, target, info.total, onProgress)
        } else {
            val part = File(target.parentFile, "${target.name}.part")
            downloadSingle(url, part, target, onProgress)
        }
    }

    private suspend fun downloadSegmented(
        url: String,
        target: File,
        total: Long,
        onProgress: (Float, Long, Long) -> Unit,
    ) = coroutineScope {
        val parts = (0 until SEGMENTS).map { i -> File(target.parentFile, "${target.name}.part$i") }
        val chunk = total / SEGMENTS
        val ranges = (0 until SEGMENTS).map { i ->
            val start = i * chunk
            val end = if (i == SEGMENTS - 1) total - 1 else (i + 1) * chunk - 1
            start to end
        }

        val counter = AtomicLong(0L)
        for (i in 0 until SEGMENTS) {
            val expected = ranges[i].second - ranges[i].first + 1
            val len = if (parts[i].exists()) parts[i].length() else 0L
            if (len > expected) {
                parts[i].delete()
            } else {
                counter.addAndGet(len)
            }
        }
        onProgress((counter.get().toFloat() / total).coerceIn(0f, 1f), counter.get(), total)

        val lastTick = AtomicLong(0L)
        fun report(force: Boolean = false) {
            val now = System.currentTimeMillis()
            val prev = lastTick.get()
            if (!force && now - prev < 80L) return
            if (!force && !lastTick.compareAndSet(prev, now)) return
            if (force) lastTick.set(now)
            val done = counter.get()
            onProgress((done.toFloat() / total).coerceIn(0f, 1f), done, total)
        }

        (0 until SEGMENTS).map { i ->
            async(Dispatchers.IO) {
                val (start, end) = ranges[i]
                val expected = end - start + 1
                val part = parts[i]
                val existing = if (part.exists()) part.length() else 0L
                if (existing >= expected) return@async
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "BiliCO-Android")
                    .header("Range", "bytes=${start + existing}-$end")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code != 206) throw IOException("分段下载被拒绝 (HTTP ${resp.code})")
                    val body = resp.body ?: throw IOException("空响应")
                    FileOutputStream(part, existing > 0L).use { out ->
                        val buf = ByteArray(64 * 1024)
                        val stream = body.byteStream()
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = stream.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            counter.addAndGet(read.toLong())
                            report()
                        }
                    }
                }
                if (part.length() != expected) {
                    throw IOException("分段 $i 长度不符：${part.length()} / $expected")
                }
            }
        }.awaitAll()

        report(force = true)

        val merged = File(target.parentFile, "${target.name}.merged")
        FileOutputStream(merged, false).use { out ->
            val buf = ByteArray(128 * 1024)
            for (part in parts) {
                FileInputStream(part).use { input ->
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                    }
                }
            }
        }
        parts.forEach { it.delete() }
        if (target.exists()) target.delete()
        if (!merged.renameTo(target)) {
            merged.copyTo(target, overwrite = true)
            merged.delete()
        }
        onProgress(1f, total, total)
    }

    private suspend fun downloadSingle(
        url: String,
        part: File,
        target: File,
        onProgress: (Float, Long, Long) -> Unit,
    ) {
        val existing = if (part.exists()) part.length() else 0L
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "BiliCO-Android")
        if (existing > 0L) {
            reqBuilder.header("Range", "bytes=$existing-")
        }
        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 416) {
                part.delete()
                return downloadSingle(url, part, target, onProgress)
            }
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("空响应")
            val append = resp.code == 206 && existing > 0L
            val contentLen = body.contentLength()
            val total = if (contentLen > 0) contentLen + (if (append) existing else 0L) else -1L
            var downloaded = if (append) existing else 0L
            onProgress(if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f, downloaded, total)

            var lastEmit = 0L
            FileOutputStream(part, append).use { out ->
                val buffer = ByteArray(64 * 1024)
                val stream = body.byteStream()
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= 80L) {
                        lastEmit = now
                        val p = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                        onProgress(p, downloaded, total)
                    }
                }
            }
            if (total > 0 && downloaded < total) {
                throw IOException("下载未完成：$downloaded / $total")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            onProgress(1f, downloaded, total)
        }
    }

    fun openDownload(context: Context, info: UpdateInfo) {
        val target = info.apkUrl ?: info.pageUrl
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

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
    private val context: Context,
    private val settings: SettingsRepository,
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
            val info = AppUpdater.latest(current, includePrerelease = includePre)
            settings.setUpdateCheckedAt(System.currentTimeMillis())
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
        if (message.isBlank()) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
