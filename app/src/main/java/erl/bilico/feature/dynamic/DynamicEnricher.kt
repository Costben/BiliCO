package erl.bilico.feature.dynamic

import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicLockedUi
import erl.bilico.data.repository.DynamicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 补齐 feed 里缺省的长文正文图片与充电专属锁定面板。
 * 动态流与个人空间动态页共用，避免两边各自维护去重与状态回写逻辑。
 */
internal class DynamicEnricher(
    private val repository: DynamicRepository,
    private val scope: CoroutineScope,
    private val updateItems: ((List<DynamicItemUi>) -> List<DynamicItemUi>) -> Unit,
) {
    private val opusEnriched = mutableSetOf<String>()
    private val lockedEnriched = mutableSetOf<String>()
    private var generation = 0

    fun reset() {
        generation++
        opusEnriched.clear()
        lockedEnriched.clear()
    }

    fun clearHistory() {
        opusEnriched.clear()
        lockedEnriched.clear()
    }

    fun enrich(items: List<DynamicItemUi>) {
        enrichOpus(items)
        enrichLocked(items)
    }

    /**
     * 长文型 opus 在 feed 里只有一张封面和「[图片]」占位，正文里真正的图要按动态编号再取一次。
     * 只补这类卡片，取到就原地换掉文字和图片；失败就保持原样，不影响整页。
     */
    private fun enrichOpus(items: List<DynamicItemUi>) {
        val requestGeneration = generation
        val targets = items.filter { item ->
            val id = item.dynamicId
            id.isNotBlank() &&
                item.video == null &&
                item.content.contains(IMAGE_PLACEHOLDER) &&
                opusEnriched.add(id)
        }
        if (targets.isEmpty()) return
        val pendingIds = targets.mapTo(HashSet()) { it.dynamicId }
        updateItems { currentList ->
            currentList.map { current ->
                if (current.dynamicId in pendingIds) current.copy(pendingImages = true) else current
            }
        }
        targets.forEach { item ->
            val id = item.dynamicId
            scope.launch {
                val filled = repository.withOpusContent(item)
                if (requestGeneration != generation) return@launch
                updateItems { currentList ->
                    currentList.map { current ->
                        when {
                            current.dynamicId != id -> current
                            filled == item -> current.copy(pendingImages = false)
                            else -> filled.copy(
                                pendingImages = false,
                                liked = current.liked,
                                likeCount = current.likeCount,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 充电专属的动态在 feed 里可能只有 `is_only_fans` 标记；正文被拦下的空壳卡片要去详情补锁定面板，
     * 已携带视频/图文正文的卡片只在接口确实返回 `module_blocked` 时才展示，避免在视频封面上顶出空白占位。
     */
    private fun enrichLocked(items: List<DynamicItemUi>) {
        val requestGeneration = generation
        val targets = items.filter { item ->
            val id = item.dynamicId
            id.isNotBlank() && item.chargeLocked && item.locked == null && lockedEnriched.add(id)
        }
        if (targets.isEmpty()) return
        val pendingIds = targets.mapTo(HashSet()) { it.dynamicId }
        updateItems { currentList ->
            currentList.map { current ->
                if (current.dynamicId in pendingIds) current.copy(pendingLocked = true) else current
            }
        }
        targets.forEach { item ->
            val id = item.dynamicId
            scope.launch {
                val fetched = repository.lockedInfo(id).getOrNull()
                if (requestGeneration != generation) return@launch
                val fallback = if (fetched == null && item.isEmptyLockedShell()) {
                    DynamicLockedUi(
                        title = "${item.authorName}专属动态",
                        hint = "该动态为充电专属，给 UP 主充电后即可查看",
                        buttonText = "去充电",
                        jumpUrl = "https://space.bilibili.com/${item.authorMid}",
                    )
                } else {
                    null
                }
                val resolved = fetched ?: fallback
                updateItems { currentList ->
                    currentList.map { current ->
                        if (current.dynamicId == id) {
                            current.copy(
                                locked = current.locked ?: resolved,
                                pendingLocked = false,
                            )
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val IMAGE_PLACEHOLDER = "[图片]"
    }
}

internal fun DynamicItemUi.isEmptyLockedShell(): Boolean =
    video == null &&
        images.isEmpty() &&
        live == null &&
        forward == null &&
        content.isBlank()
