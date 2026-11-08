package erl.bilico.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import erl.bilico.BuildConfig
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.di.AppContainer
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.feature.video.speedLabel
import erl.bilico.ui.NavState
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.PreferenceDivider
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import erl.bilico.ui.Route
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    nav: NavState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val quality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val cdnOption by container.settings.cdnOption.collectAsStateWithLifecycle()
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val longPressSpeed by container.settings.longPressSpeed.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val autoCheckUpdate by container.settings.autoCheckUpdate.collectAsStateWithLifecycle()
    val acceptPrerelease by container.settings.acceptPrerelease.collectAsStateWithLifecycle()
    val inAppUpdate by container.settings.inAppUpdate.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()

    var pickCdn by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableIntStateOf(0) }

    SecondaryPage(title = "设置", onBack = nav::pop) {
        item(key = "display-title") { SectionTitle("显示与播放") }

        item(key = "display") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                OverlayDropdownPreference(
                    items = DARK_MODE_OPTIONS,
                    selectedIndex = darkMode.coerceIn(0, DARK_MODE_OPTIONS.lastIndex),
                    title = "深色模式",
                    modifier = Modifier.testTag("settings_dark"),
                    cardListPosition = CardListPosition.Head,
                    onSelectedIndexChange = container.settings::setDarkMode,
                )
                OverlayDropdownPreference(
                    items = QUALITY_LABELS,
                    selectedIndex = DefaultQuality.entries.indexOfFirst { it.id == quality }
                        .coerceAtLeast(0),
                    title = "默认清晰度",
                    modifier = Modifier.testTag("settings_quality"),
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = { container.settings.setDefaultQuality(DefaultQuality.entries[it].id) },
                )
                ArrowPreference(
                    title = "播放线路",
                    modifier = Modifier.testTag("settings_cdn"),
                    summary = CdnOption.fromId(cdnOption).label,
                    cardListPosition = CardListPosition.Middle,
                    onClick = { pickCdn = true },
                )
                SwitchPreference(
                    checked = backgroundPlay,
                    onCheckedChange = container.settings::setBackgroundPlay,
                    title = "后台播放",
                    modifier = Modifier.testTag("settings_bg_play"),
                    cardListPosition = CardListPosition.Middle,
                )
                SwitchPreference(
                    checked = autoPip,
                    onCheckedChange = container.settings::setAutoPip,
                    title = "自动画中画",
                    modifier = Modifier.testTag("settings_auto_pip"),
                    cardListPosition = CardListPosition.Middle,
                )
                OverlayDropdownPreference(
                    items = AUTO_ROTATE_OPTIONS,
                    selectedIndex = autoRotateMode.coerceIn(0, AUTO_ROTATE_OPTIONS.lastIndex),
                    title = "自动横屏",
                    modifier = Modifier.testTag("settings_auto_rotate"),
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = container.settings::setAutoRotateMode,
                )
                OverlayDropdownPreference(
                    items = LONG_PRESS_SPEED_LABELS,
                    selectedIndex = LONG_PRESS_SPEED_OPTIONS.indexOf(longPressSpeed).coerceAtLeast(0),
                    title = "长按倍速",
                    modifier = Modifier.testTag("settings_long_press_speed"),
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = { container.settings.setLongPressSpeed(LONG_PRESS_SPEED_OPTIONS[it]) },
                )
                SwitchPreference(
                    checked = gestureVolBright,
                    onCheckedChange = container.settings::setGestureVolumeBrightness,
                    title = "手势调节亮度与音量",
                    modifier = Modifier.testTag("settings_gesture_vol_bright"),
                    cardListPosition = CardListPosition.Middle,
                )
                SwitchPreference(
                    checked = fullscreenGestureHint,
                    onCheckedChange = container.settings::setFullscreenGestureHint,
                    title = "全屏切换提示",
                    modifier = Modifier.testTag("settings_fs_hint"),
                    cardListPosition = CardListPosition.Middle,
                )
                SwitchPreference(
                    checked = gestureHaptic,
                    onCheckedChange = container.settings::setGestureHaptic,
                    title = "手势震动",
                    modifier = Modifier.testTag("settings_gesture_haptic"),
                    cardListPosition = CardListPosition.Tail,
                )
            }
        }

        item(key = "danmaku-title") { SectionTitle("弹幕") }

        item(key = "danmaku") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                SwitchPreference(
                    checked = danmakuEnabled,
                    onCheckedChange = container.settings::setDanmakuEnabled,
                    title = "弹幕",
                    modifier = Modifier.testTag("settings_danmaku"),
                    cardListPosition = CardListPosition.Head,
                )
                ArrowPreference(
                    title = "弹幕设置",
                    modifier = Modifier.testTag("settings_danmaku_detail"),
                    summary = "不透明度 · 字号 · 显示区域 · 类型屏蔽 · 速度",
                    cardListPosition = CardListPosition.Tail,
                    onClick = { showDanmakuSettings = true },
                )
            }
        }

        item(key = "storage-title") { SectionTitle("存储") }

        item(key = "storage") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                ArrowPreference(
                    title = "清除图片缓存",
                    modifier = Modifier.testTag("settings_cache"),
                    summary = if (cacheCleared > 0) "已清除" else "只清本地图片，不影响账号",
                    cardListPosition = CardListPosition.None,
                    onClick = {
                        scope.launch {
                            val loader = SingletonImageLoader.get(context)
                            loader.diskCache?.clear()
                            loader.memoryCache?.clear()
                            cacheCleared++
                        }
                    },
                )
            }
        }

        item(key = "update-title") { SectionTitle("更新") }

        item(key = "update") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                ArrowPreference(
                    title = "检查更新",
                    modifier = Modifier.testTag("settings_update"),
                    summary = if (container.updater.checkingUpdate) {
                        "正在检查…"
                    } else {
                        "当前版本 v${BuildConfig.VERSION_NAME}"
                    },
                    cardListPosition = CardListPosition.Head,
                    onClick = { scope.launch { container.updater.checkUpdate(manual = true) } },
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "更新日志",
                    modifier = Modifier.testTag("settings_changelog"),
                    summary = "检查更新页面 · 历史版本与发布说明",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.Update) },
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = autoCheckUpdate,
                    onCheckedChange = container.settings::setAutoCheckUpdate,
                    title = "自动检查更新",
                    modifier = Modifier.testTag("settings_auto_update"),
                    cardListPosition = CardListPosition.Middle,
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = acceptPrerelease,
                    onCheckedChange = container.settings::setAcceptPrerelease,
                    title = "接收预发布版本",
                    modifier = Modifier.testTag("settings_prerelease"),
                    cardListPosition = CardListPosition.Middle,
                )
                PreferenceDivider()
                SwitchPreference(
                    checked = inAppUpdate,
                    onCheckedChange = container.settings::setInAppUpdate,
                    title = "应用内更新",
                    modifier = Modifier.testTag("settings_in_app_update"),
                    cardListPosition = CardListPosition.Tail,
                )
            }
        }

        item(key = "about-title") { SectionTitle("关于") }

        item(key = "about") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                ArrowPreference(
                    title = "关于 BiliCO",
                    modifier = Modifier.testTag("settings_about"),
                    summary = "v${BuildConfig.VERSION_NAME}",
                    cardListPosition = CardListPosition.None,
                    onClick = { showAbout = true },
                )
            }
        }

        if (account != null) {
            item(key = "account-title") { SectionTitle("账号") }

            item(key = "account") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    ArrowPreference(
                        title = "退出登录",
                        modifier = Modifier.testTag("settings_logout"),
                        summary = account?.name,
                        cardListPosition = CardListPosition.None,
                        onClick = { confirmLogout = true },
                    )
                }
            }
        }
    }

    if (pickCdn) {
        OptionPicker(
            title = "播放线路",
            options = CdnOption.entries.map { it.label },
            selectedIndex = CdnOption.entries.indexOfFirst { it.id == cdnOption }
                .coerceAtLeast(0),
            onPick = { index ->
                container.settings.setCdnOption(CdnOption.entries[index].id)
                pickCdn = false
            },
            onDismiss = { pickCdn = false },
        )
    }

    if (showDanmakuSettings) {
        DanmakuSettingsDialog(
            settings = danmakuSettings,
            repository = container.settings,
            onDismiss = { showDanmakuSettings = false },
        )
    }

    if (showAbout) {
        ConfirmDialog(
            title = "关于 BiliCO",
            message = "BiliCO v${BuildConfig.VERSION_NAME}\n\n第三方哔哩哔哩客户端，仅用于个人学习与研究。",
            confirmText = "知道了",
            onDismiss = { showAbout = false },
            onConfirm = { showAbout = false },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            message = "会清除本地保存的 Cookie，下次需要重新扫码登录。",
            confirmText = "退出",
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                container.scope.launch { container.session.logout() }
            },
        )
    }
}

