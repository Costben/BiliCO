package erl.bilico.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.DynamicHotReply
import erl.bilico.ui.components.RichCommentText
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 动态卡片下方的精选评论：左侧一条竖线，每行一个气泡图标加「昵称：内容」，
 * 最多两行截断。数据来自接口的 `module_interaction`，不是另外筛的。
 */
@Composable
internal fun DynamicHotReplies(
    replies: List<DynamicHotReply>,
    modifier: Modifier = Modifier,
) {
    if (replies.isEmpty()) return
    val secondary = COUITheme.colorScheme.onSurfaceSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight()
                .background(secondary.copy(alpha = 0.25f)),
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            replies.forEach { reply ->
                Row {
                    Icon(
                        imageVector = COUIIcons.Community,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(13.dp),
                        tint = secondary,
                    )
                    Spacer(Modifier.width(5.dp))
                    RichCommentText(
                        content = reply.text,
                        emotes = reply.emotes,
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.body2.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                        color = secondary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
