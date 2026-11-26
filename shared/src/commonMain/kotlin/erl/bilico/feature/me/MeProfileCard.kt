package erl.bilico.feature.me

import erl.bilico.platform.formatFixedDecimal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.Account
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.Avatar
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 「我的」页顶部账号名片：头像、昵称/等级/大会员、硬币与经验条，以及动态/关注/粉丝三列统计。 */
@Composable
internal fun ProfileCard(
    account: Account,
    onOpenSpace: () -> Unit,
    onOpenFollowing: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val nextExp = account.nextExp
    val isMaxLevel = account.level >= 6 || (nextExp != null && nextExp <= 0)
    val expProgress = when {
        account.level < 0 -> 0f
        isMaxLevel -> 1f
        nextExp != null && nextExp > 0 ->
            (account.currentExp.toFloat() / nextExp.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    val expText = when {
        account.level < 0 -> null
        isMaxLevel -> "${account.currentExp}"
        nextExp != null && nextExp > 0 -> "${account.currentExp}/$nextExp"
        else -> "${account.currentExp}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        weight = CardDefaults.GroupWeight,
        insideMargin = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(12.dp))
                    .clickable(onClick = onOpenSpace),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val avatarUrl = remember(account.face) {
                    Formatters.sizedImageUrl(account.face, 180, 180)
                }
                Avatar(url = avatarUrl, size = 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = account.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (account.level >= 0) {
                            LevelBadge(level = account.level)
                        }
                        if (account.vip) {
                            VipBadge()
                        }
                    }
                    if (account.coins != null || expText != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            account.coins?.let { coins ->
                                Text(
                                    text = "硬币 ${formatCoins(coins)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.onSurfaceSecondary,
                                    maxLines = 1,
                                )
                            }
                            if (expText != null) {
                                Text(
                                    text = "经验 $expText",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.onSurfaceSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (account.level >= 0) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .clip(SquircleShape(2.dp))
                                .background(scheme.dividerLine),
                        ) {
                            if (expProgress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(expProgress)
                                        .height(3.5.dp)
                                        .clip(SquircleShape(2.dp))
                                        .background(levelBadgeColor(account.level)),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileStatColumn(
                    value = formatProfileCount(account.dynamicCount),
                    label = "动态",
                    onClick = onOpenSpace,
                    modifier = Modifier.weight(1f),
                )
                ProfileStatColumn(
                    value = formatProfileCount(account.following),
                    label = "关注",
                    onClick = onOpenFollowing,
                    modifier = Modifier.weight(1f),
                )
                ProfileStatColumn(
                    value = formatProfileCount(account.follower),
                    label = "粉丝",
                    onClick = onOpenSpace,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProfileStatColumn(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    Column(
        modifier = modifier
            .clip(SquircleShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = value,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = scheme.onSurfaceSecondary,
            maxLines = 1,
        )
    }
}

private fun formatProfileCount(count: Int?): String = when {
    count == null -> "--"
    count >= 10_000 -> Formatters.formatStat(count.toLong())
    else -> count.toString()
}

private fun formatCoins(coins: Double): String =
    erl.bilico.platform.formatFixedDecimal(coins, 1)

private fun levelBadgeColor(level: Int): Color = when {
    level >= 6 -> Color(0xFFFF3B30)
    level == 5 -> Color(0xFFFF6C00)
    level == 4 -> Color(0xFFFF9A5A)
    level == 3 -> Color(0xFF5AC8FA)
    level == 2 -> Color(0xFF5BD690)
    else -> Color(0xFFAEAEB2)
}

@Composable
private fun LevelBadge(level: Int) {
    Text(
        text = "LV$level",
        modifier = Modifier
            .clip(SquircleShape(4.dp))
            .background(levelBadgeColor(level))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
}

/** 官方那枚粉色的「大会员」标识。接口只给 vipStatus，拿不到会员等级和到期时间。 */
@Composable
private fun VipBadge() {
    Text(
        text = "大会员",
        modifier = Modifier
            .clip(SquircleShape(4.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFB7299), Color(0xFFFF9DBA))))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        color = Color.White,
    )
}
