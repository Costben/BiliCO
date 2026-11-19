package erl.bilico.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.FeedDislikeReason
import erl.bilico.data.model.FeedVideo
import erl.bilico.di.LocalAppContainer
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

private val FALLBACK_FEEDBACKS = listOf(
    FeedDislikeReason(1, "恐怖血腥", "将优化首页此类内容", isFeedback = true),
    FeedDislikeReason(2, "色情低俗", "将优化首页此类内容", isFeedback = true),
    FeedDislikeReason(3, "封面恶心", "将优化首页此类内容", isFeedback = true),
    FeedDislikeReason(4, "标题党/封面党", "将优化首页此类内容", isFeedback = true),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedDislikeDialog(
    video: FeedVideo,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = COUITheme.colorScheme

    val dislikeReasons = remember(video) {
        video.dislikeReasons.ifEmpty {
            buildList {
                if (video.upName.isNotBlank()) {
                    add(FeedDislikeReason(4, "UP主:${video.upName}", "将减少相似内容推荐", isFeedback = false))
                }
                add(FeedDislikeReason(12, "此类内容过多", "将减少相似内容推荐", isFeedback = false))
                add(FeedDislikeReason(13, "推荐过", "将减少相似内容推荐", isFeedback = false))
                add(FeedDislikeReason(1, "这个内容", "将减少相似内容推荐", isFeedback = false))
            }
        }
    }
    val feedbacks = remember(video) { video.dislikeFeedbacks.ifEmpty { FALLBACK_FEEDBACKS } }

    var show by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var selected by remember(video.bvid) {
        mutableStateOf(dislikeReasons.firstOrNull() ?: feedbacks.first())
    }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "为什么不想看",
        summary = video.title,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { if (!submitting) show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (dislikeReasons.isNotEmpty()) {
                    Text(
                        text = "我不想看（选择后将减少相似内容推荐）",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceSecondary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dislikeReasons.forEach { reason ->
                            DislikeReasonChip(
                                text = reason.name,
                                selected = selected == reason,
                                onClick = { if (!submitting) selected = reason },
                            )
                        }
                    }
                }
                if (feedbacks.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "反馈问题（选择后将优化首页此类内容）",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceSecondary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        feedbacks.forEach { reason ->
                            DislikeReasonChip(
                                text = reason.name,
                                selected = selected == reason,
                                onClick = { if (!submitting) selected = reason },
                            )
                        }
                    }
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "取消",
                    onClick = { if (!submitting) show = false },
                ),
                positive = DialogButtonBarAction(
                    text = if (submitting) "提交中..." else "确认提交",
                    onClick = {
                        if (submitting) return@DialogButtonBarAction
                        if (!container.session.isLoggedIn()) {
                            Toast.makeText(context, "请先登录后再提交不感兴趣反馈", Toast.LENGTH_SHORT).show()
                            return@DialogButtonBarAction
                        }
                        submitting = true
                        scope.launch {
                            container.feed.feedDislike(video, selected)
                                .onSuccess { toastMsg ->
                                    container.blocks.markSessionDisliked(video.bvid)
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                    submitting = false
                                    show = false
                                }
                                .onFailure { err ->
                                    submitting = false
                                    Toast.makeText(context, err.message ?: "提交失败", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                ),
            )
        }
    }
}

@Composable
private fun DislikeReasonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
