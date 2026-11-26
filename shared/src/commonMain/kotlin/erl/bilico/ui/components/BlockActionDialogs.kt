package erl.bilico.ui.components

import erl.bilico.platform.showToast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.repository.BlockKeywordScope
import erl.bilico.di.LocalAppContainer
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Help
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/**
 * 关键词屏蔽添加弹窗：
 * - [allowedScopes] 为单个作用域时（如屏蔽管理里各自点击“添加”），隐藏分类选项，仅针对该作用域输入；
 * - [allowedScopes] 为多个作用域时，切换分类若输入框尚未被手动改写，会自动拉取对应字段默认值。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordBlockDialog(
    allowedScopes: List<BlockKeywordScope> = BlockKeywordScope.entries,
    initialScope: BlockKeywordScope = allowedScopes.firstOrNull() ?: BlockKeywordScope.VIDEO_TITLE,
    scopeInitialKeywords: Map<BlockKeywordScope, String> = emptyMap(),
    initialKeyword: String = scopeInitialKeywords[initialScope].orEmpty(),
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current ?: return
    val context = LocalPlatformContext.current
    val scheme = COUITheme.colorScheme
    val scopes = remember(allowedScopes) { allowedScopes.ifEmpty { BlockKeywordScope.entries } }
    val resolvedInitialScope = remember(scopes, initialScope) {
        if (initialScope in scopes) initialScope else scopes.first()
    }

    var scopeChoice by remember(resolvedInitialScope) { mutableStateOf(resolvedInitialScope) }
    val initialForScope = scopeInitialKeywords[scopeChoice]
        ?: if (scopeChoice == resolvedInitialScope) initialKeyword else ""

    val dialogTitle = if (scopes.size == 1) {
        "添加${scopes.first().label}屏蔽词"
    } else {
        "添加屏蔽关键词"
    }

    SingleInputDialog(
        title = dialogTitle,
        summary = "匹配内容将自动隐藏（支持正则表达式，可用 | 分隔多词）",
        hint = "输入${scopeChoice.label}关键词或正则",
        initial = initialForScope,
        confirmText = "添加",
        topContent = if (scopes.size > 1) {
            {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    scopes.forEach { scope ->
                        val selected = scopeChoice == scope
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
                                .clickable { scopeChoice = scope }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = scope.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = if (selected) scheme.onPrimary else scheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        } else {
            null
        },
        onDismiss = onDismiss,
        onConfirm = { trimmed ->
            container.blocks.addKeyword(scopeChoice, trimmed)
            showToast(context, "已添加屏蔽词：$trimmed")
            onDismiss()
        },
    )
}

/**
 * UP 主 / 用户通用操作菜单（视频页关注旁 3 个点 & 个人空间右上角 3 个点）：
 * 顺序：复制简介（视频页有简介时） -> 关键词屏蔽 -> 本地屏蔽该 UP 主 -> 屏蔽该 UP 主（服务器黑名单） -> 举报 UP 主 -> 举报视频（视频页）。
 */