/** 单选弹窗：和 Cobo 的设置项同一套组件，胶囊高亮由 ListPopupColumn 自己铺。 */@Composable
private fun OptionPicker(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = title,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val picked = result
                if (picked != null) onPick(picked) else onDismiss()
            }
        },
    ) {
        Column {
            Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) {
                ListPopupColumn {
                    options.forEachIndexed { index, label ->
                        DropdownImpl(
                            item = DropdownItem(text = label),
                            optionSize = options.size,
                            isSelected = index == selectedIndex,
                            index = index,
                            groupKey = "settings-option",
                            onSelectedIndexChange = {
                                result = index
                                show = false
                            },
                        )
                    }
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = null,
            )
        }
    }
}

/** 下标即存储值：0 跟随系统，1 浅色，2 深色。 */
private val DARK_MODE_OPTIONS = listOf("跟随系统", "浅色", "深色")
private val QUALITY_LABELS = DefaultQuality.entries.map { it.label }
private val AUTO_ROTATE_OPTIONS = listOf("关闭", "迟钝 (防误触, ±15°)", "标准 (±25°)", "灵敏 (±35°)")
private val LONG_PRESS_SPEED_OPTIONS = listOf(150, 200, 250, 300, 400)
private val LONG_PRESS_SPEED_LABELS = LONG_PRESS_SPEED_OPTIONS.map { speedLabel(it / 100f) }
