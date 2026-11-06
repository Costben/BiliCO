package erl.bilico.data.model

/** 推荐流卡片所需的字段，UI 不直接消费接口模型。 */
data class FeedVideo(
    val bvid: String,
    val aid: Long,
    val title: String,
    val cover: String,
    val upName: String,
    val upMid: Long,
    val upFace: String = "",
    val playCount: Long,
    val durationSec: Int,
    val playCountText: String,
    val durationText: String,
)

data class VideoPart(
    val cid: Long,
    val index: Int,
    val title: String,
    val durationSec: Long,
)

data class VideoDetail(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val desc: String,
    val cover: String,
    val pubdate: Long,
    val durationSec: Int,
    val upMid: Long,
    val upName: String,
    val upFace: String,
    val view: Long,
    val danmaku: Long,
    val reply: Long,
    val like: Long,
    val coin: Long,
    val favorite: Long,
    val pages: List<VideoPart>,
)

data class QualityOption(
    val quality: Int,
    val label: String,
    val needLogin: Boolean = false,
    val needVip: Boolean = false,
)

data class PlaybackSource(
    val videoUrl: String,
    val videoBackupUrls: List<String>,
    val audioUrl: String?,
    val audioBackupUrls: List<String>,
    val quality: Int,
    val qualities: List<QualityOption>,
    val durationMs: Long,
    val lastPlayTimeMs: Long = 0L,
    val lastPlayCid: Long = 0L,
)

data class EmoteImage(
    val url: String,
    val large: Boolean = false,
)

data class Comment(
    val id: Long,
    val rootId: Long,
    val parentId: Long = 0L,
    val userName: String,
    val avatar: String,
    val level: Int,
    val timeSec: Long,
    val content: String,
    val like: Int,
    val liked: Boolean = false,
    val replyCount: Int,
    val mid: Long = 0L,
    val emotes: Map<String, EmoteImage> = emptyMap(),
    val replies: List<Comment> = emptyList(),
)

data class CommentPage(
    val comments: List<Comment>,
    val nextOffset: String?,
    val isEnd: Boolean,
    val total: Int,
)

@kotlinx.serialization.Serializable
data class Account(
    val mid: Long,
    val name: String,
    val face: String,
)

enum class DynamicKind {
    VIDEO,
    IMAGES,
    TEXT,
    FORWARD,
    UNSUPPORTED,
}

data class DynamicImage(
    val url: String,
    val width: Int,
    val height: Int,
)

data class DynamicVideoCard(
    val bvid: String,
    val title: String,
    val cover: String,
    val durationText: String,
)

/** 卡片点开后要跳去哪：有 cv 号走专栏，否则把这个动态自己的 id 交给阅读页。 */
data class DynamicArticleCard(
    val articleId: Long = 0L,
    val title: String = "",
    val dynamicId: String = "",
)

data class DynamicItemUi(
    val id: String,
    val kind: DynamicKind,
    val authorMid: Long = 0L,
    val authorName: String,
    val authorFace: String,
    val timeSec: Long,
    val pubTimeText: String,
    val content: String,
    val images: List<DynamicImage> = emptyList(),
    val video: DynamicVideoCard? = null,
    /** 专栏或图文长文：卡片右下角带「阅读」标记，点开进阅读页。 */
    val article: DynamicArticleCard? = null,
    val forward: DynamicItemUi? = null,
    val forwardCount: Int = 0,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
)

data class HistoryItem(
    val key: String = "",
    val bvid: String,
    val cid: Long = 0L,
    val seasonId: Long = 0L,
    val epId: Long = 0L,
    val isPgc: Boolean = false,
    val title: String,
    val cover: String,
    val upName: String,
    val viewAtSec: Long,
    val progressSec: Int,
    val durationSec: Int,
)

data class LiveRoomItem(
    val roomId: Long,
    val title: String,
    val cover: String,
    val anchor: String,
    val online: Long,
    val areaName: String,
)

data class LiveRoomDetail(
    val roomId: Long,
    val title: String,
    val cover: String,
    val anchorName: String,
    val anchorFace: String,
    val online: Long,
    val description: String,
    val areaName: String,
    val isLive: Boolean,
)

data class LivePlayback(
    /** 同一直播间按偏好排好序的多条线路，第一条失败就换下一条。 */
    val candidates: List<LiveCandidate>,
    val qualities: List<QualityOption>,
    val activeQuality: Int,
) {
    val url: String get() = candidates.first().url
    val isHls: Boolean get() = candidates.first().isHls
}

data class LiveCandidate(
    val url: String,
    val isHls: Boolean,
)

// ---------- 搜索 / 空间 / 收藏 / 关注 ----------

data class SearchVideo(
    val bvid: String,
    val title: String,
    val cover: String,
    val author: String,
    val upMid: Long,
    val playText: String,
    val durationText: String,
    val pubdateText: String,
)

data class SearchUser(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val fansText: String,
    val videoCountText: String,
)

data class SearchArticle(
    val id: Long,
    val title: String,
    val cover: String,
    val author: String,
    val upMid: Long,
    val categoryName: String,
    val viewText: String,
    val likeText: String,
    val desc: String,
)

data class SpaceProfile(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val followersText: String,
    val followingText: String,
    val likesText: String,
)

/** 空间投稿、收藏夹内容、稍后再看、历史共用的一张视频条目。 */
data class LibraryVideo(
    val bvid: String,
    val title: String,
    val cover: String,
    val upName: String,
    val playText: String = "",
    val durationText: String = "",
    val durationSec: Int = 0,
    val progressSec: Int = 0,
)

data class FavoriteFolder(
    val mediaId: Long,
    val title: String,
    val cover: String,
    val countText: String,
)

data class FollowingUser(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
)

data class QrLogin(
    val key: String,
    val url: String,
)

enum class QrLoginState {
    WAITING,
    SCANNED,
    EXPIRED,
    SUCCESS,
}
