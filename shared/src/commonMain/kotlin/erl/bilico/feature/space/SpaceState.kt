package erl.bilico.feature.space

import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.model.SpaceCollection
import erl.bilico.data.model.SpaceOpusUi
import erl.bilico.data.model.SpaceProfile

enum class SpaceTab(val title: String) {
    VIDEOS("投稿"),
    SEASONS("合集"),
    DYNAMICS("动态"),
    OPUS("图文"),
    LIKES("点赞"),
}

/** 个人空间列表的排序方式（投稿走接口 `order` 参数，其余标签在已加载列表内重排）。 */
enum class SpaceSort(
    val label: String,
    val apiOrder: String,
) {
    TIME("按时间顺序", "pubdate"),
    HEAT("按热度排序", "click"),
}

data class SpaceUiState(
    val profile: SpaceProfile? = null,
    val selectedTab: SpaceTab = SpaceTab.VIDEOS,
    val sortOrder: SpaceSort = SpaceSort.TIME,
    // 投稿
    val videos: List<LibraryVideo> = emptyList(),
    val isVideosLoading: Boolean = true,
    val isVideosLoadingMore: Boolean = false,
    val isVideosEnd: Boolean = false,
    val videosError: String? = null,
    // 合集
    val collections: List<SpaceCollection> = emptyList(),
    val isCollectionsLoading: Boolean = false,
    val isCollectionsLoadingMore: Boolean = false,
    val isCollectionsEnd: Boolean = false,
    val collectionsError: String? = null,
    // 动态
    val dynamics: List<DynamicItemUi> = emptyList(),
    val isDynamicsLoading: Boolean = false,
    val isDynamicsLoadingMore: Boolean = false,
    val isDynamicsEnd: Boolean = false,
    val dynamicsOffset: String = "",
    val dynamicsError: String? = null,
    // 图文
    val opusItems: List<SpaceOpusUi> = emptyList(),
    val isOpusLoading: Boolean = false,
    val isOpusLoadingMore: Boolean = false,
    val isOpusEnd: Boolean = false,
    val opusOffset: String = "",
    val opusError: String? = null,
    // 最近点赞（未公开时不展示该标签）
    val likedVideos: List<LibraryVideo> = emptyList(),
    val isLikesLoading: Boolean = false,
    val showLikesTab: Boolean = false,
    val likesError: String? = null,
    // 关注
    val following: Boolean = false,
    val isFollowBusy: Boolean = false,
    // 搜索
    val isSearchActive: Boolean = false,
    val searchKeyword: String = "",
    val error: String? = null,
) {
    val visibleTabs: List<SpaceTab>
        get() = if (showLikesTab) SpaceTab.entries else SpaceTab.entries.filter { it != SpaceTab.LIKES }

    val filteredCollections: List<SpaceCollection>
        get() = filterByKeyword(collections) {
            it.name.lowercase().contains(kwLower()) || it.description.lowercase().contains(kwLower())
        }

    val filteredDynamics: List<DynamicItemUi>
        get() {
            val matched = filterByKeyword(dynamics) { item ->
                val kw = kwLower()
                item.content.lowercase().contains(kw) ||
                    item.title.lowercase().contains(kw) ||
                    item.authorName.lowercase().contains(kw) ||
                    item.forward?.content.orEmpty().lowercase().contains(kw)
            }
            return when (sortOrder) {
                SpaceSort.TIME -> matched
                SpaceSort.HEAT -> matched.sortedWith(
                    compareByDescending<DynamicItemUi> {
                        it.likeCount.toLong() + it.commentCount * 2L + it.forwardCount * 3L
                    }.thenByDescending { it.timeSec },
                )
            }
        }

    val filteredOpus: List<SpaceOpusUi>
        get() {
            val matched = filterByKeyword(opusItems) { it.content.lowercase().contains(kwLower()) }
            return when (sortOrder) {
                SpaceSort.TIME -> matched
                SpaceSort.HEAT -> matched.sortedByDescending { parseStatCount(it.likeText) }
            }
        }

    val filteredLikedVideos: List<LibraryVideo>
        get() {
            val matched = filterByKeyword(likedVideos) { video ->
                val kw = kwLower()
                video.title.lowercase().contains(kw) || video.upName.lowercase().contains(kw)
            }
            return when (sortOrder) {
                SpaceSort.TIME -> matched
                SpaceSort.HEAT -> matched.sortedByDescending { it.playCount }
            }
        }

    private fun kwLower(): String = searchKeyword.trim().lowercase()

    private inline fun <T> filterByKeyword(list: List<T>, pred: (T) -> Boolean): List<T> =
        if (searchKeyword.isBlank()) list else list.filter(pred)
}

private fun parseStatCount(text: String): Long {
    val clean = text.trim()
    if (clean.isEmpty()) return 0L
    return when {
        clean.endsWith("万") -> ((clean.dropLast(1).toDoubleOrNull() ?: 0.0) * 10_000).toLong()
        clean.endsWith("亿") -> ((clean.dropLast(1).toDoubleOrNull() ?: 0.0) * 100_000_000).toLong()
        else -> clean.toLongOrNull() ?: 0L
    }
}
