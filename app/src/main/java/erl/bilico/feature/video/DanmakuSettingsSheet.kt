package erl.bilico.feature.video

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.layout.SheetActionButton
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import java.util.Locale
import kotlin.math.roundToInt

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
        title = "弹幕设置",
        insideMargin = DpSize(12.dp, 0.dp),
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
        LazyColumn(Modifier.testTag("danmaku_settings_sheet")) {
            item(key = "display-title") { SectionTitle("显示") }
            item(key = "display") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    SliderPreference(
                        value = settings.opacity.toFloat(),
                        onValueChange = { repository.setDanmakuOpacity(it.roundToInt()) },
                        title = "不透明度",
                        valueText = "${settings.opacity}%",
                        valueRange = 10f..100f,
                        cardListPosition = CardListPosition.Head,
                    )
                    SliderPreference(
                        value = settings.fontScale.toFloat(),
                        onValueChange = { repository.setDanmakuFontScale(it.roundToInt()) },
                        title = "字号",
                        valueText = "${settings.fontScale}%",
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
                    SliderPreference(
                        value = settings.speed.toFloat(),
                        onValueChange = { repository.setDanmakuSpeed(it.roundToInt()) },
                        title = "弹幕速度",
                        valueText = "${settings.speed}% (" +
                            String.format(Locale.US, "%.1fs", settings.scrollDurationMs / 1000f) + ")",
                        valueRange = 50f..200f,
                        cardListPosition = CardListPosition.Head,
                    )
                    SliderPreference(
                        value = settings.strokeWidth.toFloat(),
                        onValueChange = { repository.setDanmakuStrokeWidth(it.roundToInt()) },
                        title = "描边粗细",
                        valueText = String.format(Locale.US, "%.1f dp", settings.strokeWidthDp),
                        valueRange = 0f..30f,
                        cardListPosition = CardListPosition.Middle,
                    )
                    SliderPreference(
                        value = settings.fontWeight.toFloat(),
                        onValueChange = { repository.setDanmakuFontWeight(it.roundToInt()) },
                        title = "字体粗细",
                        valueText = "W${settings.fontWeight * 100}",
                        valueRange = 1f..9f,
                        steps = 7,
                        cardListPosition = CardListPosition.Middle,
                    )
                    SliderPreference(
                        value = settings.lineHeight.toFloat(),
                        onValueChange = { repository.setDanmakuLineHeight(it.roundToInt()) },
                        title = "行高间距",
                        valueText = String.format(Locale.US, "%.2fx", settings.lineHeightRatio),
                        valueRange = 100f..200f,
                        cardListPosition = CardListPosition.Tail,
                    )
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
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
