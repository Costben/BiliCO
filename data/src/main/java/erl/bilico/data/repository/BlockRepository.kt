package erl.bilico.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import erl.bilico.data.model.BlockConfig
import erl.bilico.data.model.BlockedUser
import erl.bilico.data.model.BlockedVideo
import erl.bilico.data.model.Comment
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class BlockKeywordScope(val label: String) {
    VIDEO_TITLE("视频标题"),
    UP_NAME("UP 主名称"),
    COMMENT("评论内容"),
    DYNAMIC("动态内容"),
}

private class KeywordMatcher(val raw: String) {
    private val regex: Regex? = runCatching {
        Regex(raw, setOf(RegexOption.IGNORE_CASE))
    }.getOrNull()

    fun matches(text: String): Boolean {
        if (text.isBlank()) return false
        return regex?.containsMatchIn(text) ?: text.contains(raw, ignoreCase = true)
    }
}

data class BlockSnapshot(
    val config: BlockConfig = BlockConfig(),
    val serverBlackUsers: List<BlockedUser> = emptyList(),
    val sessionDislikedBvids: Set<String> = emptySet(),
    val syncingBlacklist: Boolean = false,
) {
    val blockedBvids: Set<String> = config.blockedVideos.mapTo(HashSet()) { it.bvid } + sessionDislikedBvids
    val localBlockedMids: Set<Long> = config.blockedUsers.mapTo(HashSet()) { it.mid }
    val serverBlackMids: Set<Long> = serverBlackUsers.mapTo(HashSet()) { it.mid }
    val allBlockedMids: Set<Long> = localBlockedMids + serverBlackMids

    private val videoMatchers = config.videoKeywords.filter { it.isNotBlank() }.map(::KeywordMatcher)
    private val upMatchers = config.upKeywords.filter { it.isNotBlank() }.map(::KeywordMatcher)
    private val commentMatchers = config.commentKeywords.filter { it.isNotBlank() }.map(::KeywordMatcher)
    private val dynamicMatchers = config.dynamicKeywords.filter { it.isNotBlank() }.map(::KeywordMatcher)

    fun isVideoBlocked(bvid: String): Boolean = bvid.isNotBlank() && bvid in blockedBvids

    fun isMidBlocked(mid: Long): Boolean = mid > 0L && mid in allBlockedMids

    fun isServerBlacklisted(mid: Long): Boolean = mid > 0L && mid in serverBlackMids

    fun matchesVideoTitle(title: String): Boolean = videoMatchers.any { it.matches(title) }

    fun matchesUpName(name: String): Boolean = upMatchers.any { it.matches(name) }

    fun matchesComment(text: String): Boolean = commentMatchers.any { it.matches(text) }

    fun matchesDynamic(text: String): Boolean = dynamicMatchers.any { it.matches(text) }

    fun shouldShowFeedVideo(video: FeedVideo, isRelated: Boolean = false): Boolean {
        if (isVideoBlocked(video.bvid)) return false
        if (isMidBlocked(video.upMid)) return false
        val followedExempt = config.exemptFollowed && video.isFollowed
        if (!followedExempt) {
            if (matchesVideoTitle(video.title)) return false
            if (matchesUpName(video.upName)) return false
        }
        if (isRelated && !config.applyToRelatedVideos) return true
        if (followedExempt) return true
        if (config.minDurationSec > 0 && video.durationSec > 0 && video.durationSec < config.minDurationSec) {
            return false
        }
        if (config.minPlayCount > 0 && video.playCount > 0 && video.playCount < config.minPlayCount) {
            return false
        }
        if (config.minLikeRatioPercent > 0 && video.playCount > 0) {
            if (video.likeCount * 100 < video.playCount * config.minLikeRatioPercent) return false
        }
        return true
    }

    fun shouldShowSearchVideo(video: SearchVideo): Boolean {
        if (isVideoBlocked(video.bvid)) return false
        if (isMidBlocked(video.upMid)) return false
        if (matchesVideoTitle(video.title)) return false
        if (matchesUpName(video.author)) return false
        return true
    }

    fun shouldShowDynamic(item: DynamicItemUi): Boolean {
        if (isMidBlocked(item.authorMid)) return false
        if (matchesUpName(item.authorName)) return false
        val dynText = buildString {
            if (item.title.isNotBlank()) append(item.title).append('\n')
            if (item.content.isNotBlank()) append(item.content)
        }
        if (matchesDynamic(dynText)) return false
        val video = item.video
        if (video != null) {
            if (isVideoBlocked(video.bvid)) return false
            if (matchesVideoTitle(video.title)) return false
        }
        val forward = item.forward
        if (forward != null && !shouldShowDynamic(forward)) return false
        return true
    }

    fun shouldShowComment(comment: Comment): Boolean {
        if (isMidBlocked(comment.mid)) return false
        if (matchesUpName(comment.userName)) return false
        if (matchesComment(comment.content)) return false
        return true
    }

    fun filterCommentTree(comment: Comment): Comment? {
        if (!shouldShowComment(comment)) return null
        if (comment.replies.isEmpty()) return comment
        val filteredReplies = comment.replies.mapNotNull(::filterCommentTree)
        return if (filteredReplies.size == comment.replies.size) {
            comment
        } else {
            comment.copy(replies = filteredReplies)
        }
    }
}

