package erl.bilico.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.layout.SheetActionButton
import io.github.suqi8.coui.kmp.layout.SheetCloseCapsule
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.Locale
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/**
 * 竖屏下的弹幕设置：COUI 底部面板，开关、下拉菜单和滑条都用设置页同一套组件。
 * 全屏横屏时面板要跟着播放器一起转，仍走 [DanmakuSettingsDialog] 里的舞台内面板。
 */
@Composable
internal fun DanmakuSettingsSheet(
    settings: DanmakuSettings,
    repository: SettingsRepository,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayBottomSheet(
        show = show,
        title = null,
        insideMargin = DpSize(12.dp, 0.dp),
        startAction = {
            Box(Modifier.fillMaxWidth()) {
                SheetCloseCapsule(
                    onClick = { show = false },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    text = "弹幕设置",
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = COUITheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        },
        endAction = {
            SheetActionButton(
                onClick = { confirmReset = true },
                modifier = Modifier.testTag("dm_reset"),
            ) {
                Icon(COUIIcons.Refresh, "恢复默认", Modifier.size(24.dp))
            }
        },
        onDismissRequest = { show = false },
        onDismissFinished = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .testTag("danmaku_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "display-title") { SectionTitle("显示", topPadding = 4.dp) }
            item(key = "display") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    DraftSliderPreference(
                        persistedValue = settings.opacity,
                        onCommit = repository::setDanmakuOpacity,
                        title = "不透明度",
                        formatValueText = { "$it%" },
                        valueRange = 10f..100f,
                        cardListPosition = CardListPosition.Head,
                    )
                    DraftSliderPreference(
                        persistedValue = settings.fontScale,
                        onCommit = repository::setDanmakuFontScale,
                        title = "字号",
                        formatValueText = { "$it%" },
                        valueRange = 50f..200f,
                        cardListPosition = CardListPosition.Middle,
                    )
                    OverlayDropdownPreference(
                        items = AREA_LABELS,
                        selectedIndex = settings.area.coerceIn(0, AREA_LABELS.lastIndex),
                        title = "显示区域",
                        cardListPosition = CardListPosition.Tail,
                        onSelectedIndexChange = repository::setDanmakuArea,
                    )
                }
            }

            item(key = "filter-title") { SectionTitle("显示的弹幕类型") }
            item(key = "filter") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    SwitchPreference(
                        checked = settings.allowScroll,
                        onCheckedChange = repository::setDanmakuAllowScroll,
                        title = "滚动弹幕",
                        cardListPosition = CardListPosition.Head,
                    )
                    SwitchPreference(
                        checked = settings.allowTop,
                        onCheckedChange = repository::setDanmakuAllowTop,
                        title = "顶部弹幕",
                        cardListPosition = CardListPosition.Middle,
                    )
                    SwitchPreference(
                        checked = settings.allowBottom,
                        onCheckedChange = repository::setDanmakuAllowBottom,
                        title = "底部弹幕",
                        cardListPosition = CardListPosition.Middle,
                    )
                    SwitchPreference(
                        checked = settings.allowColorful,
                        onCheckedChange = repository::setDanmakuAllowColorful,
                        title = "彩色弹幕",
                        cardListPosition = CardListPosition.Middle,
                    )
                    SwitchPreference(
                        checked = settings.allowSpecial,
                        onCheckedChange = repository::setDanmakuAllowSpecial,
                        title = "高级弹幕",
                        cardListPosition = CardListPosition.Tail,
                    )
                }
            }

            item(key = "feature-title") { SectionTitle("功能") }
            item(key = "feature") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    SwitchPreference(
                        checked = settings.mergeDuplicates,
                        onCheckedChange = repository::setDanmakuMergeDuplicates,
                        title = "合并重复弹幕",
                        cardListPosition = CardListPosition.Head,
                    )
                    SwitchPreference(
                        checked = settings.massiveMode,
                        onCheckedChange = repository::setDanmakuMassiveMode,
                        title = "海量弹幕模式",
                        cardListPosition = CardListPosition.Tail,
                    )
                }
            }

            item(key = "style-title") { SectionTitle("样式") }
            item(key = "style") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    DraftSliderPreference(
                        persistedValue = settings.speed,
                        onCommit = repository::setDanmakuSpeed,
                        title = "弹幕速度",
                        formatValueText = { v ->
                            val sec = settings.copy(speed = v).scrollDurationMs / 1000f
                            "$v% (${String.format(Locale.US, "%.1fs", sec)})"
                        },
                        valueRange = 50f..200f,
                        cardListPosition = CardListPosition.Head,
                    )
                    DraftSliderPreference(
                        persistedValue = settings.strokeWidth,
                        onCommit = repository::setDanmakuStrokeWidth,
                        title = "描边粗细",
                        formatValueText = { v ->
                            String.format(Locale.US, "%.1f dp", settings.copy(strokeWidth = v).strokeWidthDp)
                        },
                        valueRange = 0f..30f,
                        cardListPosition = CardListPosition.Middle,
                    )
                    DraftSliderPreference(
                        persistedValue = settings.fontWeight,
                        onCommit = repository::setDanmakuFontWeight,
                        title = "字体粗细",
                        formatValueText = { "W${it * 100}" },
                        valueRange = 1f..9f,
                        steps = 7,
                        cardListPosition = CardListPosition.Middle,
                    )
                    DraftSliderPreference(
                        persistedValue = settings.lineHeight,
                        onCommit = repository::setDanmakuLineHeight,
                        title = "行高间距",
                        formatValueText = { v ->
                            String.format(Locale.US, "%.2fx", settings.copy(lineHeight = v).lineHeightRatio)
                        },
                        valueRange = 100f..200f,
                        cardListPosition = CardListPosition.Tail,
                    )
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
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

@Composable
private fun DraftSliderPreference(
    persistedValue: Int,
    onCommit: (Int) -> Unit,
    title: String,
    formatValueText: (Int) -> String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    cardListPosition: CardListPosition,
) {
    val state = rememberDraftIntSliderState(persistedValue)
    SliderPreference(
        value = state.draftValue,
        onValueChange = { state.draftValue = it },
        onValueChangeFinished = { state.commit(onCommit) },
        title = title,
        valueText = formatValueText(state.roundedInt),
        valueRange = valueRange,
        steps = steps,
        cardListPosition = cardListPosition,
    )
}
