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
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
    private val cookies: CookieStore,
    private val tokenRefresh: TokenRefreshHelper,
    private val settings: SettingsRepository,
) {
    /**
     * 首页推荐：根据设置选择「App 端推荐」或「网页端推荐」。
     * App 端推荐附带每张卡片的「我不想看 / 反馈」动态理由，异常或空数据时自动回退 Web 端 `rcmd`。
     */
    suspend fun recommend(page: Int): Result<List<FeedVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            if (settings.feedSource.value == 1) {
                return@runCatching fetchWebRecommend(page)
            }
            val appItems = runCatching { fetchAppRecommend(page) }.getOrNull().orEmpty()
            if (appItems.isNotEmpty()) return@runCatching appItems
            fetchWebRecommend(page)
        }
    }

    private suspend fun fetchAppRecommend(page: Int): List<FeedVideo> {
        val accessKey = session.readTokens().first
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
            put("idx", if (page <= 1) "0" else page.toString())
            put("mobi_app", "android")
            put("network", "wifi")
            put("platform", "android")
            put("player_net", "1")
            put("pull", if (page <= 1) "true" else "false")
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
            error(response.message.ifBlank { "App推荐加载失败(${response.code})" })
        }
        return response.data?.items.orEmpty()
            .filter { it.adInfo == null && (it.cardGoto == "av" || it.goto == "av") }
            .mapNotNull { it.toFeedVideo() }
    }

    private suspend fun fetchWebRecommend(page: Int): List<FeedVideo> {
        val keys = wbi.getKeys()
        val index = page.coerceAtLeast(1).toString()
        val params = mapOf(
            "ps" to "20",
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
            playCountText = coverLeftText1,
            danmakuText = coverLeftText2,
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
    }
}
