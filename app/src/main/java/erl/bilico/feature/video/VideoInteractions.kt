package erl.bilico.feature.video

import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.repository.DownloadRepository
import erl.bilico.data.repository.InteractionRepository
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 视频详情页互动操作（点赞、点踩、投币、收藏、一键三连、稍后再看、关注及关系加载）。 */
internal class VideoInteractionDelegate(
    private val interactions: InteractionRepository,
    private val session: SessionRepository,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<VideoUiState>,
) {
    fun toggleLike() {
        val detail = state.value.detail ?: return
        if (!requireLogin()) return
        val target = !state.value.liked
        interact {
            interactions.like(detail.aid, target).onSuccess {
                state.update { s ->
                    val current = s.detail ?: return@update s
                    val delta = if (target) 1L else -1L
                    s.copy(
                        liked = target,
                        disliked = if (target) false else s.disliked,
                        detail = current.copy(like = (current.like + delta).coerceAtLeast(0L)),
                    )
                }
            }
        }
    }

    fun toggleDislike() {
        val detail = state.value.detail ?: return
        if (!requireLogin()) return
        val target = !state.value.disliked
        interact {
            interactions.dislike(detail.aid, target).onSuccess {
                state.update { s ->
                    val current = s.detail ?: return@update s
                    val wasLiked = s.liked
                    s.copy(
                        disliked = target,
                        liked = if (target) false else s.liked,
                        detail = if (target && wasLiked) {
                            current.copy(like = (current.like - 1L).coerceAtLeast(0L))
                        } else {
                            current
                        },
                    )
                }
            }
        }
    }

    fun coin(count: Int) {
        val detail = state.value.detail ?: return
        if (!requireLogin()) return
        val awarded = state.value.coined
        if (awarded >= 2) {
            state.update { it.copy(message = "已经投过币了") }
            return
        }
        val add = (count.coerceIn(1, 2) - awarded).coerceIn(1, 2)
        interact {
            interactions.coin(detail.aid, add).onSuccess {
                state.update { s ->
                    val current = s.detail ?: return@update s
                    s.copy(
                        coined = (s.coined + add).coerceAtMost(2),
                        detail = current.copy(coin = current.coin + add),
                        message = "投币成功",
                    )
                }
            }
        }
    }

    fun favorite(
        addMediaIds: List<Long>,
        delMediaIds: List<Long>,
        nowFavorited: Boolean,
    ) {
        val detail = state.value.detail ?: return
        if (!requireLogin()) return
        if (addMediaIds.isEmpty() && delMediaIds.isEmpty()) return
        interact {
            interactions.favorite(detail.aid, addMediaIds, delMediaIds).onSuccess {
                state.update { s ->
                    val current = s.detail ?: return@update s
                    val delta = when {
                        !s.favorited && nowFavorited -> 1L
                        s.favorited && !nowFavorited -> -1L
                        else -> 0L
                    }
                    s.copy(
                        favorited = nowFavorited,
                        detail = current.copy(favorite = (current.favorite + delta).coerceAtLeast(0L)),
                        message = when {
                            !nowFavorited -> "已取消收藏"
                            addMediaIds.isNotEmpty() || !s.favorited -> "已收藏"
                            else -> "已更新收藏夹"
                        },
                    )
                }
            }
        }
    }

    fun triple() {
        val detail = state.value.detail ?: return
        if (!requireLogin()) return
        val snapshot = state.value
        if (snapshot.liked && snapshot.coined >= 2 && snapshot.favorited) {
            state.update { it.copy(message = "已经三连过了") }
            return
        }
        interact {
            interactions.triple(detail.aid).onSuccess { result ->
                state.update { s ->
                    val current = s.detail ?: return@update s
                    val addedLike = if (result.like && !s.liked) 1L else 0L
                    val addedCoin = if (result.coin) {
                        result.multiply.coerceAtLeast(1).coerceAtMost((2 - s.coined).coerceAtLeast(0))
                    } else {
                        0
                    }
                    val addedFav = if (result.fav && !s.favorited) 1L else 0L
                    val nextLiked = s.liked || result.like
                    s.copy(
                        liked = nextLiked,
                        disliked = if (nextLiked) false else s.disliked,
                        coined = (s.coined + addedCoin).coerceAtMost(2),
                        favorited = s.favorited || result.fav,
                        detail = current.copy(
                            like = current.like + addedLike,
                            coin = current.coin + addedCoin,
                            favorite = current.favorite + addedFav,
                        ),
                        message = "一键三连成功",
                    )
                }
            }.map { }
        }
    }

    fun addToWatchLater() {
        val detail = state.value.detail ?: return
        if (!requireLogin()) return
        interact {
            interactions.addToWatchLater(detail.aid).onSuccess {
                state.update { it.copy(message = "已加入稍后再看") }
            }
        }
    }

    fun toggleFollow() {
        val detail = state.value.detail ?: return
        if (!requireLogin()) return
        if (detail.upMid <= 0L) return
        val target = !state.value.following
        interact {
            interactions.follow(detail.upMid, target).onSuccess {
                state.update {
                    it.copy(following = target, message = if (target) "已关注" else "已取消关注")
                }
            }
        }
    }

    fun loadRelation(aid: Long, upMid: Long) {
        if (!session.isLoggedIn()) return
        scope.launch {
            interactions.relation(aid).onSuccess { relation ->
                state.update {
                    it.copy(
                        liked = relation.like,
                        coined = relation.coin,
                        favorited = relation.favorite,
                    )
                }
            }
            if (upMid > 0L) {
                val following = interactions.isFollowing(upMid)
                state.update { it.copy(following = following) }
            }
        }
    }

    fun loadUpFollowers(mid: Long) {
        if (mid <= 0L) return
        scope.launch {
            interactions.upCardSummary(mid)?.let { card ->
                state.update {
                    it.copy(
                        upFollowers = card.followers,
                        upOfficialType = card.officialType,
                        upOfficialDesc = card.officialDesc,
                    )
                }
            }
        }
    }

    private fun requireLogin(): Boolean {
        if (session.isLoggedIn()) return true
        state.update { it.copy(message = "登录后可用") }
        return false
    }

    private fun interact(block: suspend () -> Result<Unit>) {
        if (state.value.interactionBusy) return
        state.update { it.copy(interactionBusy = true) }
        scope.launch {
            block().onFailure { error ->
                state.update { it.copy(message = error.message ?: "操作失败") }
            }
            state.update { it.copy(interactionBusy = false) }
        }
    }
}

