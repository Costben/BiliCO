package erl.bilico.ui

sealed interface Route {
    data object Main : Route
    data class Video(
        val bvid: String,
        val startSec: Long = 0L,
        val cid: Long = 0L,
        val commentRootId: Long = 0L,
        val commentTargetId: Long = 0L,
        val commentMatchAuthor: String = "",
        val commentMatchText: String = "",
        val offline: Boolean = false,
        /** 推荐流下发的商业推广（小火箭）内容，只在详情页标题前标注。 */
        val promoted: Boolean = false,
    ) : Route
    data class LiveRoom(val roomId: Long) : Route
    data class Space(val mid: Long) : Route
    data class SpaceCollection(val mid: Long, val collectionId: Long, val isSeason: Boolean, val title: String, val total: Int = 0) : Route
    data object History : Route
    data object Favorites : Route
    data class FavoriteFolder(val mediaId: Long, val title: String) : Route
    data object WatchLater : Route
    data object Downloads : Route
    data object Following : Route
    data object Settings : Route
    data object StatsOverlaySettings : Route
    data object BlockManager : Route
    data object About : Route
    data class Pgc(
        val seasonId: Long,
        val epId: Long = 0L,
        val startSec: Long = 0L,
        val offline: Boolean = false,
    ) : Route
    data object Messages : Route
    data object BangumiFollow : Route
    data object Login : Route
    data object WebLogin : Route
    data object WebProfileEdit : Route
    data class EmoteManager(val url: String) : Route
    data class WebPage(val url: String, val title: String = "网页") : Route
    data object Update : Route
    data class Article(
        val cvId: Long,
        val title: String = "",
        val commentRootId: Long = 0L,
        val commentTargetId: Long = 0L,
        val commentMatchAuthor: String = "",
        val commentMatchText: String = "",
    ) : Route
    data class Opus(
        val dynamicId: String,
        val title: String = "",
        val commentRootId: Long = 0L,
        val commentTargetId: Long = 0L,
        val commentMatchAuthor: String = "",
        val commentMatchText: String = "",
    ) : Route
    data class Chat(val talkerId: Long, val talkerName: String = "", val talkerFace: String = "") : Route
    data class Search(val query: String = "") : Route
}

fun Route.isPlayback(): Boolean = this is Route.Video || this is Route.Pgc || this is Route.LiveRoom

/** 该页面上允许展示或退回右下角播放融合球（排除其他播放页及登录/聊天等独立模态页）。 */
fun Route.supportsOrbOverlay(): Boolean = this == Route.Main || (
    !isPlayback() && this !is Route.Chat && this !is Route.Login &&
        this !is Route.WebLogin && this !is Route.WebProfileEdit &&
        this !is Route.EmoteManager && this !is Route.WebPage
    )
