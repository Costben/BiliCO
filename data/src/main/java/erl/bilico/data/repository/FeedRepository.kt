package erl.bilico.data.repository

import erl.bilico.data.model.AppFeedItem
import erl.bilico.data.model.FeedDislikeReason
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.RecommendItem
import erl.bilico.data.network.AppSignUtils
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.TokenRefreshHelper
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.session.CookieStore
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.settings.FeedPreferences
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class FeedRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
    private val cookies: CookieStore,
    private val tokenRefresh: TokenRefreshHelper,
    private val settings: SettingsRepository,
) {
    data class FeedUpProfile(
        val face: String,
        val officialType: Int,
    )

    private val upProfileCache = object : LinkedHashMap<Long, FeedUpProfile>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FeedUpProfile>?): Boolean =
            size > MAX_PROFILE_CACHE_SIZE
    }
    private val upProfileLock = Any()
    /** 正在请求中的 mid，下拉刷新与翻页同时触发补全时只让一个协程去要。 */
    private val upProfilePending = ConcurrentHashMap.newKeySet<Long>()
    /** 接口没返回头像的 mid → 冷却到期时间，避免每一页都重复请求同一批人。 */
    private val upProfileUnavailable = ConcurrentHashMap<Long, Long>()

    @Volatile
    private var appFeedCursorIdx: Long = 0L

    /**
     * 首页推荐：根据设置选择「App 端推荐」或「网页端推荐」。
     * App 端推荐附带每张卡片的「我不想看 / 反馈」动态理由，异常或空数据时自动回退 Web 端 `rcmd`。
     * [count] 非空时按目标条数拉取（用于下拉刷新控制单次拉取数量）。
     */
    suspend fun recommend(page: Int, count: Int? = null): Result<List<FeedVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            settings.loaded.first { it }
            buvid.ensure()
            if (settings.feedSource.value == 1) {
                return@runCatching fetchWebRecommend(page, count)
            }
            val appItems = runCatching { fetchAppRecommend(page, count) }.getOrNull().orEmpty()
            if (appItems.isNotEmpty()) return@runCatching appItems
            fetchWebRecommend(page, count)
        }
    }

    private suspend fun fetchAppRecommend(page: Int, count: Int?): List<FeedVideo> {
        val accessKey = session.readTokens().first
        val targetCount = count?.coerceIn(1, FeedPreferences.MAX_REFRESH_COUNT)
        val maxRequests = if (targetCount == null) 1 else MAX_APP_BATCH_REQUESTS
        val collected = ArrayList<FeedVideo>(targetCount ?: 12)
        val seenBvids = HashSet<String>()
        var cursorIdx = if (page <= 1) 0L else appFeedCursorIdx

        for (attempt in 0 until maxRequests) {
            val isInitialPull = page <= 1 && attempt == 0
            val reqIdx = if (isInitialPull) {
                "0"
            } else {
                cursorIdx.takeIf { it > 0L }?.toString() ?: (page + attempt).toString()
            }
            val params = buildMap {
                put("build", APP_BUILD)
                put("c_locale", "zh_CN")
                put("channel", "master")
                put("column", "2")
                put("device", "phone")
                put("device_name", "android")
                put("device_type", "0")
                put("disable_rcmd", "0")
                put("flush", "8")
                put("fnval", "976")
                put("fnver", "0")
                put("force_host", "2")
                put("fourk", "1")
                put("guidance", "1")
                put("https_url_req", "1")
                put("idx", reqIdx)
                put("mobi_app", "android")
                put("network", "wifi")
                put("platform", "android")
                put("player_net", "1")
                put("pull", if (isInitialPull) "true" else "false")
                put("qn", "32")
                put("recsys_mode", "0")
                put("s_locale", "zh_CN")
                put("splash_id", "")
                put("voice_balance", "0")
                put("ts", AppSignUtils.getTimestamp().toString())
                if (!accessKey.isNullOrBlank()) put("access_key", accessKey)
            }
            val signed = AppSignUtils.signForTvApi(params)
            val response = api.appRecommend(
                buvid = cookies.get("buvid3").orEmpty(),
                params = signed,
            )
            if (response.code != 0) {
                if (attempt == 0) {
                    error(response.message.ifBlank { "App推荐加载失败(${response.code})" })
                }
                break
            }
            val rawItems = response.data?.items.orEmpty()
            val nextIdx = rawItems.lastOrNull { it.idx > 0L }?.idx
            if (nextIdx != null) cursorIdx = nextIdx

            rawItems.asSequence()
                .filter { it.adInfo == null && (it.cardGoto == "av" || it.goto == "av") }
                .mapNotNull { it.toFeedVideo() }
                .forEach { video ->
                    if (seenBvids.add(video.bvid)) collected += video
                }

            if (targetCount == null || collected.size >= targetCount || rawItems.isEmpty()) {
                break
            }
        }
        if (cursorIdx > 0L) appFeedCursorIdx = cursorIdx

        val result = if (targetCount != null) collected.take(targetCount) else collected
        // 仅同步套用已命中的内存缓存，未命中的头像交由调用方后台异步回填，避免阻塞推荐流首屏渲染。
        return applyCachedUpProfiles(result)
    }

    private fun applyCachedUpProfiles(videos: List<FeedVideo>): List<FeedVideo> =
        videos.map { video ->
            if (video.upFace.isNotBlank() || video.upMid <= 0L) return@map video
            val profile = cachedUpProfile(video.upMid) ?: return@map video
            video.copy(
                upFace = profile.face,
                officialType = if (video.officialType == -1) profile.officialType else video.officialType,
            )
        }

    private fun cachedUpProfile(mid: Long): FeedUpProfile? = synchronized(upProfileLock) {
        upProfileCache[mid]
    }

    private fun storeUpProfile(mid: Long, profile: FeedUpProfile) {
        synchronized(upProfileLock) { upProfileCache[mid] = profile }
    }

    private fun isUpProfileCoolingDown(mid: Long): Boolean {
        val retryAt = upProfileUnavailable[mid] ?: return false
        if (System.currentTimeMillis() < retryAt) return true
        upProfileUnavailable.remove(mid)
        return false
    }

    private fun markUpProfileUnavailable(mids: Collection<Long>) {
        if (mids.isEmpty()) return
        if (upProfileUnavailable.size > MAX_PROFILE_CACHE_SIZE) upProfileUnavailable.clear()
        val retryAt = System.currentTimeMillis() + UNAVAILABLE_RETRY_MS
        mids.forEach { upProfileUnavailable[it] = retryAt }
    }

    /**
     * App 端双列卡片（small_cover_v2）不下发 UP 主头像。
     * 供 ViewModel 在卡片展示后异步批量拉取缺失的头像与认证角标，失败时静默兜底不影响推荐流。
     */
    suspend fun fetchMissingUpProfiles(mids: Collection<Long>): Map<Long, FeedUpProfile> =
        withContext(Dispatchers.IO) {
            val distinctMids = mids.asSequence().filter { it > 0L }.distinct().toList()
            if (distinctMids.isEmpty()) return@withContext emptyMap()
            // 冷却期内或别人正在请求的直接跳过，只认领自己负责的那部分。
            val claimed = distinctMids.filter {
                cachedUpProfile(it) == null && !isUpProfileCoolingDown(it) && upProfilePending.add(it)
            }
            try {
                claimed.chunked(PROFILE_BATCH_SIZE).forEach { chunk ->
                    val data = runCatching {
                        val response = api.liveMultipleUsers(uids = chunk)
                        if (response.code == 0) response.data.orEmpty() else emptyMap()
                    }.getOrNull().orEmpty()
                    val resolved = HashSet<Long>()
                    data.forEach { (midKey, entry) ->
                        val info = entry.info ?: return@forEach
                        val mid = info.uid.takeIf { it > 0L } ?: midKey.toLongOrNull() ?: return@forEach
                        val face = Formatters.normalizeImageUrl(info.face)
                        if (face.isNotBlank()) {
                            storeUpProfile(
                                mid,
                                FeedUpProfile(
                                    face = face,
                                    officialType = info.officialVerify?.resolvedType() ?: -1,
                                ),
                            )
                            resolved += mid
                        }
                    }
                    markUpProfileUnavailable(chunk.filterNot { it in resolved })
                }
            } finally {
                upProfilePending.removeAll(claimed.toSet())
            }
            buildMap {
                distinctMids.forEach { mid ->
                    cachedUpProfile(mid)?.let { put(mid, it) }
                }
            }
        }

    private suspend fun fetchWebRecommend(page: Int, count: Int?): List<FeedVideo> {
        val keys = wbi.getKeys()
        val index = page.coerceAtLeast(1).toString()
        val pageSize = (count ?: DEFAULT_WEB_PAGE_SIZE).coerceIn(1, FeedPreferences.MAX_REFRESH_COUNT)
        val params = mapOf(
            "ps" to pageSize.toString(),
            "fresh_idx" to index,
            "fresh_idx_1h" to index,
            "feed_version" to "V8",
            "fresh_type" to "3",
            "homepage_ver" to "1",
            "web_location" to "1430650",
        )
        val response = api.recommend(WbiUtils.sign(params, keys.first, keys.second))
        if (response.code != 0) {
            error(response.message.ifBlank { "推荐加载失败(${response.code})" })
        }
        return response.data?.item.orEmpty()
            .filter { it.goto == "av" || it.goto.isNullOrBlank() }
            .mapNotNull { it.toFeedVideo() }
            .take(pageSize)
    }

    /** 提交推荐卡片「我不想看 / 反馈」到 B 站服务器（`app.bilibili.com/x/feed/dislike`）。 */
    suspend fun feedDislike(
        video: FeedVideo,
        reason: FeedDislikeReason,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!session.isLoggedIn()) {
                error("请先登录后再提交不感兴趣反馈")
            }
            val token = session.ensureAccessToken()
                ?: return@runCatching "已隐藏此视频（当前仅含网页 Cookie，在「我的」重新扫码登录后可同步推荐偏好）"
            val targetId = video.appParam.takeIf { it > 0L } ?: video.aid
            require(targetId > 0L) { "无效视频 ID" }
            val goto = video.cardGoto.ifBlank { "av" }

            suspend fun submitWith(accessKey: String) = api.appFeedDislike(
                AppSignUtils.signForTvApi(
                    buildMap {
                        put("goto", goto)
                        put("id", targetId.toString())
                        put("access_key", accessKey)
                        put("build", APP_BUILD)
                        put("mobi_app", "android")
                        put("ts", AppSignUtils.getTimestamp().toString())
                        if (reason.isFeedback) {
                            put("feedback_id", reason.id.toString())
                        } else {
                            put("reason_id", reason.id.toString())
                        }
                    },
                ),
            )

            var response = submitWith(token)
            if (response.code == -101) {
                if (tokenRefresh.refreshIfPossible()) {
                    val refreshed = session.readTokens().first
                    if (!refreshed.isNullOrBlank()) response = submitWith(refreshed)
                } else {
                    session.clearTokens()
                    val reissued = session.ensureAccessToken()
                    if (!reissued.isNullOrBlank()) response = submitWith(reissued)
                }
            }
            if (response.code == -101) {
                return@runCatching "已隐藏此视频（当前 App 授权已失效，在「我的」重新扫码登录后可同步推荐偏好）"
            }
            if (response.code != 0) {
                error(response.message.ifBlank { "提交失败(${response.code})" })
            }
            reason.toast.ifBlank { "将减少相似内容推荐" }
        }
    }

    /** 撤销推荐卡片「我不想看」（`app.bilibili.com/x/feed/dislike/cancel`）。 */
    suspend fun feedDislikeCancel(video: FeedVideo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = session.ensureAccessToken() ?: error("请先登录")
            val targetId = video.appParam.takeIf { it > 0L } ?: video.aid
            require(targetId > 0L) { "无效视频 ID" }
            val response = api.appFeedDislikeCancel(
                AppSignUtils.signForTvApi(
                    mapOf(
                        "goto" to video.cardGoto.ifBlank { "av" },
                        "id" to targetId.toString(),
                        "access_key" to token,
                        "build" to APP_BUILD,
                        "mobi_app" to "android",
                        "ts" to AppSignUtils.getTimestamp().toString(),
                    ),
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "撤销失败(${response.code})" })
            }
        }
    }

    private fun AppFeedItem.toFeedVideo(): FeedVideo? {
        val aidValue = playerArgs?.aid?.takeIf { it > 0L }
            ?: args?.aid?.takeIf { it > 0L }
            ?: param.toLongOrNull()
            ?: 0L
        val resolvedBvid = bvid.ifBlank { if (aidValue > 0L) Formatters.av2bv(aidValue) else "" }
        if (resolvedBvid.isBlank()) return null
        val normalizedCover = Formatters.normalizeImageUrl(cover)
        if (normalizedCover.isBlank()) return null

        val likeFromReason = if (rcmdReason.contains("赞")) {
            Formatters.parseStatCount(rcmdReason)
        } else {
            0L
        }
        val followed = rcmdReason == "已关注" || rcmdReason == "新关注"
        val durationSeconds = playerArgs?.duration ?: Formatters.parseDurationText(coverRightText)
        val durationLabel = if (durationSeconds > 0) {
            Formatters.formatDuration(durationSeconds.toLong())
        } else {
            coverRightText
        }

        val v2Dislike = threePointV2
            .firstOrNull { it.type == "dislike" }
            ?.reasons
            .orEmpty()
            .map { FeedDislikeReason(id = it.id, name = it.name, toast = it.toast, isFeedback = false) }
        val legacyDislike = threePoint?.dislikeReasons.orEmpty()
            .map { FeedDislikeReason(id = it.id, name = it.name, toast = it.toast, isFeedback = false) }
        val v2Feedback = threePointV2
            .firstOrNull { it.type == "feedback" }
            ?.reasons
            .orEmpty()
            .map { FeedDislikeReason(id = it.id, name = it.name, toast = it.toast, isFeedback = true) }
        val legacyFeedback = threePoint?.feedbacks.orEmpty()
            .map { FeedDislikeReason(id = it.id, name = it.name, toast = it.toast, isFeedback = true) }

        return FeedVideo(
            bvid = resolvedBvid,
            aid = aidValue,
            title = Formatters.decodeHtmlEntities(title),
            cover = normalizedCover,
            upName = args?.upName?.ifBlank { descButton?.text.orEmpty() }.orEmpty(),
            upMid = args?.upId ?: 0L,
            upFace = "",
            playCount = Formatters.parseStatCount(coverLeftText1),
            likeCount = likeFromReason,
            danmaku = Formatters.parseStatCount(coverLeftText2),
            durationSec = durationSeconds,
            playCountText = Formatters.statLabelOrBlank(coverLeftText1),
            danmakuText = Formatters.statLabelOrBlank(coverLeftText2),
            durationText = durationLabel,
            isFollowed = followed,
            appParam = param.toLongOrNull() ?: aidValue,
            cardGoto = cardGoto.ifBlank { goto.ifBlank { "av" } },
            fromAppFeed = true,
            dislikeReasons = v2Dislike.ifEmpty { legacyDislike },
            dislikeFeedbacks = v2Feedback.ifEmpty { legacyFeedback },
        )
    }

    private fun RecommendItem.toFeedVideo(): FeedVideo? {
        val videoBvid = bvid?.takeIf { it.isNotBlank() } ?: return null
        val cover = pic?.takeIf { it.isNotBlank() } ?: return null
        val viewCount = stat?.view ?: 0L
        val seconds = (duration ?: 0).toLong().coerceAtLeast(0L)
        return FeedVideo(
            bvid = videoBvid,
            aid = id,
            title = title.orEmpty(),
            cover = Formatters.normalizeImageUrl(cover),
            upName = owner?.name.orEmpty(),
            upMid = owner?.mid ?: 0L,
            upFace = Formatters.normalizeImageUrl(owner?.face),
            playCount = viewCount,
            likeCount = stat?.like ?: 0L,
            danmaku = stat?.danmaku ?: 0L,
            durationSec = duration ?: 0,
            playCountText = Formatters.formatStat(viewCount),
            danmakuText = Formatters.formatStat(stat?.danmaku ?: 0L),
            durationText = Formatters.formatDuration(seconds),
            isFollowed = isFollowed == 1,
        )
    }

    private companion object {
        const val APP_BUILD = "8430300"
        const val DEFAULT_WEB_PAGE_SIZE = 20
        const val MAX_APP_BATCH_REQUESTS = 5
        const val MAX_PROFILE_CACHE_SIZE = 2000
        /** 接口单次最多接受 50 个 uid。 */
        const val PROFILE_BATCH_SIZE = 50
        /** 接口没给头像的 UP 多久后再试一次。 */
        const val UNAVAILABLE_RETRY_MS = 10 * 60 * 1000L
    }
}
