package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 详情页 BV 号旁的商单标记，数据来自空降助手。 */
@Composable
internal fun SponsorBadge(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    text: String = SPONSOR_BADGE_TEXT,
) {
    val scheme = COUITheme.colorScheme
    ContentBadge(
        text = text,
        containerColor = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        fontSize = fontSize,
        modifier = modifier,
    )
}

/** 详情页标题前的「推广」标记，表示这条视频是推荐流下发的商业推广。 */
@Composable
internal fun PromotionBadge(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    ContentBadge(
        text = "推广",
        containerColor = scheme.primary.copy(alpha = 0.12f),
        contentColor = scheme.primary,
        fontSize = fontSize,
        modifier = modifier,
    )
}

/** 只有片段被标记时的保守说法。 */
internal const val SPONSOR_BADGE_TEXT = "疑似商单"

/** 社区把整条视频标成了商单。 */
internal const val SPONSOR_BADGE_CONFIRMED = "商单"

@Composable
private fun ContentBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(SquircleShape(3.dp))
            .background(containerColor)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        color = contentColor,
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}
