package erl.bilico.feature.search

import androidx.compose.ui.graphics.vector.ImageVector
import erl.bilico.data.model.SearchArticle
import erl.bilico.data.model.SearchLive
import erl.bilico.data.model.SearchPgc
import erl.bilico.data.model.SearchSuggestion
import erl.bilico.data.model.SearchUser
import erl.bilico.data.model.SearchVideo
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Album
import io.github.suqi8.coui.kmp.icon.extended.All
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Playlist
import io.github.suqi8.coui.kmp.icon.extended.RecordingTape
import io.github.suqi8.coui.kmp.icon.extended.ScreenMirroring
import java.time.LocalDate
import java.time.ZoneId

data class SearchUiState(
    val query: String = "",
    val submitted: String = "",
    val tab: Int = 0,
    val videoOrder: VideoOrderOption = VideoOrderOption.TotalRank,
    val videoDuration: VideoDurationOption = VideoDurationOption.All,
    val videoZone: VideoZoneOption = VideoZoneOption.All,
    val videoPubTime: VideoPubTimeOption = VideoPubTimeOption.All,
    val customPubBegin: LocalDate? = null,
    val customPubEnd: LocalDate? = null,
    val userOrder: UserOrderOption = UserOrderOption.Default,
    val userType: UserTypeOption = UserTypeOption.All,
    val articleOrder: ArticleOrderOption = ArticleOrderOption.TotalRank,
    val articleZone: ArticleZoneOption = ArticleZoneOption.All,
    val hot: List<String> = emptyList(),
    val hotLoading: Boolean = false,
    val suggestions: List<SearchSuggestion> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val allUsers: List<SearchUser> = emptyList(),
    val allPgc: List<SearchPgc> = emptyList(),
    val allVideos: List<SearchVideo> = emptyList(),
    val videos: List<SearchVideo> = emptyList(),
    val bangumis: List<SearchPgc> = emptyList(),
    val movies: List<SearchPgc> = emptyList(),
    val liveRooms: List<SearchLive> = emptyList(),
    val users: List<SearchUser> = emptyList(),
    val articles: List<SearchArticle> = emptyList(),
    val tabCounts: Map<SearchTab, Int> = emptyMap(),
    val loadedTabs: Set<SearchTab> = emptySet(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
) {
    val currentTab: SearchTab get() = SearchTab.fromIndex(tab)
    val hasVideoFilter: Boolean
        get() = videoOrder != VideoOrderOption.TotalRank ||
            videoDuration != VideoDurationOption.All ||
            videoZone != VideoZoneOption.All ||
            videoPubTime != VideoPubTimeOption.All

    fun currentTabItemCount(): Int = when (currentTab) {
        SearchTab.All -> allUsers.size + (if (allPgc.isNotEmpty()) 1 else 0) + allVideos.size
        SearchTab.Video -> videos.size
        SearchTab.Bangumi -> bangumis.size
        SearchTab.Movie -> movies.size
        SearchTab.LiveRoom -> liveRooms.size
        SearchTab.User -> users.size
        SearchTab.Article -> articles.size
    }
}

/** 搜索页分类标签，按 PiliPlus 与 B 站搜索分类补齐。 */
enum class SearchTab(val label: String) {
    All("综合"),
    Video("视频"),
    Bangumi("番剧"),
    Movie("影视"),
    LiveRoom("直播间"),
    User("用户"),
    Article("专栏"),
    ;

    companion object {
        fun fromIndex(index: Int): SearchTab =
            entries.getOrElse(index) { All }
    }
}

internal fun SearchTab.iconVector(): ImageVector = when (this) {
    SearchTab.All -> COUIIcons.All
    SearchTab.Video -> COUIIcons.Play
    SearchTab.Bangumi -> COUIIcons.Playlist
    SearchTab.Movie -> COUIIcons.RecordingTape
    SearchTab.LiveRoom -> COUIIcons.ScreenMirroring
    SearchTab.User -> COUIIcons.Contacts
    SearchTab.Article -> COUIIcons.Album
}

internal fun formatSearchCount(count: Int): String =
    if (count > 99) "99+" else count.coerceAtLeast(0).toString()

internal fun Map<String, Int>.toTabCountMap(allNumResults: Int): Map<SearchTab, Int> = buildMap {
    put(SearchTab.All, allNumResults)
    this@toTabCountMap["video"]?.let { put(SearchTab.Video, it) }
    this@toTabCountMap["media_bangumi"]?.let { put(SearchTab.Bangumi, it) }
    this@toTabCountMap["media_ft"]?.let { put(SearchTab.Movie, it) }
    this@toTabCountMap["live_room"]?.let { put(SearchTab.LiveRoom, it) }
    this@toTabCountMap["bili_user"]?.let { put(SearchTab.User, it) }
    this@toTabCountMap["article"]?.let { put(SearchTab.Article, it) }
}

enum class VideoOrderOption(val label: String, val param: String) {
    TotalRank("综合排序", "totalrank"),
    PubDate("最新发布", "pubdate"),
    Click("最多播放", "click"),
    Danmaku("最多弹幕", "dm"),
    Stow("最多收藏", "stow"),
    Scores("最多评论", "scores"),
}

enum class VideoDurationOption(val label: String, val param: Int) {
    All("全部时长", 0),
    Under10Min("0-10分钟", 1),
    Min10To30("10-30分钟", 2),
    Min30To60("30-60分钟", 3),
    Over60Min("60分钟+", 4),
}

enum class VideoPubTimeOption(val label: String) {
    All("不限"),
    Day("最近一天"),
    Week("最近一周"),
    HalfYear("最近半年"),
    Custom("自定义时间"),
}

enum class VideoZoneOption(val label: String, val tid: Int) {
    All("全部分区", 0),
    Douga("动画", 1),
    Anime("番剧", 13),
    Guochuang("国创", 167),
    Music("音乐", 3),
    Dance("舞蹈", 129),
    Game("游戏", 4),
    Knowledge("知识", 36),
    Tech("科技", 188),
    Sports("运动", 234),
    Car("汽车", 223),
    Life("生活", 160),
    Food("美食", 221),
    Animal("动物", 217),
    Kichiku("鬼畜", 119),
    Fashion("时尚", 115),
    Info("资讯", 202),
    Ent("娱乐", 5),
    Cinephile("影视", 181),
    Documentary("记录", 177),
    Movie("电影", 23),
    Tv("电视", 11),
}

enum class UserOrderOption(
    val label: String,
    val order: String,
    val orderSort: Int,
) {
    Default("默认排序", "", 0),
    FansDesc("粉丝数由高到低", "fans", 0),
    FansAsc("粉丝数由低到高", "fans", 1),
    LevelDesc("Lv等级由高到低", "level", 0),
    LevelAsc("Lv等级由低到高", "level", 1),
}

enum class UserTypeOption(val label: String, val param: Int) {
    All("全部用户", 0),
    Up("UP主", 1),
    Common("普通用户", 2),
    Verified("认证用户", 3),
}

enum class ArticleOrderOption(val label: String, val param: String) {
    TotalRank("综合排序", "totalrank"),
    PubDate("最新发布", "pubdate"),
    Click("最多点击", "click"),
    Attention("最多喜欢", "attention"),
    Scores("最多评论", "scores"),
}

enum class ArticleZoneOption(val label: String, val categoryId: Int) {
    All("全部分区", 0),
    Douga("动画", 2),
    Game("游戏", 1),
    Cinephile("影视", 28),
    Life("生活", 3),
    Interest("兴趣", 29),
    Novel("轻小说", 16),
    Tech("科技", 17),
    Note("笔记", 41),
}

internal fun computePubTimeSeconds(
    option: VideoPubTimeOption,
    customBegin: LocalDate?,
    customEnd: LocalDate?,
): Pair<Long?, Long?> {
    if (option == VideoPubTimeOption.All) return null to null
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val (startDate, endDate) = when (option) {
        VideoPubTimeOption.All -> return null to null
        VideoPubTimeOption.Day -> today to today
        VideoPubTimeOption.Week -> today.minusDays(6) to today
        VideoPubTimeOption.HalfYear -> today.minusDays(179) to today
        VideoPubTimeOption.Custom -> {
            val begin = customBegin ?: today.withDayOfMonth(1)
            val end = customEnd ?: today
            if (begin.isAfter(end)) end to begin else begin to end
        }
    }
    val beginSec = startDate.atStartOfDay(zone).toEpochSecond()
    val endSec = endDate.atTime(23, 59, 59).atZone(zone).toEpochSecond()
    return beginSec to endSec
}