/** 全局屏蔽与推荐过滤仓库：持久化本地规则，并可选同步 B 站账号黑名单。 */
class BlockRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _snapshot = MutableStateFlow(BlockSnapshot())
    val snapshot: StateFlow<BlockSnapshot> = _snapshot.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val raw = context.biliDataStore.data.first()[KEY_BLOCK_CONFIG]
            if (!raw.isNullOrBlank()) {
                runCatching { json.decodeFromString<BlockConfig>(raw) }
                    .getOrNull()
                    ?.let { loaded -> _snapshot.update { it.copy(config = loaded) } }
            }
            session.account
                .map { it?.mid ?: 0L }
                .distinctUntilChanged()
                .collectLatest { mid ->
                    if (mid > 0L && session.isLoggedIn()) {
                        syncServerBlacklist()
                    } else {
                        _snapshot.update { it.copy(serverBlackUsers = emptyList(), syncingBlacklist = false) }
                    }
                }
        }
    }

    suspend fun syncServerBlacklist(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!session.isLoggedIn()) {
                _snapshot.update { it.copy(serverBlackUsers = emptyList(), syncingBlacklist = false) }
                return@runCatching
            }
            _snapshot.update { it.copy(syncingBlacklist = true) }
            try {
                buvid.ensure()
                val collected = mutableListOf<BlockedUser>()
                var page = 1
                while (page <= 5) {
                    val res = api.blackList(
                        mapOf(
                            "pn" to page.toString(),
                            "ps" to "50",
                            "re_version" to "0",
                            "jsonp" to "jsonp",
                        ),
                    )
                    if (res.code != 0) {
                        error(res.message.ifBlank { "黑名单获取失败(${res.code})" })
                    }
                    val list = res.data?.list.orEmpty()
                    list.forEach { user ->
                        if (user.mid > 0L) {
                            collected += BlockedUser(
                                mid = user.mid,
                                name = user.uname,
                                face = erl.bilico.data.util.Formatters.normalizeImageUrl(user.face),
                            )
                        }
                    }
                    val total = res.data?.total ?: 0
                    if (list.isEmpty() || collected.size >= total) break
                    page += 1
                }
                _snapshot.update {
                    it.copy(
                        serverBlackUsers = collected.distinctBy { u -> u.mid },
                        syncingBlacklist = false,
                    )
                }
            } finally {
                _snapshot.update { it.copy(syncingBlacklist = false) }
            }
        }
    }

    fun blockVideo(
        bvid: String,
        title: String = "",
        upName: String = "",
        upMid: Long = 0L,
    ) {
        val clean = bvid.trim()
        if (clean.isEmpty()) return
        updateConfig { cfg ->
            if (cfg.blockedVideos.any { it.bvid == clean }) return@updateConfig cfg
            val entry = BlockedVideo(
                bvid = clean,
                title = title,
                upName = upName,
                upMid = upMid,
                addedAtMs = System.currentTimeMillis(),
            )
            cfg.copy(blockedVideos = listOf(entry) + cfg.blockedVideos)
        }
    }

    fun unblockVideo(bvid: String) {
        updateConfig { cfg ->
            cfg.copy(blockedVideos = cfg.blockedVideos.filterNot { it.bvid == bvid })
        }
    }

    fun markSessionDisliked(bvid: String) {
        val clean = bvid.trim()
        if (clean.isEmpty()) return
        _snapshot.update { snap ->
            snap.copy(sessionDislikedBvids = snap.sessionDislikedBvids + clean)
        }
    }

    fun unmarkSessionDisliked(bvid: String) {
        _snapshot.update { snap ->
            snap.copy(sessionDislikedBvids = snap.sessionDislikedBvids - bvid)
        }
    }

    fun blockUserLocally(
        mid: Long,
        name: String = "",
        face: String = "",
    ) {
        if (mid <= 0L) return
        updateConfig { cfg ->
            if (cfg.blockedUsers.any { it.mid == mid }) return@updateConfig cfg
            val entry = BlockedUser(
                mid = mid,
                name = name,
                face = face,
                addedAtMs = System.currentTimeMillis(),
            )
            cfg.copy(blockedUsers = listOf(entry) + cfg.blockedUsers)
        }
    }

    fun unblockUserLocally(mid: Long) {
        updateConfig { cfg ->
            cfg.copy(blockedUsers = cfg.blockedUsers.filterNot { it.mid == mid })
        }
    }

    suspend fun toggleServerBlacklist(
        mid: Long,
        add: Boolean,
        name: String = "",
        face: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(mid > 0L) { "无效用户 ID" }
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            buvid.ensure()
            val response = api.modifyRelation(
                mapOf(
                    "fid" to mid.toString(),
                    "act" to if (add) "5" else "6",
                    "re_src" to "11",
                    "csrf" to csrf,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "黑名单操作失败(${response.code})" })
            }
            _snapshot.update { snap ->
                val updated = if (add) {
                    val user = BlockedUser(mid = mid, name = name, face = face)
                    (listOf(user) + snap.serverBlackUsers).distinctBy { it.mid }
                } else {
                    snap.serverBlackUsers.filterNot { it.mid == mid }
                }
                snap.copy(serverBlackUsers = updated)
            }
        }
    }

    fun addKeyword(scope: BlockKeywordScope, keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        updateConfig { cfg ->
            when (scope) {
                BlockKeywordScope.VIDEO_TITLE -> if (clean in cfg.videoKeywords) cfg else cfg.copy(videoKeywords = listOf(clean) + cfg.videoKeywords)
                BlockKeywordScope.UP_NAME -> if (clean in cfg.upKeywords) cfg else cfg.copy(upKeywords = listOf(clean) + cfg.upKeywords)
                BlockKeywordScope.COMMENT -> if (clean in cfg.commentKeywords) cfg else cfg.copy(commentKeywords = listOf(clean) + cfg.commentKeywords)
                BlockKeywordScope.DYNAMIC -> if (clean in cfg.dynamicKeywords) cfg else cfg.copy(dynamicKeywords = listOf(clean) + cfg.dynamicKeywords)
            }
        }
    }

    fun removeKeyword(scope: BlockKeywordScope, keyword: String) {
        updateConfig { cfg ->
            when (scope) {
                BlockKeywordScope.VIDEO_TITLE -> cfg.copy(videoKeywords = cfg.videoKeywords.filterNot { it == keyword })
                BlockKeywordScope.UP_NAME -> cfg.copy(upKeywords = cfg.upKeywords.filterNot { it == keyword })
                BlockKeywordScope.COMMENT -> cfg.copy(commentKeywords = cfg.commentKeywords.filterNot { it == keyword })
                BlockKeywordScope.DYNAMIC -> cfg.copy(dynamicKeywords = cfg.dynamicKeywords.filterNot { it == keyword })
            }
        }
    }

    fun updateRecommendThresholds(
        minDurationSec: Int = _snapshot.value.config.minDurationSec,
        minPlayCount: Int = _snapshot.value.config.minPlayCount,
        minLikeRatioPercent: Int = _snapshot.value.config.minLikeRatioPercent,
        exemptFollowed: Boolean = _snapshot.value.config.exemptFollowed,
        applyToRelatedVideos: Boolean = _snapshot.value.config.applyToRelatedVideos,
    ) {
        updateConfig { cfg ->
            cfg.copy(
                minDurationSec = minDurationSec.coerceAtLeast(0),
                minPlayCount = minPlayCount.coerceAtLeast(0),
                minLikeRatioPercent = minLikeRatioPercent.coerceIn(0, 100),
                exemptFollowed = exemptFollowed,
                applyToRelatedVideos = applyToRelatedVideos,
            )
        }
    }

    private fun updateConfig(transform: (BlockConfig) -> BlockConfig) {
        var nextConfig: BlockConfig? = null
        _snapshot.update { current ->
            val updated = transform(current.config)
            nextConfig = updated
            current.copy(config = updated)
        }
        val toPersist = nextConfig ?: return
        scope.launch(Dispatchers.IO) {
            val encoded = json.encodeToString(toPersist)
            context.biliDataStore.edit { it[KEY_BLOCK_CONFIG] = encoded }
        }
    }

    private companion object {
        val KEY_BLOCK_CONFIG = stringPreferencesKey("block_config")
    }
}