internal data class ResolvedDetail(
    val detail: VideoDetail? = null,
    val fromOffline: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
)

internal data class ResolvedPlayback(
    val source: PlaybackSource? = null,
    val notice: String? = null,
    val error: String? = null,
)

internal suspend fun resolveVideoDetail(
    bvid: String,
    offline: Boolean,
    repository: VideoRepository,
    downloads: DownloadRepository,
): ResolvedDetail {
    if (offline) {
        val local = downloads.buildOfflineVideoDetail(bvid)
        return if (local != null) ResolvedDetail(detail = local, fromOffline = true)
        else ResolvedDetail(error = "未找到本地缓存文件")
    }
    var resolved = ResolvedDetail(error = "视频加载失败")
    repository.detail(bvid)
        .onSuccess { detail -> resolved = ResolvedDetail(detail = detail, fromOffline = false) }
        .onFailure { err ->
            val local = downloads.buildOfflineVideoDetail(bvid)
            resolved = if (local != null) {
                ResolvedDetail(detail = local, fromOffline = true, notice = "当前离线，正在播放本地缓存")
            } else {
                ResolvedDetail(error = err.message ?: "视频加载失败")
            }
        }
    return resolved
}

internal suspend fun resolveVideoPlayback(
    bvid: String,
    cid: Long,
    qn: Int,
    offlineMode: Boolean,
    notifyDowngrade: Boolean,
    cdnOptionId: String,
    repository: VideoRepository,
    downloads: DownloadRepository,
): ResolvedPlayback {
    if (offlineMode) {
        val local = downloads.findCompletedVideo(bvid, cid)?.let(downloads::buildOfflinePlaybackSource)
        return if (local != null) ResolvedPlayback(source = local)
        else ResolvedPlayback(error = "该分 P 尚未离线缓存")
    }
    var resolved = ResolvedPlayback(error = "播放地址获取失败")
    repository.playback(bvid, cid, qn, CdnOption.fromId(cdnOptionId))
        .onSuccess { source ->
            val downgraded = qn > source.quality && qn >= 80 && notifyDowngrade
            val label = source.qualities.firstOrNull { it.quality == source.quality }?.label?.takeIf { it.isNotBlank() }
                ?: "${source.quality}P"
            resolved = ResolvedPlayback(
                source = source,
                notice = if (downgraded) "该清晰度不可用，已使用 $label" else null,
            )
        }
        .onFailure { err ->
            val local = downloads.findCompletedVideo(bvid, cid)?.let(downloads::buildOfflinePlaybackSource)
            resolved = if (local != null) {
                ResolvedPlayback(source = local, notice = "网络不可用，已切换为本地缓存")
            } else {
                ResolvedPlayback(error = err.message ?: "播放地址获取失败")
            }
        }
    return resolved
}
