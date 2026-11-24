package erl.bilico.feature.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.ui.AccentHueCycle
import erl.bilico.ui.AccentPresets
import erl.bilico.ui.DefaultAccentSwatchColor
import erl.bilico.ui.accentColorSummary
import erl.bilico.ui.formatHexColor
import erl.bilico.ui.hueCycleArgb
import erl.bilico.ui.isDarkTheme
import erl.bilico.ui.parseHexColor
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Check
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

private val RainbowSwatchColors = listOf(
    Color(0xFFFF3B30),
    Color(0xFFFF9500),
    Color(0xFFFFCC00),
    Color(0xFF34C759),
    Color(0xFF00C7BE),
    Color(0xFF007AFF),
    Color(0xFFAF52DE),
    Color(0xFFFF3B30),
)

/**
 * 主题强调色选择器：对齐 Cobo COUI 骨架，提供预设色板（含 B 站粉与 Jeb_ 动态循环）、
 * HSV 三通道微调与十六进制色号输入，仅替换强调色家族而不改动页面与卡片中性灰。
 */
@Composable
internal fun AccentColorPickerDialog(
    darkMode: Int,
    currentArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var apply by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    val dark = isDarkTheme(darkMode)
    val themePrimary = COUITheme.colorScheme.primary
    var pickedArgb by remember { mutableIntStateOf(currentArgb) }
    val initialColor = when (currentArgb) {
        0 -> DefaultAccentSwatchColor
        AccentHueCycle -> themePrimary
        else -> Color(currentArgb)
    }
    val initialHsv = remember {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(formatHexColor(initialColor.toArgb())) }

    val isHueCycle = pickedArgb == AccentHueCycle
    LaunchedEffect(isHueCycle, dark) {
        if (!isHueCycle) return@LaunchedEffect
        val startMs = System.currentTimeMillis()
        val startHue = hue
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            val nextHue = (startHue + elapsed / 100f) % 360f
            val cycleRgb = hueCycleArgb(nextHue, dark)
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(cycleRgb, hsv)
            hue = hsv[0]
            saturation = hsv[1]
            brightness = hsv[2]
            hexInput = formatHexColor(cycleRgb)
            delay(120L)
        }
    }

    val previewColor = when (pickedArgb) {
        0 -> DefaultAccentSwatchColor
        AccentHueCycle -> Color(hueCycleArgb(hue, dark))
        else -> Color(pickedArgb)
    }

    fun selectArgb(argb: Int) {
        pickedArgb = argb
        if (argb == AccentHueCycle) return
        val displayArgb = if (argb == 0) DefaultAccentSwatchColor.toArgb() else argb
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(displayArgb, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        hexInput = formatHexColor(displayArgb)
    }

    fun updateFromHsv() {
        val c = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
        pickedArgb = c
        hexInput = formatHexColor(c)
    }

    OverlayDialog(
        show = show,
        title = "主题色",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                if (apply) onConfirm(pickedArgb) else onDismiss()
            }
        },
    ) {
        Column {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, COUITheme.colorScheme.outline, CircleShape),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = accentColorSummary(pickedArgb),
                            fontSize = 14.sp,
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "强调色预览 · @UP主 · #话题#",
                            fontSize = 12.sp,
                            color = previewColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (pickedArgb != 0) {
                        TextButton(
                            text = "恢复默认",
                            onClick = { selectArgb(0) },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentPresets.chunked(5).forEachIndexed { rowIdx, rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            rowPresets.forEachIndexed { colIdx, preset ->
                                val presetIndex = rowIdx * 5 + colIdx
                                val isRainbowPreset = preset.argb == AccentHueCycle
                                val swatchColor = when (preset.argb) {
                                    0 -> DefaultAccentSwatchColor
                                    AccentHueCycle -> previewColor
                                    else -> Color(preset.argb)
                                }
                                val selected = when (preset.argb) {
                                    0 -> pickedArgb == 0
                                    AccentHueCycle -> pickedArgb == AccentHueCycle
                                    else -> pickedArgb != 0 && pickedArgb != AccentHueCycle &&
                                        (pickedArgb or 0xFF000000.toInt()) == preset.argb
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectArgb(preset.argb) }
                                        .padding(horizontal = 2.dp, vertical = 4.dp)
                                        .testTag("accent_preset_$presetIndex"),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (isRainbowPreset) {
                                                    Modifier.background(Brush.sweepGradient(RainbowSwatchColors))
                                                } else {
                                                    Modifier.background(swatchColor)
                                                },
                                            )
                                            .then(
                                                if (selected) {
                                                    Modifier.border(2.dp, COUITheme.colorScheme.onSurface, CircleShape)
                                                } else {
                                                    Modifier.border(1.dp, COUITheme.colorScheme.outline, CircleShape)
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = COUIIcons.Basic.Check,
                                                contentDescription = "已选",
                                                tint = if (!isRainbowPreset && swatchColor.luminance() > 0.55f) {
                                                    Color(0xE6000000)
                                                } else {
                                                    Color.White
                                                },
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = preset.label,
                                        fontSize = 10.sp,
                                        color = if (selected) {
                                            COUITheme.colorScheme.onSurface
                                        } else {
                                            COUITheme.colorScheme.onSurfaceSecondary
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                AccentSliderRow(
                    label = "色相",
                    valueText = "${hue.toInt()}°",
                    value = hue,
                    valueRange = 0f..360f,
                    accentColor = previewColor,
                    onValueChange = {
                        hue = it
                        updateFromHsv()
                    },
                )
                AccentSliderRow(
                    label = "饱和度",
                    valueText = "${(saturation * 100).toInt()}%",
                    value = saturation,
                    valueRange = 0f..1f,
                    accentColor = previewColor,
                    onValueChange = {
                        saturation = it
                        updateFromHsv()
                    },
                )
                AccentSliderRow(
                    label = "明度",
                    valueText = "${(brightness * 100).toInt()}%",
                    value = brightness,
                    valueRange = 0f..1f,
                    accentColor = previewColor,
                    onValueChange = {
                        brightness = it
                        updateFromHsv()
                    },
                )

                TextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase().take(7)
                        hexInput = cleaned
                        parseHexColor(cleaned)?.let { parsed ->
                            pickedArgb = parsed
                            val hsv = FloatArray(3)
                            AndroidColor.colorToHSV(parsed, hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("accent_hex_input"),
                    label = "十六进制色号 (#RRGGBB)",
                    backgroundMode = TextFieldMode.Rectangle,
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(6.dp))
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "确定",
                    onClick = {
                        apply = true
                        show = false
                    },
                ),
            )
        }
    }
}

@Composable
private fun AccentSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
            Text(valueText, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.sliderColors(foregroundColor = accentColor),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
