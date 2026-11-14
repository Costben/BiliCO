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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.ui.components.ConfirmDialog
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.squircle.SquircleShape
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
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
    ) {
        val scheme = COUITheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (inlineInStage) 12.dp else 24.dp)
                .clip(SquircleShape(24.dp))
                .background(if (inlineInStage) Color(0xF51F1F22) else scheme.surfaceContainer)
                .padding(vertical = 12.dp)
                .testTag("danmaku_settings_dialog"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "弹幕设置",
                    style = COUITheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                HighlightedIconButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.testTag("dm_reset"),
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
                    valueText = "${settings.opacity}%",
                    value = settings.opacity.toFloat(),
                    valueRange = 10f..100f,
                    onValueChange = { repository.setDanmakuOpacity(it.roundToInt()) },
                )

                SliderSettingRow(
                    label = "字体大小",
                    valueText = "${settings.fontScale}%",
                    value = settings.fontScale.toFloat(),
                    valueRange = 50f..200f,
                    onValueChange = { repository.setDanmakuFontScale(it.roundToInt()) },
                )

                SliderSettingRow(
                    label = "弹幕速度",
                    valueText = "${settings.speed}% (${String.format(Locale.US, "%.1fs", settings.scrollDurationMs / 1000f)})",
                    value = settings.speed.toFloat(),
                    valueRange = 50f..200f,
                    onValueChange = { repository.setDanmakuSpeed(it.roundToInt()) },
                )

                SliderSettingRow(
                    label = "描边粗细",
                    valueText = String.format(Locale.US, "%.1f dp", settings.strokeWidthDp),
                    value = settings.strokeWidth.toFloat(),
                    valueRange = 0f..30f,
                    onValueChange = { repository.setDanmakuStrokeWidth(it.roundToInt()) },
                )

                SliderSettingRow(
                    label = "字体粗细",
                    valueText = "W${settings.fontWeight * 100}",
                    value = settings.fontWeight.toFloat(),
                    valueRange = 1f..9f,
                    steps = 7,
                    onValueChange = { repository.setDanmakuFontWeight(it.roundToInt()) },
                )

                SliderSettingRow(
                    label = "行高间距",
                    valueText = String.format(Locale.US, "%.2fx", settings.lineHeightRatio),
                    value = settings.lineHeight.toFloat(),
                    valueRange = 100f..200f,
                    onValueChange = { repository.setDanmakuLineHeight(it.roundToInt()) },
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
            .clip(RoundedCornerShape(10.dp))
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

@Composable
private fun SliderSettingRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    val scheme = COUITheme.colorScheme
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
                text = valueText,
                style = COUITheme.textStyles.footnote1,
                color = scheme.primary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal val AREA_LABELS = listOf("1/4 屏", "半屏", "3/4 屏", "全屏")