@Composable
fun UserMoreMenu(
    mid: Long,
    name: String,
    face: String,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 28.dp,
    /** 顶栏版本：使用 COUI 的玻璃圆形动作按钮，尺寸与搜索按钮一致。 */
    highlighted: Boolean = false,
    videoAid: Long = 0L,
    videoBvid: String = "",
    videoTitle: String = "",
    videoDesc: String = "",
) {
    if (mid <= 0L) return
    val container = LocalAppContainer.current ?: return
    val context = LocalPlatformContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val blocks by container.blocks.snapshot.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showMemberReport by remember(mid) { mutableStateOf(false) }
    var showVideoReport by remember(videoAid) { mutableStateOf(false) }
    var showKeyword by remember(mid, videoBvid) { mutableStateOf(false) }

    if (showMemberReport) {
        ReportDialog(
            target = ReportTarget.Member(mid = mid, name = name),
            onDismiss = { showMemberReport = false },
        )
    }
    if (showVideoReport && videoAid > 0L) {
        ReportDialog(
            target = ReportTarget.Video(aid = videoAid, bvid = videoBvid, title = videoTitle),
            onDismiss = { showVideoReport = false },
        )
    }
    if (showKeyword) {
        val allowed = if (videoTitle.isNotBlank()) {
            listOf(BlockKeywordScope.UP_NAME, BlockKeywordScope.VIDEO_TITLE)
        } else {
            listOf(BlockKeywordScope.UP_NAME)
        }
        val map = buildMap {
            put(BlockKeywordScope.UP_NAME, name)
            if (videoTitle.isNotBlank()) put(BlockKeywordScope.VIDEO_TITLE, videoTitle)
        }
        KeywordBlockDialog(
            allowedScopes = allowed,
            initialScope = BlockKeywordScope.UP_NAME,
            scopeInitialKeywords = map,
            onDismiss = { showKeyword = false },
        )
    }

    val isLocalBlocked = mid in blocks.localBlockedMids
    val isServerBlack = blocks.isServerBlacklisted(mid)
    Box(modifier = modifier) {
        if (highlighted) {
            HighlightedIconButton(
                onClick = { expanded = true },
                modifier = Modifier.couiTopAppBarActionItem(),
            ) {
                Icon(
                    imageVector = COUIIcons.More,
                    contentDescription = "更多",
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .clickable { expanded = true }
                    .semantics { contentDescription = "更多" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = COUIIcons.More,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
        CouiPopupMenu(
            show = expanded,
            onDismiss = { expanded = false },
            items = buildList {
                val trimmedDesc = videoDesc.trim()
                if (trimmedDesc.isNotEmpty()) {
                    add(
                        CouiMenuItem(
                            label = "复制简介",
                            group = 0,
                            onClick = {
                                clipboard.setText(AnnotatedString(trimmedDesc))
                                showToast(context, "已复制简介")
                            },
                        ),
                    )
                }
                add(CouiMenuItem("关键词屏蔽", onClick = { showKeyword = true }, group = 1))
                add(
                    CouiMenuItem(
                        label = if (isLocalBlocked) "取消本地屏蔽" else "本地屏蔽该 UP 主",
                        group = 1,
                        onClick = {
                            if (isLocalBlocked) {
                                container.blocks.unblockUserLocally(mid)
                                showToast(context, "已取消本地屏蔽")
                            } else {
                                container.blocks.blockUserLocally(mid, name, face)
                                showToast(context, "已本地屏蔽 UP 主 $name")
                            }
                        },
                    ),
                )
                add(
                    CouiMenuItem(
                        label = if (isServerBlack) "取消屏蔽该 UP 主" else "屏蔽该 UP 主",
                        group = 1,
                        onClick = {
                            scope.launch {
                                container.blocks.toggleServerBlacklist(
                                    mid = mid,
                                    add = !isServerBlack,
                                    name = name,
                                    face = face,
                                ).onSuccess {
                                    showToast(context, if (isServerBlack) "已移出 B 站黑名单" else "已屏蔽该 UP 主（已同步账号黑名单）")
                                }.onFailure { err ->
                                    showToast(context, err.message ?: "操作失败")
                                }
                            }
                        },
                    ),
                )
                add(CouiMenuItem("举报 UP 主", onClick = { showMemberReport = true }, group = 2))
                if (videoAid > 0L) {
                    add(CouiMenuItem("举报视频", onClick = { showVideoReport = true }, group = 2))
                }
            },
        )
    }
}

@Composable
fun RegexHelpButton(onClick: () -> Unit) {
    HighlightedIconButton(onClick = onClick) {
        Icon(COUIIcons.Help, "正则说明", Modifier.size(24.dp))
    }
}

private const val AI_REGEX_PROMPT =
    "请帮我写一条用于 B 站内容屏蔽的正则表达式（仅输出一行正则表达式本身，不要解释也不要代码块）：\n" +
        "我想屏蔽的内容是：【在此填写你想屏蔽的主题、句式或 UP 名特征】\n" +
        "编写要求：\n" +
        "1. 使用标准正则语法，多个条件用 | 分隔；\n" +
        "2. 涉及 () [] 【】 ? . + * 等符号请做好转义；\n" +
        "3. 默认已忽略大小写，不需要加 /.../ 或 (?i) 前缀，尽量避免误伤正常内容。"

@Composable
fun RegexHelpDialog(onDismiss: () -> Unit) {
    val context = LocalPlatformContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scheme = COUITheme.colorScheme
    var show by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "正则表达式规则",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "常用正则语法（默认不区分大小写，语法无效时自动回退为普通关键词包含匹配）：",
                    style = COUITheme.textStyles.body2,
                    color = scheme.onSurface,
                )
                Text(
                    text = "• |  匹配多个关键词：广告|推广|带货\n" +
                        "• .* 匹配中间任意内容：开箱.*评测\n" +
                        "• ^ / $ 匹配开头或结尾：^官方、纯享版$\n" +
                        "• () 分组匹配：(抽奖|福利)来了\n" +
                        "• \\  特殊符号转义：\\[置顶\\]",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = scheme.onSurfaceSecondary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceContainerHigh)
                        .padding(10.dp),
                ) {
                    Text(
                        text = AI_REGEX_PROMPT,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = scheme.onSurfaceSecondary,
                    )
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "关闭", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "复制提示词",
                    onClick = {
                        clipboard.setText(AnnotatedString(AI_REGEX_PROMPT))
                        showToast(context, "已复制提示词")
                        show = false
                    },
                ),
            )
        }
    }
}
