package erl.bilico.feature.dynamic

import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.showToast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicVoteSummary
import erl.bilico.data.repository.VoteDetailUi
import erl.bilico.data.repository.VoteOptionUi
import erl.bilico.data.util.Formatters
import erl.bilico.di.LocalAppContainer
import erl.bilico.ui.nestedBlockColor
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/**
 * 动态内嵌投票卡片：立即显示摘要信息并异步拉取完整选项与实时票数，支持单选/多选点击投票。
 */
@Composable
internal fun DynamicVoteCard(
    vote: DynamicVoteSummary,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current ?: return
    val context = LocalPlatformContext.current
    val scheme = COUITheme.colorScheme
    val scope = rememberCoroutineScope()

    var detail by remember(vote.voteId) { mutableStateOf<VoteDetailUi?>(null) }
    var loadError by remember(vote.voteId) { mutableStateOf<String?>(null) }
    var pendingSelected by remember(vote.voteId) { mutableStateOf<Set<Int>>(emptySet()) }
    var submitting by remember(vote.voteId) { mutableStateOf(false) }

    LaunchedEffect(vote.voteId) {
        container.dynamic.votes.detail(vote.voteId)
            .onSuccess {
                detail = it
                loadError = null
            }
            .onFailure {
                loadError = it.message
            }
    }

    val resolvedTitle = detail?.title?.takeIf { it.isNotBlank() } ?: vote.title.ifBlank { "参与投票" }
    val choiceCnt = (detail?.choiceCnt ?: vote.choiceCnt).coerceAtLeast(1)
    val totalVotes = detail?.totalVotes ?: vote.joinNum
    val endTimeSec = detail?.endTimeSec?.takeIf { it > 0L } ?: vote.endTimeSec
    val isEnded = detail?.isEnded ?: (
        vote.status == 4 || (endTimeSec > 0L && currentTimeMillis() / 1000L > endTimeSec)
        )
    val myVotes = detail?.myVotes.orEmpty()
    val showResult = detail?.hasVoted == true || isEnded

    fun submitSelection(indices: Set<Int>) {
        if (submitting || indices.isEmpty()) return
        if (!container.session.isLoggedIn()) {
            showToast(context, "登录后可用")
            return
        }
        submitting = true
        scope.launch {
            container.dynamic.votes.submit(vote.voteId, indices.toList())
                .onSuccess { updated ->
                    detail = updated
                    pendingSelected = emptySet()
                    showToast(context, "投票成功")
                }
                .onFailure { err ->
                    showToast(context, err.message ?: "投票失败")
                }
            submitting = false
        }
    }

    val shape = SquircleShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(nestedBlockColor())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = resolvedTitle,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (choiceCnt > 1) "最多选 $choiceCnt 项" else "单选",
                fontSize = 11.sp,
                color = scheme.primary,
                modifier = Modifier
                    .clip(SquircleShape(6.dp))
                    .background(scheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        val options = detail?.options.orEmpty()
        if (options.isEmpty()) {
            Text(
                text = loadError ?: "正在加载投票选项…",
                fontSize = 12.sp,
                color = scheme.onSurfaceSecondary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    val isMine = option.idx in myVotes
                    val isPicked = option.idx in pendingSelected
                    VoteOptionRow(
                        option = option,
                        totalVotes = totalVotes,
                        showResult = showResult,
                        highlighted = isMine || isPicked,
                        clickable = !showResult && !submitting,
                        onClick = {
                            if (choiceCnt == 1) {
                                submitSelection(setOf(option.idx))
                            } else {
                                pendingSelected = if (isPicked) {
                                    pendingSelected - option.idx
                                } else if (pendingSelected.size < choiceCnt) {
                                    pendingSelected + option.idx
                                } else {
                                    showToast(context, "最多选择 $choiceCnt 项")
                                    pendingSelected
                                }
                            }
                        },
                    )
                }
            }
            if (choiceCnt > 1 && !showResult && pendingSelected.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = if (submitting) "提交中…" else "确认投票 (${pendingSelected.size})",
                        enabled = !submitting,
                        onClick = { submitSelection(pendingSelected) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${Formatters.formatStat(totalVotes)} 人参与",
                fontSize = 11.5.sp,
                color = scheme.onSurfaceSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isEnded) "已结束" else "进行中",
                fontSize = 11.5.sp,
                color = if (isEnded) scheme.onSurfaceSecondary else scheme.primary,
            )
            if (endTimeSec > 0L && !isEnded) {
                val endLabel = Formatters.formatViewDate(endTimeSec)
                if (endLabel.isNotBlank()) {
                    Text(
                        text = " · $endLabel 截止",
                        fontSize = 11.5.sp,
                        color = scheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteOptionRow(
    option: VoteOptionUi,
    totalVotes: Long,
    showResult: Boolean,
    highlighted: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val ratio = if (showResult && totalVotes > 0L) {
        (option.count.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedRatio by animateFloatAsState(targetValue = ratio, label = "voteRatio")
    val rowShape = SquircleShape(10.dp)
    val rowBg = if (highlighted && !showResult) {
        scheme.primary.copy(alpha = 0.12f)
    } else {
        scheme.surfaceContainer
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBg)
            .then(
                if (highlighted) Modifier.border(1.dp, scheme.primary.copy(alpha = 0.55f), rowShape)
                else Modifier,
            )
            .clickable(enabled = clickable, onClick = onClick),
    ) {
        if (animatedRatio > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedRatio)
                        .fillMaxHeight()
                        .background(
                            if (highlighted) {
                                scheme.primary.copy(alpha = 0.2f)
                            } else {
                                scheme.onSurface.copy(alpha = 0.08f)
                            },
                        ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (option.imgUrl.isNotBlank()) {
                AsyncImage(
                    model = option.imgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(SquircleShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (highlighted && showResult) "${option.desc} · 已选" else option.desc,
                fontSize = 13.5.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlighted) scheme.primary else scheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showResult) {
                Spacer(Modifier.width(8.dp))
                val pct = if (totalVotes > 0L) (option.count * 100f / totalVotes).toInt() else 0
                Text(
                    text = "${Formatters.formatStat(option.count)} ($pct%)",
                    fontSize = 12.sp,
                    color = if (highlighted) scheme.primary else scheme.onSurfaceSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
