package erl.bilico.feature.live

import erl.bilico.platform.formatFixedDecimal
import erl.bilico.platform.PlatformBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.LiveSuperChat
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import erl.bilico.platform.formatEpochSeconds

/** 竖屏聊天顶部的生效 SC 横向胶囊条，点击可快速聚焦/复制该 SC。 */
@Composable
internal fun SuperChatStrip(
    items: List<LiveSuperChat>,
    nowSec: Long,
    onSelect: (LiveSuperChat) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { sc ->
            val rem = (sc.endTimeSec - nowSec).coerceAtLeast(0L)
            val headerColor = parseHexColor(sc.backgroundPriceColor, Color(0xFF7497CD))
            val bodyColor = parseHexColor(sc.backgroundBottomColor, Color(0xFF2A60B2))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bodyColor)
                    .border(1.dp, headerColor.copy(alpha = 0.7f), CircleShape)
                    .clickable { onSelect(sc) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (sc.userFace.isNotBlank()) {
                    AsyncImage(
                        model = Formatters.sizedImageUrl(sc.userFace, 48, 48),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(18.dp).clip(CircleShape),
                    )
                }
                Text(
                    text = "${sc.userName} · ￥${sc.price}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = "${rem}s",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** 照 PiliPlus 双段卡片样式绘制 SC：上段用户信息 + 金额 + 倒计时，下段主题底色留言正文。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveSuperChatCard(
    sc: LiveSuperChat,
    nowSec: Long,
    modifier: Modifier = Modifier,
    overrideEndSec: Long? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val bottomColor = parseHexColor(sc.backgroundBottomColor, Color(0xFF2A60B2))
    val headerBgColor = parseHexColor(sc.backgroundColor, Color(0xFFEDF5FF))
    val messageColor = parseHexColor(sc.messageFontColor, Color.White)
    val endSec = overrideEndSec ?: sc.endTimeSec
    val remainingSec = (endSec - nowSec).coerceAtLeast(0L)
    val shape = SquircleShape(10.dp)
    val density = LocalDensity.current
    val avatarPx = with(density) { 36.dp.roundToPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, bottomColor, shape)
            .let { base ->
                if (onClick != null || onLongClick != null) {
                    base.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = { onLongClick?.invoke() },
                    )
                } else {
                    base
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBgColor)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sc.userFace.isNotBlank()) {
                AsyncImage(
                    model = Formatters.sizedImageUrl(sc.userFace, avatarPx, avatarPx),
                    contentDescription = sc.userName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sc.medalName.isNotBlank()) {
                        Text(
                            text = "${sc.medalName} ${sc.medalLevel}",
                            fontSize = 10.sp,
                            color = bottomColor,
                            modifier = Modifier
                                .clip(SquircleShape(4.dp))
                                .background(bottomColor.copy(alpha = 0.14f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = sc.userName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = parseHexColor(sc.nameColor, Color(0xFF1F2430)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "￥${sc.price}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = bottomColor,
                )
            }
            if (remainingSec > 0L) {
                Text(
                    text = "${remainingSec}s",
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                )
            }
        }
        if (sc.message.isNotBlank()) {
            Text(
                text = sc.message,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bottomColor)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = messageColor,
            )
        }
    }
}

/** 横屏全屏左下角/竖屏回放浮出的限时 SC 卡片，带关闭按钮（参考 PiliPlus `fsSC`）。 */
@Composable
internal fun FullscreenSuperChatOverlay(
    sc: LiveSuperChat,
    endSec: Long,
    nowSec: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isReplay: Boolean = false,
) {
    Box(modifier = modifier.widthIn(max = 300.dp)) {
        LiveSuperChatCard(
            sc = sc,
            nowSec = nowSec,
            overrideEndSec = endSec,
            modifier = Modifier.padding(top = 6.dp, end = 6.dp),
        )
        if (isReplay) {
            Text(
                text = "回放",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(COUITheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xEEFFFFFF))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = COUIIcons.Close,
                contentDescription = "关闭醒目留言",
                tint = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** 播放器底栏 SC 图标：放在「线路」旁，点击从屏幕右侧滑出 SC 统计与回放面板。 */
@Composable
internal fun PlayerSuperChatButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "醒目留言" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 18.dp)
                .border(1.5.dp, Color.White, SquircleShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "SC",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 10.sp,
            )
        }
        if (count > 0) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 2.dp)
                    .clip(CircleShape)
                    .background(COUITheme.colorScheme.primary)
                    .padding(horizontal = 3.dp, vertical = 0.5.dp),
            )
        }
    }
}

