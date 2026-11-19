package erl.bilico.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

private val OfficialPersonalColor = Color(0xFFFFB900)
private val OfficialOrgColor = Color(0xFF00A1D6)

/** B 站官方认证角标：0 = 个人认证（黄），1 = 机构认证（蓝），其余值不绘制。 */
@Composable
fun OfficialVerifyBadge(
    type: Int,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
    ringColor: Color = COUITheme.colorScheme.surface,
) {
    if (type != 0 && type != 1) return
    val badgeColor = if (type == 0) OfficialPersonalColor else OfficialOrgColor
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(badgeColor)
            .border(width = (size * 0.1f).coerceAtLeast(1.dp), color = ringColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.62f)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.58f, h * 0.06f)
                lineTo(w * 0.18f, h * 0.55f)
                lineTo(w * 0.48f, h * 0.55f)
                lineTo(w * 0.36f, h * 0.94f)
                lineTo(w * 0.82f, h * 0.44f)
                lineTo(w * 0.52f, h * 0.44f)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

/** 认证头衔胶囊条（个人空间、搜索用户卡片等需要展示具体认证说明的场景）。 */
@Composable
fun OfficialVerifyLabel(
    type: Int,
    desc: String,
    modifier: Modifier = Modifier,
) {
    if (type != 0 && type != 1 && desc.isBlank()) return
    val text = desc.ifBlank { if (type == 1) "机构认证" else "个人认证" }
    val tint = if (type == 1) OfficialOrgColor else OfficialPersonalColor
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (type == 0 || type == 1) {
            OfficialVerifyBadge(type = type, size = 13.dp)
        }
        Text(
            text = text,
            fontSize = 11.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
