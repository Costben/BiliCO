package erl.bilico.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.di.AppContainer
import erl.bilico.update.AppUpdater
import erl.bilico.update.UpdateInfo
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import java.util.Locale

/**
 * 应用更新弹窗（融合 Cobo UpdateDialog 与 c001apk AppUpdateDialog）：
 * - 支持正式版 / 预发布版标签展示、Markdown 更新说明与底部渐隐滚动
 * - 支持跳过指定版本（预览模式不展示）
 * - 支持应用内多镜像加速分片下载、实时速度与进度条展示、APK 校验及调起系统安装器
 */
@Composable
fun UpdateDialog(
    container: AppContainer,
    info: UpdateInfo,
    preview: Boolean = false,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inAppSetting by container.settings.inAppUpdate.collectAsStateWithLifecycle()

    var show by remember { mutableStateOf(false) }
    var browserDownload by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }

    val inApp = inAppSetting && !info.apkUrl.isNullOrBlank()
    var downloading by remember { mutableStateOf(false) }
    var readyFile by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) { show = true }
    val haptic = LocalHapticFeedback.current
    val textMargin = DialogDefaults.insideMargin.width
    val scrollState = rememberScrollState()

    fun startInAppDownload() {
        val apkUrl = info.apkUrl ?: return
        downloading = true
        readyFile = null
        downloadError = null
        progress = 0f
        progressText = "正在测速并准备下载…"
        var lastBytes = 0L
        var lastAt = 0L
        var speed = 0L
        downloadJob = scope.launch {
            try {
                val file = AppUpdater.downloadApk(
                    context = context,
                    url = apkUrl,
                    version = info.version,
                    force = preview,
                    onProgress = { p, current, total ->
                        val now = System.currentTimeMillis()
                        if (lastAt != 0L && now > lastAt) {
                            speed = ((current - lastBytes) * 1000 / (now - lastAt)).coerceAtLeast(0L)
                        }
                        lastBytes = current
                        lastAt = now
                        progress = p
                        val size = if (total > 0) {
                            "${(p * 100).toInt()}% (${formatSize(current)} / ${formatSize(total)})"
                        } else {
                            formatSize(current)
                        }
                        progressText = if (speed > 0) "$size · ${formatSize(speed)}/s" else size
                    },
                )
                downloading = false
                readyFile = file
                progress = 1f
                progressText = "安装包已就绪 (${formatSize(file.length())})"
                delay(200)
                runCatching { AppUpdater.installApk(context, file) }
            } catch (_: CancellationException) {
                // 用户主动取消
            } catch (t: Throwable) {
                downloading = false
                downloadError = t.message ?: "下载失败"
            }
        }
    }

    OverlayDialog(
        show = show,
        title = when {
            downloading -> "正在下载新版本"
            readyFile != null -> "下载完成 v${info.version}"
            downloadError != null -> "更新下载失败"
            else -> "发现新版本 v${info.version}"
        },
        summary = when {
            downloading -> "正在拉取安装包…"
            readyFile != null -> "已校验安装包，可立即安装"
            downloadError != null -> "请重试或前往浏览器下载"
            info.prerelease -> "预发布版本"
            else -> "正式版本"
        },
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = {
            if (downloading) {
                downloadJob?.cancel()
                downloading = false
            }
            show = false
        },
        onDismissFinished = {
            if (!settled) {
                settled = true
                when {
                    browserDownload -> onDownload()
                    skip -> onSkip()
                    else -> onDismiss()
                }
            }
        },
    ) {
        Column {
            when {
                downloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textMargin, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            progressText,
                            fontSize = 13.sp,
                            color = COUITheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "取消",
                            onClick = {
                                downloadJob?.cancel()
                                downloading = false
                                show = false
                            },
                        ),
                        positive = DialogButtonBarAction(
                            text = "下载中 ${(progress * 100).toInt()}%",
                            recommended = true,
                            enabled = false,
                            onClick = {},
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                readyFile != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textMargin, vertical = 12.dp),
                    ) {
                        Text(
                            progressText,
                            fontSize = 14.sp,
                            color = COUITheme.colorScheme.onBackground,
                        )
                    }
                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "稍后",
                            onClick = { show = false },
                        ),
                        positive = DialogButtonBarAction(
                            text = "立即安装",
                            recommended = true,
                            onClick = {
                                val file = readyFile ?: return@DialogButtonBarAction
                                show = false
                                runCatching { AppUpdater.installApk(context, file) }
                            },
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                downloadError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textMargin, vertical = 12.dp),
                    ) {
                        Text(
                            downloadError ?: "下载遇到错误",
                            fontSize = 14.sp,
                            color = COUITheme.colorScheme.error,
                        )
                    }
                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "浏览器下载",
                            onClick = {
                                browserDownload = true
                                show = false
                            },
                        ),
                        positive = DialogButtonBarAction(
                            text = "重试",
                            recommended = true,
                            onClick = { startInAppDownload() },
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                else -> {
                    if (info.notes.isNotBlank()) {
                        MarkdownText(
                            markdown = info.notes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    if (scrollState.canScrollForward) {
                                        val fadePx = 28.dp.toPx().coerceAtMost(size.height)
                                        val topY = size.height - fadePx
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black),
                                                startY = topY,
                                                endY = size.height,
                                            ),
                                            topLeft = Offset(0f, topY),
                                            size = Size(size.width, fadePx),
                                            blendMode = BlendMode.DstOut,
                                        )
                                    }
                                }
                                .scrollEndHaptic()
                                .verticalScroll(scrollState)
                                .padding(horizontal = textMargin),
                        )
                    }
                    if (!preview) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = textMargin, top = 12.dp)
                                .toggleable(
                                    value = skip,
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        skip = it
                                        haptic.performHapticFeedback(
                                            if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                        )
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(state = ToggleableState(skip), onClick = null)
                            Text(
                                "跳过本次更新",
                                fontSize = 14.sp,
                                color = COUITheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "稍后",
                            onClick = { show = false },
                        ),
                        positive = DialogButtonBarAction(
                            text = if (inApp) "立即更新" else "浏览器下载",
                            recommended = true,
                            onClick = {
                                if (inApp) {
                                    startInAppDownload()
                                } else {
                                    browserDownload = true
                                    show = false
                                }
                            },
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes < 1024L) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2fMB", mb)
}
