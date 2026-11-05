package erl.bilico.data.model

/** 推荐流卡片所需的字段，UI 不直接消费接口模型。 */
data class FeedVideo(
    val bvid: String,
    val aid: Long,
    val title: String,
    val cover: String,
    val upName: String,
    val upMid: Long,
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
)

data class Comment(
    val id: Long,
    val rootId: Long,
    val userName: String,
    val avatar: String,
    val level: Int,
    val timeSec: Long,
    val content: String,
    val like: Int,
    val replyCount: Int,
    val replies: List<Comment> = emptyList(),
)

data class CommentPage(
    val comments: List<Comment>,
    val nextOffset: String?,
    val isEnd: Boolean,
    val total: Int,
)

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

data class DynamicItemUi(
    val id: String,
    val kind: DynamicKind,
    val authorName: String,
    val authorFace: String,
    val timeSec: Long,
    val pubTimeText: String,
    val content: String,
    val images: List<DynamicImage> = emptyList(),
    val video: DynamicVideoCard? = null,
    val forward: DynamicItemUi? = null,
    val forwardCount: Int = 0,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
)

data class HistoryItem(
    val bvid: String,
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
    val url: String,
    val isHls: Boolean,
    val qualities: List<QualityOption>,
    val activeQuality: Int,
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
