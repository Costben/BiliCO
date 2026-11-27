package erl.bilico.feature.video

import erl.bilico.platform.copyToClipboard
import erl.bilico.platform.PlatformContext
import androidx.compose.ui.geometry.Rect
import erl.bilico.platform.PlatformBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.repository.DanmakuRepository
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal class DanmakuHit(
    val item: DanmakuItem,
    val displayText: String,
    val count: Int,
    val boundsPx: Rect,
)

internal class DanmakuInteractionHost {
    var hitTest: ((Offset) -> DanmakuHit?)? = null
    var selectedHit: DanmakuHit? by mutableStateOf(null)
}

internal val LocalDanmakuInteraction = staticCompositionLocalOf<DanmakuInteractionHost?> { null }

internal class DanmakuActionCallbacks(
    val onCopy: (DanmakuItem) -> Unit,
    val onReport: ((DanmakuItem) -> Unit)? = null,
    val onPlusOne: (DanmakuItem) -> Unit,
    val onAdvancedPlusOne: ((DanmakuItem) -> Unit)? = null,
    val onBlock: (DanmakuItem) -> Unit,
)

internal fun copyDanmakuToClipboard(context: PlatformContext, text: String) {
    copyToClipboard(context, text, "danmaku")
}

@Composable
internal fun rememberDanmakuActions(
    aid: Long,
    cid: Long,
    fullscreen: Boolean,
    positionMs: () -> Long,
    isLoggedIn: () -> Boolean,
    repository: DanmakuRepository,
    controller: DanmakuController,
    scope: CoroutineScope,
    onShowMessage: (String) -> Unit,
    onShowLoginHint: () -> Unit,
): DanmakuActionCallbacks {
    val context = LocalPlatformContext.current
    var reportingItem by remember(cid) { mutableStateOf<DanmakuItem?>(null) }

    reportingItem?.let { target ->
        DanmakuReportDialog(
            inlineInStage = fullscreen,
            onSelectReason = { reason ->
                reportingItem = null
                scope.launch {
                    repository.report(cid = cid, dmid = target.id, reason = reason)
                        .onSuccess { onShowMessage("已举报该弹幕") }
                        .onFailure { onShowMessage(it.message ?: "举报弹幕失败") }
                }
            },
            onDismiss = { reportingItem = null },
        )
    }

    return DanmakuActionCallbacks(
        onCopy = { item ->
            copyDanmakuToClipboard(context, item.content)
            onShowMessage("已复制弹幕")
        },
        onReport = { item ->
            when {
                !isLoggedIn() -> onShowLoginHint()
                item.id <= 0L -> onShowMessage("该弹幕暂不支持举报")
                else -> reportingItem = item
            }
        },
        onPlusOne = { item ->
            when {
                !isLoggedIn() -> onShowLoginHint()
                aid <= 0L || cid <= 0L -> onShowMessage("还没有加载到视频信息")
                else -> scope.launch {
                    val sentAt = positionMs().coerceAtLeast(0L)
                    repository.send(aid = aid, cid = cid, message = item.content, progressMs = sentAt)
                        .onSuccess { dmid ->
                            val showAt = maxOf(sentAt, positionMs().coerceAtLeast(0L))
                            controller.appendSelf(
                                content = item.content,
                                timeMs = showAt,
                                serverId = dmid,
                            )
                            controller.reload(sentAt)
                            onShowMessage("弹幕已发送")
                        }
                        .onFailure { onShowMessage(it.message ?: "发送弹幕失败") }
                }
            }
        },
        onBlock = { item ->
            controller.blockTemporary(item)
            onShowMessage("已临时屏蔽该弹幕及发送者")
        },
    )
}

/**
 * 点击弹幕后紧贴弹幕下方浮出的悬浮工具栏：复制 / 举报 / +1 / 屏蔽。
 * 若下方空间不足（例如底部弹幕），自动翻到弹幕上方。
 */
