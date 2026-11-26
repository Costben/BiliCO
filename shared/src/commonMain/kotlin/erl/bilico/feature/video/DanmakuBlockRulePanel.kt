package erl.bilico.feature.video

import erl.bilico.platform.showToast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.repository.BlockKeywordScope
import erl.bilico.di.LocalAppContainer
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Help
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/**
 * 弹幕关键词与正则屏蔽管理面板：复用全局 [BlockKeywordScope.DANMAKU]，
 * 支持竖屏弹窗与全屏舞台内弹窗两种承载方式，添加或删除规则后即时生效。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DanmakuKeywordBlockDialog(
    inlineInStage: Boolean,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current ?: return
    val context = LocalPlatformContext.current
    val snapshot by container.blocks.snapshot.collectAsStateWithLifecycle()
    val rules = snapshot.config.danmakuKeywords

    var draft by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val trimmed = draft.trim()
    val invalidRegex = remember(trimmed) {
        trimmed.isNotEmpty() && runCatching { Regex(trimmed, setOf(RegexOption.IGNORE_CASE)) }.isFailure
    }

    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
        horizontalPadding = if (inlineInStage) 12.dp else 20.dp,
        cornerRadius = 24.dp,
    ) {
        val scheme = COUITheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("danmaku_block_rules_dialog"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "弹幕屏蔽词",
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                HighlightedIconButton(
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(COUIIcons.Help, "正则语法说明", Modifier.size(22.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (inlineInStage) 260.dp else 420.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "支持普通关键词与正则表达式（忽略大小写），与「设置 → 屏蔽管理」同步。",
                    style = COUITheme.textStyles.footnote1,
                    color = scheme.onSurfaceSecondary,
                )

                if (showHelp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(12.dp))
                            .background(scheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "常用正则示例",
                            style = COUITheme.textStyles.footnote1,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "• | 匹配多词：剧透|前方高能|打卡\n" +
                                "• .* 匹配间隔：^第一.*打卡$\n" +
                                "• 语法无效时自动回退为普通包含匹配",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = scheme.onSurfaceSecondary,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dm_block_input"),
                        label = "输入关键词或正则",
                        backgroundMode = TextFieldMode.Line,
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "添加",
                        enabled = trimmed.isNotEmpty(),
                        onClick = {
                            if (trimmed.isNotEmpty()) {
                                container.blocks.addKeyword(BlockKeywordScope.DANMAKU, trimmed)
                                draft = ""
                                showToast(context, "已添加弹幕屏蔽规则")
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.testTag("dm_block_add"),
                    )
                }

                if (invalidRegex) {
                    Text(
                        text = "提示：当前正则语法无效，将按普通关键词匹配。",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceSecondary,
                    )
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = "已添加规则（${rules.size}）",
                    style = COUITheme.textStyles.footnote1,
                    color = scheme.onSurfaceSecondary,
                )

                if (rules.isEmpty()) {
                    Text(
                        text = "暂无弹幕屏蔽规则，点击上方输入后添加",
                        style = COUITheme.textStyles.body2,
                        color = scheme.onSurfaceSecondary,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rules.forEach { rule ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.surfaceContainerHigh)
                                    .clickable {
                                        container.blocks.removeKeyword(BlockKeywordScope.DANMAKU, rule)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "$rule ×",
                                    fontSize = 12.sp,
                                    color = scheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
