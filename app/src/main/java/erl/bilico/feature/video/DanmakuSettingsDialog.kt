package erl.bilico.feature.video

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
import androidx.compose.foundation.verticalScroll
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.di.LocalAppContainer
import erl.bilico.ui.components.ConfirmDialog
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.Locale
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

/**
 * 弹幕设置。竖屏走 [DanmakuSettingsSheet]；全屏横屏时是下面这块贴在播放器舞台里的深色面板。
 *
 * 舞台内面板（迁移自霹雳派 DanmakuSettingsPanel 与霹雳Plus PlDanmakuPanel）：
 * - 按类型屏蔽（滚动 / 顶部 / 底部 / 彩色 / 高级）
 * - 功能开关（合并重复弹幕 / 海量弹幕模式）
 * - 显示区域（1/4 屏 / 半屏 / 3/4 屏 / 全屏）
 * - 连续调节（不透明度 / 字号 / 弹幕速度 / 描边粗细 / 字体粗细 / 行高）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DanmakuSettingsDialog(
    settings: DanmakuSettings,
    repository: SettingsRepository,
    onDismiss: () -> Unit,
    inlineInStage: Boolean = false,
) {
    if (!inlineInStage) {
        DanmakuSettingsSheet(settings, repository, onDismiss)
        return
    }
    var confirmReset by remember { mutableStateOf(false) }
    var showBlockRules by remember { mutableStateOf(false) }
    val blockRepo = LocalAppContainer.current?.blocks
    val ruleCount = blockRepo?.snapshot?.collectAsStateWithLifecycle()?.value?.config?.danmakuKeywords?.size ?: 0
    if (showBlockRules) {
        DanmakuKeywordBlockDialog(
            inlineInStage = inlineInStage,
            onDismiss = { showBlockRules = false },
        )
        return
    }
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
        horizontalPadding = if (inlineInStage) 12.dp else 24.dp,
        cornerRadius = 24.dp,
    ) {
        val scheme = COUITheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("danmaku_settings_dialog"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "弹幕设置",
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                HighlightedIconButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .testTag("dm_reset"),
                ) {
                    Icon(COUIIcons.Refresh, "恢复默认", Modifier.size(24.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (inlineInStage) 280.dp else 440.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader("按类型屏蔽（高亮为开启显示）")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        label = "滚动",
                        selected = settings.allowScroll,
                        onClick = { repository.setDanmakuAllowScroll(!settings.allowScroll) },
                    )
                    FilterChip(
                        label = "顶部",
                        selected = settings.allowTop,
                        onClick = { repository.setDanmakuAllowTop(!settings.allowTop) },
                    )
                    FilterChip(
                        label = "底部",
                        selected = settings.allowBottom,
                        onClick = { repository.setDanmakuAllowBottom(!settings.allowBottom) },
                    )
                    FilterChip(
                        label = "彩色",
                        selected = settings.allowColorful,
                        onClick = { repository.setDanmakuAllowColorful(!settings.allowColorful) },
                    )
                    FilterChip(
                        label = "高级",
                        selected = settings.allowSpecial,
                        onClick = { repository.setDanmakuAllowSpecial(!settings.allowSpecial) },
                    )
                    FilterChip(
                        label = "合并重复",
                        selected = settings.mergeDuplicates,
                        onClick = { repository.setDanmakuMergeDuplicates(!settings.mergeDuplicates) },
                    )
                    FilterChip(
                        label = "海量模式",
                        selected = settings.massiveMode,
                        onClick = { repository.setDanmakuMassiveMode(!settings.massiveMode) },
                    )
                }

                if (blockRepo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(12.dp))
                            .background(scheme.surfaceContainerHigh)
                            .clickable { showBlockRules = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("dm_block_rules_entry"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "关键词与正则屏蔽",
                            style = COUITheme.textStyles.body2,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = if (ruleCount == 0) "管理 >" else "已设 $ruleCount 条 >",
                            style = COUITheme.textStyles.footnote1,
                            color = scheme.primary,
                        )
                    }
                }

                SectionHeader("显示区域")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AREA_LABELS.forEachIndexed { index, label ->
                        FilterChip(
                            label = label,
                            selected = settings.area == index,
                            onClick = { repository.setDanmakuArea(index) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SliderSettingRow(
                    label = "不透明度",
                    persistedValue = settings.opacity,
                    formatValueText = { "$it%" },
                    valueRange = 10f..100f,
                    onCommit = repository::setDanmakuOpacity,
                )

                SliderSettingRow(
                    label = "字体大小",
                    persistedValue = settings.fontScale,
                    formatValueText = { "$it%" },
                    valueRange = 50f..200f,
                    onCommit = repository::setDanmakuFontScale,
                )

                SliderSettingRow(
                    label = "弹幕速度",
                    persistedValue = settings.speed,
                    formatValueText = { v ->
                        val sec = settings.copy(speed = v).scrollDurationMs / 1000f
                        "$v% (${String.format(Locale.US, "%.1fs", sec)})"
                    },
                    valueRange = 50f..200f,
                    onCommit = repository::setDanmakuSpeed,
                )

                SliderSettingRow(
                    label = "描边粗细",
                    persistedValue = settings.strokeWidth,
                    formatValueText = { v ->
                        String.format(Locale.US, "%.1f dp", settings.copy(strokeWidth = v).strokeWidthDp)
                    },
                    valueRange = 0f..30f,
                    onCommit = repository::setDanmakuStrokeWidth,
                )

                SliderSettingRow(
                    label = "字体粗细",
                    persistedValue = settings.fontWeight,
                    formatValueText = { "W${it * 100}" },
                    valueRange = 1f..9f,
                    steps = 7,
                    onCommit = repository::setDanmakuFontWeight,
                )

                SliderSettingRow(
                    label = "行高间距",
                    persistedValue = settings.lineHeight,
                    formatValueText = { v ->
                        String.format(Locale.US, "%.2fx", settings.copy(lineHeight = v).lineHeightRatio)
                    },
                    valueRange = 100f..200f,
                    onCommit = repository::setDanmakuLineHeight,
                )
            }
        }

        if (confirmReset) {
            ConfirmDialog(
                title = "恢复默认",
                message = "确定要将弹幕设置恢复为默认值吗？",
                confirmText = "恢复",
                onDismiss = { confirmReset = false },
                onConfirm = {
                    confirmReset = false
                    repository.resetDanmakuSettings()
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceSecondary,
    )
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val bg = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceContainerHigh
    val fg = if (selected) scheme.primary else scheme.onSurfaceSecondary
    Box(
        modifier = modifier
            .clip(SquircleShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = fg,
        )
    }
}

/**
 * 拖动期间仅保存原始浮点草稿值，避免四舍五入后触发 COUI 滑块的外部同步取消手势，
 * 也避免拖动每一帧都触发弹幕重排与磁盘写入；仅在松手结束时提交整数配置。
 */
@Stable
internal class DraftIntSliderState(initialValue: Int) {
    var draftValue by mutableFloatStateOf(initialValue.toFloat())
    val roundedInt: Int get() = draftValue.roundToInt()

    fun commit(onCommit: (Int) -> Unit) {
        val committed = roundedInt
        draftValue = committed.toFloat()
        onCommit(committed)
    }
}

@Composable
internal fun rememberDraftIntSliderState(persistedValue: Int): DraftIntSliderState =
    remember(persistedValue) { DraftIntSliderState(persistedValue) }

@Composable
private fun SliderSettingRow(
    label: String,
    persistedValue: Int,
    formatValueText: (Int) -> String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onCommit: (Int) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val state = rememberDraftIntSliderState(persistedValue)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = COUITheme.textStyles.body2,
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                text = formatValueText(state.roundedInt),
                style = COUITheme.textStyles.footnote1,
                color = scheme.primary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = state.draftValue,
            onValueChange = { state.draftValue = it },
            onValueChangeFinished = { state.commit(onCommit) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal val AREA_LABELS = listOf("1/4 屏", "半屏", "3/4 屏", "全屏")