/** 屏幕右侧弹出的 SC 统计与回放面板：所有出现过的 SC 永久保留至退出直播间，点击即可回放。 */
@Composable
internal fun LiveSuperChatDrawer(
    visible: Boolean,
    items: List<LiveSuperChat>,
    nowSec: Long,
    inlineInStage: Boolean,
    onReplay: (LiveSuperChat) -> Unit,
    onCopy: (LiveSuperChat) -> Unit,
    onDismiss: () -> Unit,
) {
    PlatformBackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val scheme = COUITheme.colorScheme
        val panelBg = if (inlineInStage) Color(0xEB181A20) else scheme.surfaceContainer
        val titleColor = if (inlineInStage) Color.White else scheme.onSurface
        val subColor = if (inlineInStage) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceSecondary
        val pillBg = if (inlineInStage) Color.White.copy(alpha = 0.14f) else scheme.surfaceContainerHigh
        val totalPrice = remember(items) { items.sumOf { it.price } }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .animateEnterExit(
                        enter = slideInHorizontally { it },
                        exit = slideOutHorizontally { it },
                    )
                    .then(if (inlineInStage) Modifier.width(340.dp) else Modifier.fillMaxWidth(0.82f).widthIn(max = 340.dp))
                    .fillMaxHeight()
                    .background(panelBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .then(if (inlineInStage) Modifier.displayCutoutPadding() else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SC 统计与回放",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                        )
                        Text(
                            text = if (items.isEmpty()) "暂无醒目留言" else "共 ${items.size} 条 · ￥$totalPrice",
                            fontSize = 12.sp,
                            color = subColor,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .semantics { contentDescription = "关闭" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = COUIIcons.Close,
                            contentDescription = "关闭",
                            tint = titleColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                HorizontalDivider(
                    color = if (inlineInStage) Color.White.copy(alpha = 0.12f) else scheme.dividerLine,
                )
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "本场直播暂未收到 SC",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = titleColor,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "收到的醒目留言即使在弹幕区消失，也会一直保留在此列表中供查看与回放",
                                fontSize = 12.sp,
                                color = subColor,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .scrollEndHaptic(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.id }) { sc ->
                            val rem = (sc.endTimeSec - nowSec).coerceAtLeast(0L)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LiveSuperChatCard(
                                    sc = sc,
                                    nowSec = nowSec,
                                    onClick = { onReplay(sc) },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (rem > 0L) "${formatScClock(sc.startTimeSec)} · 生效中 ${rem}s"
                                        else "${formatScClock(sc.startTimeSec)} · 已结束",
                                        fontSize = 11.sp,
                                        color = subColor,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    if (sc.message.isNotBlank()) {
                                        Text(
                                            text = "复制",
                                            fontSize = 11.sp,
                                            color = titleColor,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(pillBg)
                                                .clickable { onCopy(sc) }
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                    Text(
                                        text = "回放",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = scheme.onPrimary,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(scheme.primary)
                                            .clickable { onReplay(sc) }
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatScClock(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    return formatEpochSeconds((epochSec * 1000L) / 1000L, "HH:mm:ss")
}

private fun parseHexColor(raw: String, fallback: Color): Color {
    val clean = raw.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return fallback
    val value = clean.toLongOrNull(16) ?: return fallback
    val argb = if (clean.length == 6) (0xFF000000L or value) else value
    return Color(argb.toInt())
}
