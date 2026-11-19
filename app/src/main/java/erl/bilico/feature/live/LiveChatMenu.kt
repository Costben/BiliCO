package erl.bilico.feature.live

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.EmoteImage
import erl.bilico.feature.video.copyDanmakuToClipboard
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState

/** 直播间底部弹幕/SC 原生更多菜单：顶部一排三联快捷图标（加1 / 高级加1 / 复制），下方为屏蔽与主页选项。 */
@Composable
internal fun ChatEntryPopupMenu(
    expanded: Boolean,
    entry: LiveChatEntry,
    pressState: PreciseClickState,
    context: Context,
    onDismiss: () -> Unit,
    onPlusOne: (text: String, emotes: Map<String, EmoteImage>) -> Unit,
    onAdvancedPlusOne: (medalLabel: String, senderName: String, text: String, emotes: Map<String, EmoteImage>) -> Unit,
    onCopyDanmaku: (String) -> Unit,
    onBlockUser: (mid: Long, name: String) -> Unit,
    onBlockContent: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
) {
    val mid: Long
    val userName: String
    val medalLabel: String
    val text: String
    val emotes: Map<String, EmoteImage>
    val isSc: Boolean
    when (entry) {
        is LiveChatEntry.Danmaku -> {
            mid = entry.message.mid
            userName = entry.message.name
            medalLabel = if (entry.message.medalName.isNotBlank()) "${entry.message.medalName} ${entry.message.medalLevel}" else ""
            text = entry.message.text
            emotes = entry.message.emotes
            isSc = false
        }
        is LiveChatEntry.SuperChat -> {
            mid = entry.sc.uid
            userName = entry.sc.userName
            medalLabel = if (entry.sc.medalName.isNotBlank()) "${entry.sc.medalName} ${entry.sc.medalLevel}" else ""
            text = entry.sc.message
            emotes = emptyMap()
            isSc = true
        }
    }
    CouiPopupMenu(
        show = expanded,
        onDismiss = onDismiss,
        preciseClickState = pressState,
        headerContent = if (text.isNotBlank()) {
            {
                val dismiss = LocalDismissState.current ?: onDismiss
                ChatPopupQuickTriplet(
                    onPlusOne = {
                        dismiss()
                        onPlusOne(text, emotes)
                    },
                    onAdvancedPlusOne = {
                        dismiss()
                        onAdvancedPlusOne(medalLabel, userName, text, emotes)
                    },
                    onCopy = {
                        dismiss()
                        copyDanmakuToClipboard(context, text)
                        onCopyDanmaku(if (isSc) "已复制 SC 留言" else "已复制弹幕")
                    },
                )
            }
        } else {
            null
        },
        items = buildList {
            if (mid > 0L) {
                add(CouiMenuItem(label = "屏蔽发送者（$userName）", onClick = { onBlockUser(mid, userName) }, group = 0))
            }
            if (text.isNotBlank()) {
                add(CouiMenuItem(label = "屏蔽此条内容", onClick = { onBlockContent(text) }, group = 0))
            }
            if (mid > 0L) {
                add(CouiMenuItem(label = "查看主页", onClick = { onOpenSpace(mid) }, group = 1))
            }
        },
    )
}

@Composable
private fun ChatPopupQuickTriplet(
    onPlusOne: () -> Unit,
    onAdvancedPlusOne: () -> Unit,
    onCopy: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatQuickActionCell(label = "加1", onClick = onPlusOne, modifier = Modifier.weight(1f)) {
            ChatQuickTextGlyph("+1", scheme.onSurface)
        }
        ChatQuickActionCell(label = "高级加1", onClick = onAdvancedPlusOne, modifier = Modifier.weight(1f)) {
            ChatQuickTextGlyph("@+1", scheme.primary)
        }
        ChatQuickActionCell(label = "复制", onClick = onCopy, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = COUIIcons.Copy,
                contentDescription = "复制内容",
                tint = scheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ChatQuickActionCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(SquircleShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icon()
        Text(
            text = label,
            fontSize = 10.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatQuickTextGlyph(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        lineHeight = 18.sp,
        maxLines = 1,
    )
}