@Composable
internal fun DanmakuActionToolbar(
    hit: DanmakuHit,
    actions: DanmakuActionCallbacks,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(hit) { Animatable(0f, visibilityThreshold = 0.0001f) }
    var exiting by remember(hit) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val requestDismiss: () -> Unit = remember(hit) {
        {
            if (!exiting) {
                exiting = true
                scope.launch {
                    progress.animateTo(0f, tween(120, easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)))
                    currentOnDismiss()
                }
            }
        }
    }
    LaunchedEffect(hit) {
        progress.animateTo(1f, tween(180, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)))
    }
    PlatformBackHandler(enabled = !exiting, onBack = requestDismiss)
    val density = LocalDensity.current
    val marginPx = remember(density) { with(density) { 12.dp.roundToPx() } }
    val gapPx = remember(density) { with(density) { 8.dp.roundToPx() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (exiting) {
                    Modifier
                } else {
                    Modifier.pointerInput(requestDismiss) {
                        detectTapGestures { requestDismiss() }
                    }
                },
            ),
    ) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            val p = progress.value
                            val s = if (exiting) 1f else 0.88f + 0.12f * p
                            scaleX = s
                            scaleY = s
                            alpha = p
                        }
                        .clip(SquircleShape(12.dp))
                        .background(Color(0xEB1C1C1E))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), SquircleShape(12.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolbarAction("复制") {
                        actions.onCopy(hit.item)
                        requestDismiss()
                    }
                    actions.onReport?.let { report ->
                        ToolbarDivider()
                        ToolbarAction("举报") {
                            onDismiss()
                            report(hit.item)
                        }
                    }
                    ToolbarDivider()
                    ToolbarAction("+1") {
                        actions.onPlusOne(hit.item)
                        requestDismiss()
                    }
                    actions.onAdvancedPlusOne?.let { advanced ->
                        ToolbarDivider()
                        ToolbarAction("@+1") {
                            advanced(hit.item)
                            requestDismiss()
                        }
                    }
                    ToolbarDivider()
                    ToolbarAction("屏蔽") {
                        actions.onBlock(hit.item)
                        requestDismiss()
                    }
                }
            },
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val maxX = (constraints.maxWidth - placeable.width - marginPx).coerceAtLeast(marginPx)
            val x = (hit.boundsPx.center.x - placeable.width / 2f).roundToInt().coerceIn(marginPx, maxX)
            val belowY = (hit.boundsPx.bottom + gapPx).roundToInt()
            val aboveY = (hit.boundsPx.top - gapPx - placeable.height).roundToInt()
            val y = if (belowY + placeable.height <= constraints.maxHeight - marginPx) {
                belowY
            } else {
                aboveY.coerceAtLeast(marginPx)
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeRelative(x, y)
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(SquircleShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = COUITheme.textStyles.footnote1,
        color = Color.White,
    )
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(Color.White.copy(alpha = 0.14f)),
    )
}

@Composable
private fun DanmakuReportDialog(
    inlineInStage: Boolean,
    onSelectReason: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingReason by remember { mutableStateOf<Int?>(null) }
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = {
            val code = pendingReason
            pendingReason = null
            if (code != null) onSelectReason(code) else onDismiss()
        },
    ) {
        val dismiss = LocalDismissState.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "举报弹幕",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState()),
            ) {
                DANMAKU_REPORT_REASONS.forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pendingReason = code
                                dismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = COUITheme.textStyles.body1,
                            color = COUITheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private val DANMAKU_REPORT_REASONS = listOf(
    8 to "剧透",
    7 to "引战",
    9 to "恶意刷屏",
    6 to "垃圾广告",
    4 to "人身攻击",
    10 to "视频无关",
    1 to "违法违禁",
    2 to "色情低俗",
    3 to "赌博诈骗",
    5 to "侵犯隐私",
    12 to "青少年不良",
)
