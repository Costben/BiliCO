package erl.bilico.feature.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.settings.AutoNextPreferences
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.data.settings.StatsOverlayPreferences
import erl.bilico.feature.video.rememberDraftIntSliderState
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem

/** 设置页「播放器」分组条目，包含播放画质、后台/画中画、手势交互、顶栏/浮窗与自动连播。 */
internal fun playerSettingsEntries(
    quality: Int,
    cdnOption: String,
    inAppBackgroundPlay: Boolean,
    backgroundPlay: Boolean,
    autoPip: Boolean,
    autoRotateMode: Int,
    longPressSpeed: Int,
    adjustableLongPressSpeed: Boolean,
    swipeDownLockLongPressSpeed: Boolean,
    gestureVolBright: Boolean,
    fullscreenGestureHint: Boolean,
    gestureHaptic: Boolean,
    playerConicLight: Boolean,
    statsOverlayEnabled: Boolean,
    statsOverlayX: Float,
    statsOverlayY: Float,
    statsOverlayVisibleCount: Int,
    autoNextRecommendPopup: Boolean,
    autoNextCountdownSeconds: Int,
    onQualityChange: (Int) -> Unit,
    onPickCdn: () -> Unit,
    onInAppBackgroundPlayChange: (Boolean) -> Unit,
    onBackgroundPlayChange: (Boolean) -> Unit,
    onAutoPipChange: (Boolean) -> Unit,
    onAutoRotateModeChange: (Int) -> Unit,
    onLongPressSpeedChange: (Int) -> Unit,
    onAdjustableLongPressSpeedChange: (Boolean) -> Unit,
    onSwipeDownLockLongPressSpeedChange: (Boolean) -> Unit,
    onGestureVolBrightChange: (Boolean) -> Unit,
    onFullscreenGestureHintChange: (Boolean) -> Unit,
    onGestureHapticChange: (Boolean) -> Unit,
    onOpenTopBarSettings: () -> Unit,
    onOpenStatsOverlaySettings: () -> Unit,
    onPlayerConicLightChange: (Boolean) -> Unit,
    onAutoNextRecommendPopupChange: (Boolean) -> Unit,
    onAutoNextCountdownSecondsChange: (Int) -> Unit,
): List<SettingsEntry> {
    val selectedQuality = DefaultQuality.fromId(quality)
    val qualitySummary = if (selectedQuality.needVip) {
        "需要大会员，无权限或片源不支持时自动降到可用最高画质"
    } else {
        ""
    }
    val cdnLabel = CdnOption.fromId(cdnOption).label
    val statsSummary = StatsOverlayPreferences.summary(
        enabled = statsOverlayEnabled,
        xFraction = statsOverlayX,
        yFraction = statsOverlayY,
        visibleCount = statsOverlayVisibleCount,
    )
    return listOf(
        SettingsEntry(
            id = "quality",
            title = "默认清晰度",
            summary = qualitySummary,
            keywords = "画质 4K 1080P 720P 60帧 大会员",
        ) { pos ->
            val qualityEntry = remember(selectedQuality) {
                DropdownEntry(
                    DefaultQuality.entries.map { option ->
                        DropdownItem(
                            text = option.label,
                            summary = if (option.needVip) "大会员可用，不可用时自动降级" else null,
                            selected = option == selectedQuality,
                            onClick = { onQualityChange(option.id) },
                        )
                    },
                )
            }
            SettingsDropdownRow(
                entry = qualityEntry,
                title = "默认清晰度",
                summary = qualitySummary.ifEmpty { null },
                modifier = Modifier.testTag("settings_quality"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "cdn",
            title = "播放线路",
            summary = cdnLabel,
            keywords = "CDN PCDN 节点 镜像 缓冲 加载",
        ) { pos ->
            SettingsArrowRow(
                title = "播放线路",
                modifier = Modifier.testTag("settings_cdn"),
                summary = cdnLabel,
                cardListPosition = pos,
                onClick = onPickCdn,
            )
        },
        SettingsEntry(
            id = "in_app_bg_play",
            title = "应用内后台播放",
            summary = "返回上一级时缩到底栏融合球继续播放",
            keywords = "融合球 小窗 音频 后台",
        ) { pos ->
            SettingsSwitchRow(
                checked = inAppBackgroundPlay,
                onCheckedChange = onInAppBackgroundPlayChange,
                title = "应用内后台播放",
                summary = "返回上一级时缩到底栏融合球继续播放",
                modifier = Modifier.testTag("settings_in_app_bg_play"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "bg_play",
            title = "应用外后台播放",
            summary = "切到桌面或其他应用时继续播放",
            keywords = "锁屏 听视频 音频 后台",
        ) { pos ->
            SettingsSwitchRow(
                checked = backgroundPlay,
                onCheckedChange = onBackgroundPlayChange,
                title = "应用外后台播放",
                summary = "切到桌面或其他应用时继续播放",
                modifier = Modifier.testTag("settings_bg_play"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "auto_pip",
            title = "自动画中画",
            keywords = "PiP 小窗 悬浮窗 后台",
        ) { pos ->
            SettingsSwitchRow(
                checked = autoPip,
                onCheckedChange = onAutoPipChange,
                title = "自动画中画",
                modifier = Modifier.testTag("settings_auto_pip"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "auto_rotate",
            title = "自动横屏",
            keywords = "旋转 重力 方向 全屏 竖屏",
        ) { pos ->
            SettingsDropdownRow(
                items = AUTO_ROTATE_OPTIONS,
                selectedIndex = autoRotateMode.coerceIn(0, AUTO_ROTATE_OPTIONS.lastIndex),
                title = "自动横屏",
                modifier = Modifier.testTag("settings_auto_rotate"),
                cardListPosition = pos,
                onSelectedIndexChange = onAutoRotateModeChange,
            )
        },
        SettingsEntry(
            id = "long_press_speed",
            title = "长按倍速",
            keywords = "速度 加速 变速 下滑锁定 2x 3x speed rate",
        ) { pos ->
            LongPressSpeedPreference(
                speedOptions = LONG_PRESS_SPEED_OPTIONS,
                selectedPercent = longPressSpeed,
                adjustableSpeed = adjustableLongPressSpeed,
                swipeDownLock = swipeDownLockLongPressSpeed,
                onSelectPercent = onLongPressSpeedChange,
                onAdjustableChange = onAdjustableLongPressSpeedChange,
                onSwipeDownLockChange = onSwipeDownLockLongPressSpeedChange,
                modifier = Modifier.testTag("settings_long_press_speed"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "gesture_vol_bright",
            title = "手势调节亮度与音量",
            keywords = "滑动 声音 屏幕亮度 手势",
        ) { pos ->
            SettingsSwitchRow(
                checked = gestureVolBright,
                onCheckedChange = onGestureVolBrightChange,
                title = "手势调节亮度与音量",
                modifier = Modifier.testTag("settings_gesture_vol_bright"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "fs_hint",
            title = "全屏切换提示",
            keywords = "全屏 提示 手势 上滑 下滑",
        ) { pos ->
            SettingsSwitchRow(
                checked = fullscreenGestureHint,
                onCheckedChange = onFullscreenGestureHintChange,
                title = "全屏切换提示",
                modifier = Modifier.testTag("settings_fs_hint"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "gesture_haptic",
            title = "手势震动",
            keywords = "触感 振动 反馈",
        ) { pos ->
            SettingsSwitchRow(
                checked = gestureHaptic,
                onCheckedChange = onGestureHapticChange,
                title = "手势震动",
                modifier = Modifier.testTag("settings_gesture_haptic"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "player_top_bar",
            title = "播放器顶栏",
            summary = "设置全屏与竖屏时顶栏显示哪些按钮",
            keywords = "按钮 排序 投屏 画中画 顶部栏",
        ) { pos ->
            SettingsArrowRow(
                title = "播放器顶栏",
                summary = "设置全屏与竖屏时顶栏显示哪些按钮",
                modifier = Modifier.testTag("settings_player_top_bar"),
                cardListPosition = pos,
                onClick = onOpenTopBarSettings,
            )
        },
        SettingsEntry(
            id = "stats_overlay",
            title = "统计信息浮窗",
            summary = statsSummary,
            keywords = "帧率 码率 编码 分辨率 主机 缓冲 调试",
        ) { pos ->
            SettingsArrowRow(
                title = "统计信息浮窗",
                summary = statsSummary,
                modifier = Modifier.testTag("settings_stats_overlay"),
                cardListPosition = pos,
                onClick = onOpenStatsOverlaySettings,
            )
        },
        SettingsEntry(
            id = "player_conic_light",
            title = "进度条光感",
            summary = "播放或拖动视频进度条时显示锥形光效",
            keywords = "光效 ColorOS 进度条",
        ) { pos ->
            SettingsSwitchRow(
                checked = playerConicLight,
                onCheckedChange = onPlayerConicLightChange,
                title = "进度条光感",
                summary = "播放或拖动视频进度条时显示锥形光效",
                modifier = Modifier.testTag("settings_player_conic_light"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "auto_next_popup",
            title = "推荐视频连播弹窗",
            summary = "自动连播且非分批播放时，在右下角弹出下一个推荐视频",
            keywords = "自动连播 下一集 连续播放",
        ) { pos ->
            SettingsSwitchRow(
                checked = autoNextRecommendPopup,
                onCheckedChange = onAutoNextRecommendPopupChange,
                title = "推荐视频连播弹窗",
                summary = "自动连播且非分批播放时，在右下角弹出下一个推荐视频",
                modifier = Modifier.testTag("settings_auto_next_popup"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "auto_next_countdown",
            title = "连播倒计时",
            summary = "倒计时结束后自动播放下一个推荐视频",
            keywords = "自动连播 秒数 下一集",
        ) { pos ->
            val sliderState = rememberDraftIntSliderState(
                autoNextCountdownSeconds.coerceIn(
                    AutoNextPreferences.MIN_COUNTDOWN_SECONDS,
                    AutoNextPreferences.MAX_COUNTDOWN_SECONDS,
                ),
            )
            SettingsSliderRow(
                value = sliderState.draftValue,
                onValueChange = { sliderState.draftValue = it },
                onValueChangeFinished = { sliderState.commit(onAutoNextCountdownSecondsChange) },
                title = "连播倒计时",
                summary = "倒计时结束后自动播放下一个推荐视频",
                valueText = "${sliderState.roundedInt} 秒",
                enabled = autoNextRecommendPopup,
                valueRange = AutoNextPreferences.MIN_COUNTDOWN_SECONDS.toFloat()..
                    AutoNextPreferences.MAX_COUNTDOWN_SECONDS.toFloat(),
                steps = AutoNextPreferences.MAX_COUNTDOWN_SECONDS -
                    AutoNextPreferences.MIN_COUNTDOWN_SECONDS - 1,
                modifier = Modifier.testTag("settings_auto_next_countdown"),
                cardListPosition = pos,
            )
        },
    )
}

private val AUTO_ROTATE_OPTIONS = listOf("关闭", "迟钝 (防误触, ±15°)", "标准 (±25°)", "灵敏 (±35°)")
private val LONG_PRESS_SPEED_OPTIONS = listOf(150, 200, 250, 300, 400)
