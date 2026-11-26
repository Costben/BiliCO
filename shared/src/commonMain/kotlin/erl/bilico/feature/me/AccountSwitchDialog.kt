package erl.bilico.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.session.StoredSession
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.Avatar
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 切换账号面板：列出登录过的账号，点一行切过去，右侧叉号把它从名册里删掉。
 * 底部「添加账号」去登录页扫码，登录成功会自动退回。
 *
 * 「我的」和「设置」共用同一份面板，登录态为空时也允许打开：只要名册里还有账号，
 * 就不该强迫用户重新扫码。
 */
@Composable
internal fun AccountSwitchDialog(
    sessions: List<StoredSession>,
    activeMid: Long?,
    onDismiss: () -> Unit,
    onSwitch: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settled by remember { mutableStateOf(false) }
    val colors = COUITheme.colorScheme
    val textStyles = COUITheme.textStyles
    val dimens = COUITheme.dimens
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "切换账号",
        summary = if (sessions.isEmpty()) "还没有保存过账号" else null,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val action = pending
                pending = null
                if (action != null) action() else onDismiss()
            }
        },
    ) {
        COUITheme(colors = colors, textStyles = textStyles, dimens = dimens) {
            Column {
                sessions.forEach { session ->
                    AccountRow(
                        session = session,
                        active = session.mid == activeMid,
                        onSwitch = {
                            pending = { onSwitch(session.mid) }
                            show = false
                        },
                        onRemove = { onRemove(session.mid) },
                    )
                }
                DialogButtonBar(
                    negative = DialogButtonBarAction(
                        text = "添加账号",
                        onClick = {
                            pending = onAdd
                            show = false
                        },
                    ),
                    positive = DialogButtonBarAction(
                        text = "完成",
                        onClick = { show = false },
                    ),
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    session: StoredSession,
    active: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(SquircleShape(16.dp))
            .clickable(enabled = !active, onClick = onSwitch)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarUrl = remember(session.face) {
            Formatters.sizedImageUrl(session.face, 120, 120)
        }
        Avatar(url = avatarUrl, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = session.name.ifBlank { "UID ${session.mid}" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "UID ${session.mid}",
                fontSize = 12.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
        if (active) {
            Icon(
                imageVector = COUIIcons.Ok,
                contentDescription = "当前账号",
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = COUIIcons.Close,
                    contentDescription = "移除 ${session.name}",
                    tint = scheme.onSurfaceSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
