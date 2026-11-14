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
    val danmaku: Long = 0,
    val durationSec: Int,
    val playCountText: String,
    val danmakuText: String = "",
    val durationText: String,
)

data class VideoPart(
    val cid: Long,
    val index: Int,
    val title: String,
    val durationSec: Long,
)

/** 合集里的一集：换 bvid 就是另一条视频，同一 bvid 下按 cid 区分分 P。 */
data class VideoEpisode(
    val bvid: String,
    val cid: Long,
    val title: String,
    val durationSec: Long,
)

data class VideoCollection(
    val id: Long,
    val title: String,
    val episodes: List<VideoEpisode>,
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
    val collection: VideoCollection? = null,
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

/** 评论输入框表情面板里的一个表情：点一下往正文里插 [insert]。 */
data class CommentEmote(
    val text: String,
    val url: String,
    val large: Boolean = false,
    /** 纯文字表情（颜文字）直接插原文，图片表情插 `[名字]`。 */
    val insert: String = text,
)

data class CommentEmotePackage(
    val emotes: List<CommentEmote>,
)

data class CommentSailing(
    val imageUrl: String,
    val numPrefix: String = "",
    val numDesc: String = "",
    val fanColorHex: String = "",
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
    val disliked: Boolean = false,
    val replyCount: Int,
    val mid: Long = 0L,
    val location: String = "",
    val vipColorHex: String = "",
    val sailing: CommentSailing? = null,
    val subReplyEntryText: String = "",
    val isTop: Boolean = false,
    val isUp: Boolean = false,
    val emotes: Map<String, EmoteImage> = emptyMap(),
    val pictures: List<DynamicImage> = emptyList(),
    val replies: List<Comment> = emptyList(),
)

data class CommentPage(
    val comments: List<Comment>,
    val nextOffset: String?,
    val isEnd: Boolean,
    val total: Int,
)

/** 主楼评论排序：B 站 `mode=3` 按热度，`mode=2` 按时间。 */
enum class CommentSort(val mode: Int) {
    Hot(3),
    New(2),
}

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
    LIVE,
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
    val aid: Long = 0L,
)

/** 开播动态的直播间预览：封面、看过人数与分区名都直接来自接口。 */
data class DynamicLiveCard(
    val roomId: Long,
    val title: String,
    val cover: String,
    val areaName: String,
    val watchedText: String,
    val living: Boolean,
)

/** 动态卡片下方外露的精选/热评：整段文字已经拼好，表情单独给图。 */
data class DynamicHotReply(
    val text: String,
    val emotes: Map<String, EmoteImage> = emptyMap(),
)

/** 卡片点开后要跳去哪：有 cv 号走专栏，否则把这个动态自己的 id 交给阅读页。 */
data class DynamicArticleCard(
    val articleId: Long = 0L,
    val title: String = "",
    val dynamicId: String = "",
)

data class DynamicItemUi(
    val id: String,
    val dynamicId: String = "",
    val kind: DynamicKind,
    val authorMid: Long = 0L,
    val authorName: String,
    val authorFace: String,
    /** 头像框；空串就是不挂框。 */
    val authorPendant: String = "",
    /** 大会员昵称色（`#RRGGBB`），空串用默认前景色。 */
    val authorNameColor: String = "",
    /** 右侧装扮卡片图与粉丝编号。 */
    val nameplateUrl: String = "",
    val nameplateNumber: String = "",
    val nameplateColor: String = "",
    /** 卡片下方外露的精选评论。 */
    val hotReplies: List<DynamicHotReply> = emptyList(),
    val timeSec: Long,
    val pubTimeText: String,
    /** 时间后面跟的动作（「直播了」「投稿了视频」），空串就不显示。 */
    val pubAction: String = "",
    val title: String = "",
    val content: String,
    val emotes: Map<String, EmoteImage> = emptyMap(),
    val images: List<DynamicImage> = emptyList(),
    val video: DynamicVideoCard? = null,
    val live: DynamicLiveCard? = null,
    /** 专栏或图文长文：点开进阅读页。 */
    val article: DynamicArticleCard? = null,
    val forward: DynamicItemUi? = null,
    val forwardCount: Int = 0,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
    val commentOid: Long = 0L,
    val commentType: Int = 17,
    /** 长文型动态的正文图还在取：此时 content 里只剩「[图片]」占位，界面先用灰块顶上。 */
    val pendingImages: Boolean = false,
    /** 头像那一行的灰标签（如「充电专属」）：接口给的是小图，文字兜底。 */
    val authorBadge: String = "",
    val authorBadgeIcon: String = "",
    /** 充电专属：正文被锁。feed 只给标记，面板文案要等详情补上。 */
    val chargeLocked: Boolean = false,
    val locked: DynamicLockedUi? = null,
)

/** 充电专属动态的锁定面板，文案与按钮都来自接口的 `module_blocked`。 */
data class DynamicLockedUi(
    val title: String = "",
    val hint: String = "",
    val buttonText: String = "",
    val buttonIcon: String = "",
    val jumpUrl: String = "",
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

/** 历史记录条目按需补的播放量 / 弹幕数（接口列表本身不带）。 */
data class HistoryStat(
    val view: Long,
    val danmaku: Long,
)

data class LiveRoomItem(
    val roomId: Long,
    val title: String,
    val cover: String,
    val anchor: String,
    val online: Long,
    val areaName: String,
    /** 接口直接给的人气文案（如「1.2万」）；没有数值 online 时用它兜底。 */
    val popularityText: String = "",
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
    /** 本场「看过」人数，官方客户端显示的就是它；0 表示接口没给。 */
    val watched: Long = 0L,
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
    val danmakuText: String = "",
    val durationText: String = "",
    val durationSec: Int = 0,
    val progressSec: Int = 0,
    val aid: Long = 0,
)

data class FavoriteFolder(
    val mediaId: Long,
    val title: String,
    val cover: String,
    /** 收藏夹里前几个视频的封面，用来拼列表左边那块四宫格。 */
    val coverUrls: List<String> = emptyList(),
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
