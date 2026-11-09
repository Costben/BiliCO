package erl.bilico.feature.dynamic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicLockedUi
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** B 站粉，按钮和角标共用。 */
private val ChargePink = Color(0xFFFB7299)

/**
 * 充电专属动态的锁定面板：浅粉底卡片，左边是闪电圆环加锁扣，
 * 中间是标题与说明（说明第二行通常写「加入当前UP主的「30元档包月充电」即可解锁观看」），
 * 右侧是粉色按钮，点了去充电。
 */
@Composable
internal fun ChargeLockedPanel(
    locked: DynamicLockedUi,
    modifier: Modifier = Modifier,
) {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    // 官方那张 800×800 的 bg_img 是「粉色渐变 + 灰描边」的叠加层，这里照着画：
    // 底色压一层浅粉渐变到透明，外面 1dp 淡灰描边，深色模式把粉降下来。
    val base = if (isDark) Color(0xFF2A2226) else Color.White
    val pinkTop = Color(0xFFFFCDF2).copy(alpha = if (isDark) 0.12f else 0.34f)
    val gradient = Brush.verticalGradient(
        0f to pinkTop,
        0.78f to Color.Transparent,
        1f to Color.Transparent,
    )
    val outline = if (isDark) Color(0xFF3A3A3E) else Color(0xFFE3E5E7)
    val uriHandler = LocalUriHandler.current
    val open: () -> Unit = {
        val target = locked.jumpUrl.trim()
        if (target.isNotEmpty()) {
            uriHandler.openUri(if (target.startsWith("//")) "https:$target" else target)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(base)
            .background(gradient)
            .border(1.dp, outline, RoundedCornerShape(12.dp))
            .clickable(onClick = open)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChargeLockBadge()
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (locked.title.isNotBlank()) {
                Text(
                    text = locked.title,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (locked.hint.isNotBlank()) {
                Text(
                    text = locked.hint,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (locked.buttonText.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChargePink)
                    .clickable(onClick = open)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (locked.buttonIcon.isNotBlank()) {
                        AsyncImage(
                            model = locked.buttonIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = locked.buttonText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 闪电圆环加右下角小锁扣，尺寸固定成面板左侧的图标位。 */
@Composable
private fun ChargeLockBadge(size: androidx.compose.ui.unit.Dp = 44.dp) {
    Canvas(Modifier.size(size)) {
        val diameter = this.size.minDimension
        val stroke = 1.2.dp.toPx()
        drawCircle(
            color = ChargePink.copy(alpha = 0.10f),
            radius = diameter / 2f - stroke,
        )
        drawCircle(
            color = ChargePink.copy(alpha = 0.32f),
            radius = diameter / 2f - stroke,
            style = Stroke(width = stroke),
        )

        val w = diameter
        val h = diameter
        val bolt = Path().apply {
            moveTo(w * 0.57f, h * 0.17f)
            lineTo(w * 0.33f, h * 0.55f)
            lineTo(w * 0.48f, h * 0.55f)
            lineTo(w * 0.41f, h * 0.83f)
            lineTo(w * 0.67f, h * 0.43f)
            lineTo(w * 0.51f, h * 0.43f)
            close()
        }
        drawPath(bolt, ChargePink)

        // 右下角的锁扣：粉色实心圆打底，白色锁钩与锁体压在正中。
        val badgeRadius = w * 0.19f
        val center = Offset(w * 0.78f, h * 0.78f)
        drawCircle(ChargePink, badgeRadius, center)
        val shackle = badgeRadius * 0.62f
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - shackle / 2f, center.y - shackle * 1.02f),
            size = Size(shackle, shackle),
            style = Stroke(width = badgeRadius * 0.26f, cap = StrokeCap.Round),
        )
        val bodyWidth = badgeRadius * 1.16f
        val bodyHeight = badgeRadius * 0.84f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(center.x - bodyWidth / 2f, center.y - bodyHeight * 0.18f),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(badgeRadius * 0.24f, badgeRadius * 0.24f),
        )
    }
}

/** 面板还在取的时候占个位，避免卡片先塌下去再撑开。 */
@Composable
internal fun ChargeLockedPlaceholder(modifier: Modifier = Modifier) {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF2A2226) else Color(0xFFFFF5F7)),
    )
}
