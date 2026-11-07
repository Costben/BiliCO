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
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.di.AppContainer
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.ui.NavState
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
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
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val danmakuOpacity by container.settings.danmakuOpacity.collectAsStateWithLifecycle()
    val danmakuFontScale by container.settings.danmakuFontScale.collectAsStateWithLifecycle()
    val danmakuArea by container.settings.danmakuArea.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()

    var pickQuality by remember { mutableStateOf(false) }
    var pickAutoRotate by remember { mutableStateOf(false) }
    var pickOpacity by remember { mutableStateOf(false) }
    var pickFontScale by remember { mutableStateOf(false) }
    var pickArea by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableIntStateOf(0) }

    SecondaryPage(title = "设置", onBack = nav::pop) {
        item(key = "display-title") { SectionTitle("显示与播放") }

        item(key = "display") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "深色模式",
                    modifier = Modifier.testTag("settings_dark"),
                    summary = when (darkMode) {
                        1 -> "浅色"
                        2 -> "深色"
                        else -> "跟随系统"
                    },
                    cardListPosition = CardListPosition.Head,
                    onClick = { container.settings.setDarkMode((darkMode + 1) % 3) },
                )
                ArrowPreference(
                    title = "默认清晰度",
                    modifier = Modifier.testTag("settings_quality"),
                    summary = DefaultQuality.entries
                        .firstOrNull { it.id == quality }
                        ?.label
                        ?: "跟随接口",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { pickQuality = true },
                )
                ArrowPreference(
                    title = "后台播放",
                    modifier = Modifier.testTag("settings_bg_play"),
                    summary = if (backgroundPlay) "开" else "关",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { container.settings.setBackgroundPlay(!backgroundPlay) },
                )
                ArrowPreference(
                    title = "自动画中画",
                    modifier = Modifier.testTag("settings_auto_pip"),
                    summary = if (autoPip) "开" else "关",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { container.settings.setAutoPip(!autoPip) },
                )
                ArrowPreference(
                    title = "自动横屏",
                    modifier = Modifier.testTag("settings_auto_rotate"),
                    summary = when (autoRotateMode) {
                        0 -> "关闭"
                        1 -> "迟钝 (防误触)"
                        2 -> "标准"
                        3 -> "灵敏"
                        else -> "迟钝 (防误触)"
                    },
                    cardListPosition = CardListPosition.Middle,
                    onClick = { pickAutoRotate = true },
                )
                ArrowPreference(
                    title = "手势调节亮度与音量",
                    modifier = Modifier.testTag("settings_gesture_vol_bright"),
                    summary = if (gestureVolBright) "开 (双侧滑动)" else "关",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { container.settings.setGestureVolumeBrightness(!gestureVolBright) },
                )
                ArrowPreference(
                    title = "全屏切换提示",
                    modifier = Modifier.testTag("settings_fs_hint"),
                    summary = if (fullscreenGestureHint) "开 (触发后淡入淡出)" else "关",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { container.settings.setFullscreenGestureHint(!fullscreenGestureHint) },
                )
                ArrowPreference(
                    title = "手势震动",
                    modifier = Modifier.testTag("settings_gesture_haptic"),
                    summary = if (gestureHaptic) "开" else "关",
                    cardListPosition = CardListPosition.Tail,
                    onClick = { container.settings.setGestureHaptic(!gestureHaptic) },
                )
            }
        }

        item(key = "danmaku-title") { SectionTitle("弹幕") }

        item(key = "danmaku") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "弹幕",
                    modifier = Modifier.testTag("settings_danmaku"),
                    summary = if (danmakuEnabled) "开" else "关",
                    cardListPosition = CardListPosition.Head,
                    onClick = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                )
                ArrowPreference(
                    title = "弹幕详细设置",
                    modifier = Modifier.testTag("settings_danmaku_detail"),
                    summary = "类型屏蔽 · 速度 · 描边 · 字重 · 行距",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { showDanmakuSettings = true },
                )
                ArrowPreference(
                    title = "不透明度",
                    modifier = Modifier.testTag("settings_danmaku_opacity"),
                    summary = "$danmakuOpacity%",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { pickOpacity = true },
                )
                ArrowPreference(
                    title = "字号",
                    modifier = Modifier.testTag("settings_danmaku_font"),
                    summary = "$danmakuFontScale%",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { pickFontScale = true },
                )
                ArrowPreference(
                    title = "显示区域",
                    modifier = Modifier.testTag("settings_danmaku_area"),
                    summary = danmakuAreaLabel(danmakuArea),
                    cardListPosition = CardListPosition.Tail,
                    onClick = { pickArea = true },
                )
            }
        }

        item(key = "storage-title") { SectionTitle("存储") }

        item(key = "storage") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
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

        item(key = "about-title") { SectionTitle("关于") }

        item(key = "about") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
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
                    insideMargin = PaddingValues(0.dp),
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

    if (pickQuality) {
        OptionPicker(
            title = "默认清晰度",
            options = DefaultQuality.entries.map { it.label },
            selectedIndex = DefaultQuality.entries.indexOfFirst { it.id == quality }
                .coerceAtLeast(0),
            onPick = { index ->
                container.settings.setDefaultQuality(DefaultQuality.entries[index].id)
                pickQuality = false
            },
            onDismiss = { pickQuality = false },
        )
    }

    if (pickAutoRotate) {
        val rotateOptions = listOf("关闭", "迟钝 (防误触, ±15°)", "标准 (±25°)", "灵敏 (±35°)")
        OptionPicker(
            title = "自动横屏灵敏度",
            options = rotateOptions,
            selectedIndex = autoRotateMode.coerceIn(0, rotateOptions.lastIndex),
            onPick = { index ->
                container.settings.setAutoRotateMode(index)
                pickAutoRotate = false
            },
            onDismiss = { pickAutoRotate = false },
        )
    }

    if (pickOpacity) {
        OptionPicker(
            title = "弹幕不透明度",
            options = OPACITY_OPTIONS.map { "$it%" },
            selectedIndex = OPACITY_OPTIONS.indexOf(danmakuOpacity).coerceAtLeast(0),
            onPick = { index ->
                container.settings.setDanmakuOpacity(OPACITY_OPTIONS[index])
                pickOpacity = false
            },
            onDismiss = { pickOpacity = false },
        )
    }

    if (pickFontScale) {
        OptionPicker(
            title = "弹幕字号",
            options = FONT_SCALE_OPTIONS.map { "$it%" },
            selectedIndex = FONT_SCALE_OPTIONS.indexOf(danmakuFontScale).coerceAtLeast(0),
            onPick = { index ->
                container.settings.setDanmakuFontScale(FONT_SCALE_OPTIONS[index])
                pickFontScale = false
            },
            onDismiss = { pickFontScale = false },
        )
    }

    if (pickArea) {
        OptionPicker(
            title = "弹幕显示区域",
            options = AREA_OPTIONS.map(::danmakuAreaLabel),
            selectedIndex = danmakuArea.coerceIn(0, AREA_OPTIONS.lastIndex),
            onPick = { index ->
                container.settings.setDanmakuArea(index)
                pickArea = false
            },
            onDismiss = { pickArea = false },
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

private val OPACITY_OPTIONS = listOf(25, 50, 75, 100)
private val FONT_SCALE_OPTIONS = listOf(75, 100, 125, 150)
private val AREA_OPTIONS = listOf(0, 1, 2, 3)

private fun danmakuAreaLabel(area: Int): String = when (area) {
    0 -> "1/4 屏"
    1 -> "半屏"
    2 -> "3/4 屏"
    else -> "全屏"
}
