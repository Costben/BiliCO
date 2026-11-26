package erl.bilico.data.repository

import erl.bilico.platform.currentTimeMillis
import erl.bilico.data.model.DynamicContent
import erl.bilico.data.model.DynamicRichTextNode
import erl.bilico.data.model.DynamicVoteSummary
import erl.bilico.data.model.VoteInfoDetail
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import erl.bilico.platform.SynchronizedMap
import erl.bilico.platform.ioDispatcher
import kotlinx.coroutines.withContext

data class VoteOptionUi(
    val idx: Int,
    val desc: String,
    val count: Long,
    val imgUrl: String = "",
)

data class VoteDetailUi(
    val voteId: Long,
    val title: String,
    val choiceCnt: Int,
    val endTimeSec: Long,
    val status: Int,
    val totalVotes: Long,
    val options: List<VoteOptionUi>,
    val myVotes: Set<Int>,
) {
    val isEnded: Boolean
        get() = status == 4 || (endTimeSec > 0L && currentTimeMillis() / 1000L > endTimeSec)

    val hasVoted: Boolean
        get() = myVotes.isNotEmpty()
}

private val VOTE_ID_REGEX = Regex("(?:vote_id=|/vote/)(\\d+)")

internal fun extractDynamicVote(
    content: DynamicContent?,
    nodes: List<DynamicRichTextNode>,
): DynamicVoteSummary? {
    val addVote = content?.additional?.vote
    if (addVote != null && addVote.voteId > 0L) {
        return DynamicVoteSummary(
            voteId = addVote.voteId,
            title = addVote.title.ifBlank { addVote.desc },
            endTimeSec = addVote.endTime,
            joinNum = addVote.joinNum,
            choiceCnt = addVote.choiceCnt.coerceAtLeast(1),
            status = addVote.status,
        )
    }
    val voteNode = nodes.firstOrNull { node ->
        node.type.contains("VOTE", ignoreCase = true) ||
            node.rich?.type?.contains("VOTE", ignoreCase = true) == true
    } ?: return null
    val voteId = voteNode.rid.toLongOrNull()
        ?: voteNode.rich?.rid?.toLongOrNull()
        ?: VOTE_ID_REGEX.find(voteNode.jumpUrl.ifBlank { voteNode.rich?.jumpUrl.orEmpty() })
            ?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: return null
    if (voteId <= 0L) return null
    val label = voteNode.text.ifBlank { voteNode.origText }
        .ifBlank { voteNode.rich?.text.orEmpty() }
        .trim()
    return DynamicVoteSummary(voteId = voteId, title = label)
}

/** 动态内嵌投票的拉取缓存与提交。 */
class DynamicVoteRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository?,
) {
    private val cache = SynchronizedMap<Long, VoteDetailUi>()

    suspend fun detail(voteId: Long, forceRefresh: Boolean = false): Result<VoteDetailUi> =
        withContext(ioDispatcher) {
            runCatching {
                if (!forceRefresh) {
                    cache[voteId]?.let { return@runCatching it }
                }
                buvid.ensure()
                val response = api.voteInfo(voteId)
                if (response.code != 0) {
                    error(response.message.ifBlank { "投票信息获取失败(${response.code})" })
                }
                val info = response.data?.info ?: error("投票详情为空")
                val mapped = info.toUi()
                cache[voteId] = mapped
                mapped
            }
        }

    suspend fun submit(voteId: Long, selectedIndices: List<Int>): Result<VoteDetailUi> =
        withContext(ioDispatcher) {
            runCatching {
                val csrf = session?.csrf().orEmpty()
                require(csrf.isNotBlank()) { "登录后可用" }
                val clean = selectedIndices.distinct().filter { it > 0 }
                require(clean.isNotEmpty()) { "请选择投票选项" }
                buvid.ensure()
                val fields = buildMap {
                    put("vote_id", voteId.toString())
                    put("votes", clean.joinToString(","))
                    val uid = session?.mid() ?: 0L
                    if (uid > 0L) put("voter_uid", uid.toString())
                    put("csrf", csrf)
                }
                val response = api.doVote(fields)
                if (response.code != 0) {
                    error(response.message.ifBlank { "投票失败(${response.code})" })
                }
                detail(voteId, forceRefresh = true).getOrThrow()
            }
        }

    private fun VoteInfoDetail.toUi(): VoteDetailUi {
        val mappedOptions = options.map { opt ->
            VoteOptionUi(
                idx = opt.idx,
                desc = opt.desc.ifBlank { opt.title }.trim(),
                count = opt.cnt.coerceAtLeast(0L),
                imgUrl = Formatters.normalizeImageUrl(opt.imgUrl),
            )
        }
        val summed = mappedOptions.sumOf { it.count }
        return VoteDetailUi(
            voteId = voteId,
            title = title.ifBlank { desc }.trim(),
            choiceCnt = choiceCnt.coerceAtLeast(1),
            endTimeSec = endtime,
            status = status,
            totalVotes = maxOf(cnt, summed),
            options = mappedOptions,
            myVotes = myVotes.toSet(),
        )
    }
}
