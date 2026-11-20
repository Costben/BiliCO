package erl.bilico.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import erl.bilico.BuildConfig
import erl.bilico.data.model.SponsorCategory
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.settings.CoverAspect
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.di.AppContainer
import erl.bilico.feature.me.AccountSwitchDialog
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.feature.video.PlayerTopBarSettingsDialog
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
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    nav: NavState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val returnBounce by container.settings.returnBounce.collectAsStateWithLifecycle()
    val predictiveBackFollow by container.settings.predictiveBackFollow.collectAsStateWithLifecycle()
    val quality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val cdnOption by container.settings.cdnOption.collectAsStateWithLifecycle()
    val feedSource by container.settings.feedSource.collectAsStateWithLifecycle()
    val inAppBackgroundPlay by container.settings.inAppBackgroundPlay.collectAsStateWithLifecycle()
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val longPressSpeed by container.settings.longPressSpeed.collectAsStateWithLifecycle()
    val adjustableLongPressSpeed by container.settings.adjustableLongPressSpeed.collectAsStateWithLifecycle()
    val swipeDownLockLongPressSpeed by container.settings.swipeDownLockLongPressSpeed.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val coverAspect by container.settings.feed.coverAspect.collectAsStateWithLifecycle()
    val topBarLayoutFullscreen by container.settings.playerTopBar.fullscreen.collectAsStateWithLifecycle()
    val topBarLayoutPortrait by container.settings.playerTopBar.portrait.collectAsStateWithLifecycle()
    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val advancedPlusOne by container.settings.advancedPlusOne.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by container.settings.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sponsorSkipMode by container.settings.sponsorSkipMode.collectAsStateWithLifecycle()
    val sponsorCategories by container.settings.sponsorCategories.collectAsStateWithLifecycle()
    val autoCheckUpdate by container.settings.autoCheckUpdate.collectAsStateWithLifecycle()
    val acceptPrerelease by container.settings.acceptPrerelease.collectAsStateWithLifecycle()
    val inAppUpdate by container.settings.inAppUpdate.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()
    val sessions by container.session.sessions.collectAsStateWithLifecycle()

    var pickCdn by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showTopBarSettings by remember { mutableStateOf(false) }
    var showSponsorCategories by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var switchAccounts by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableIntStateOf(0) }

    SecondaryPage(title = "设置", onBack = nav::pop) {
        item(key = "display-title") { SectionTitle("显示与播放", topPadding = 4.dp) }

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
                    items = COVER_ASPECT_OPTIONS,
                    selectedIndex = coverAspect.code.coerceIn(0, COVER_ASPECT_OPTIONS.lastIndex),
                    title = "封面比例",
                    summary = "推荐流卡片的封面裁切比例",
                    modifier = Modifier.testTag("settings_cover_aspect"),
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = { index ->
                        container.settings.feed.setCoverAspect(CoverAspect.fromCode(index))
                    },
                )
                SwitchPreference(
                    checked = returnBounce,
                    onCheckedChange = container.settings::setReturnBounce,
                    title = "返回动画回弹",
                    summary = "退回卡片时保留轻压回弹效果",
                    modifier = Modifier.testTag("settings_return_bounce"),
                    cardListPosition = CardListPosition.Middle,
                )
                SwitchPreference(
                    checked = predictiveBackFollow,
                    onCheckedChange = container.settings::setPredictiveBackFollow,
                    title = "返回跟手轨迹",
                    summary = "侧滑返回时跟随手指偏移，缩回卡片或融合球时顺着甩出方向回缩",
                    modifier = Modifier.testTag("settings_predictive_back_follow"),
                    cardListPosition = CardListPosition.Middle,
                )
                val selectedQuality = DefaultQuality.fromId(quality)
                val qualityEntry = remember(selectedQuality) {
                    DropdownEntry(
                        DefaultQuality.entries.map { option ->
                            DropdownItem(
                                text = option.label,
                                summary = if (option.needVip) "大会员可用，不可用时自动降级" else null,
                                selected = option == selectedQuality,
                                onClick = { container.settings.setDefaultQuality(option.id) },
                            )
                        },
                    )
                }
                OverlayDropdownPreference(
                    entry = qualityEntry,
                    title = "默认清晰度",
                    summary = if (selectedQuality.needVip) {
                        "需要大会员，无权限或片源不支持时自动降到可用最高画质"
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("settings_quality"),
                    cardListPosition = CardListPosition.Middle,
                )
                ArrowPreference(
                    title = "播放线路",
                    modifier = Modifier.testTag("settings_cdn"),
                    summary = CdnOption.fromId(cdnOption).label,
                    cardListPosition = CardListPosition.Middle,
                    onClick = { pickCdn = true },
                )
                SwitchPreference(
                    checked = inAppBackgroundPlay,
                    onCheckedChange = container.settings::setInAppBackgroundPlay,
                    title = "应用内后台播放",
                    summary = "返回上一级时缩到底栏融合球继续播放",
                    modifier = Modifier.testTag("settings_in_app_bg_play"),
                    cardListPosition = CardListPosition.Middle,
                )
                SwitchPreference(
                    checked = backgroundPlay,
                    onCheckedChange = container.settings::setBackgroundPlay,
                    title = "应用外后台播放",
                    summary = "切到桌面或其他应用时继续播放",
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
                LongPressSpeedPreference(
                    speedOptions = LONG_PRESS_SPEED_OPTIONS,
                    selectedPercent = longPressSpeed,
                    adjustableSpeed = adjustableLongPressSpeed,
                    swipeDownLock = swipeDownLockLongPressSpeed,
                    onSelectPercent = container.settings::setLongPressSpeed,
                    onAdjustableChange = container.settings::setAdjustableLongPressSpeed,
                    onSwipeDownLockChange = container.settings::setSwipeDownLockLongPressSpeed,
                    modifier = Modifier.testTag("settings_long_press_speed"),
                    cardListPosition = CardListPosition.Middle,
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
                ArrowPreference(
                    title = "播放器顶栏",
                    summary = "设置全屏与竖屏时顶栏显示哪些按钮",
                    modifier = Modifier.testTag("settings_player_top_bar"),
                    cardListPosition = CardListPosition.Middle,
                    onClick = { showTopBarSettings = true },
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

        item(key = "sponsor-title") { SectionTitle("空降助手") }

        item(key = "sponsor") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                SwitchPreference(
                    checked = sponsorBlockEnabled,
                    onCheckedChange = container.settings::setSponsorBlockEnabled,
                    title = "空降助手",
                    summary = "跳过或标记视频内的赞助广告、三连与片头片尾",
                    modifier = Modifier.testTag("settings_sponsor_block"),
                    cardListPosition = CardListPosition.Head,
                )
                OverlayDropdownPreference(
                    items = SPONSOR_SKIP_MODES,
                    selectedIndex = sponsorSkipMode.coerceIn(0, SPONSOR_SKIP_MODES.lastIndex),
                    title = "空降方式",
                    enabled = sponsorBlockEnabled,
                    modifier = Modifier.testTag("settings_sponsor_mode"),
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = container.settings::setSponsorSkipMode,
                )
                ArrowPreference(
                    title = "跳过片段类型",
                    summary = "已启用 ${sponsorCategories.size} 类",
                    enabled = sponsorBlockEnabled,
                    modifier = Modifier.testTag("settings_sponsor_categories"),
                    cardListPosition = CardListPosition.Tail,
                    onClick = { showSponsorCategories = true },
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
                    cardListPosition = CardListPosition.Middle,
                    onClick = { showDanmakuSettings = true },
                )
                SwitchPreference(
                    checked = advancedPlusOne,
                    onCheckedChange = container.settings::setAdvancedPlusOne,
                    title = "高级+1",
                    summary = "开启后，直播间点击弹幕时在 +1 右侧显示 @+1",
                    modifier = Modifier.testTag("settings_danmaku_advanced_plus_one"),
                    cardListPosition = CardListPosition.Tail,
                )
            }
        }

        item(key = "block-title") { SectionTitle("内容过滤与屏蔽") }

        item(key = "block") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                OverlayDropdownPreference(
                    items = FEED_SOURCE_OPTIONS,
                    selectedIndex = feedSource.coerceIn(0, FEED_SOURCE_OPTIONS.lastIndex),
                    title = "推荐信息流来源",
                    summary = "切换首页推荐为 App 端或网页端；云端黑名单与「我不想看」偏好按当前账号同步",
                    modifier = Modifier.testTag("settings_feed_source"),
                    cardListPosition = CardListPosition.Head,
                    onSelectedIndexChange = container.settings::setFeedSource,
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "屏蔽管理",
                    summary = "关键词、UP 主、黑名单、视频与推荐过滤",
                    cardListPosition = CardListPosition.Tail,
                    onClick = { nav.push(Route.BlockManager) },
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
                    onClick = { nav.push(Route.About) },
                )
            }
        }

        if (account != null || sessions.isNotEmpty()) {
            item(key = "account-title") { SectionTitle("账号") }

            item(key = "account") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    // 名册里还有账号时留一个切换入口：设置页也能直接换号，不必绕回「我的」。
                    if (sessions.isNotEmpty()) {
                        ArrowPreference(
                            title = "切换账号",
                            modifier = Modifier.testTag("settings_switch_account"),
                            summary = "已保存 ${sessions.size} 个账号",
                            cardListPosition = CardListPosition.Head,
                            onClick = { switchAccounts = true },
                        )
                    }
                    if (account != null) {
                        ArrowPreference(
                            title = "退出登录",
                            modifier = Modifier.testTag("settings_logout"),
                            summary = account?.name,
                            cardListPosition = if (sessions.isNotEmpty()) {
                                CardListPosition.Tail
                            } else {
                                CardListPosition.None
                            },
                            onClick = { confirmLogout = true },
                        )
                    } else {
                        ArrowPreference(
                            title = "切换账号",
                            modifier = Modifier.testTag("settings_switch_account"),
                            summary = "已保存 ${sessions.size} 个账号",
                            cardListPosition = CardListPosition.None,
                            onClick = { switchAccounts = true },
                        )
                    }
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

    if (showTopBarSettings) {
        PlayerTopBarSettingsDialog(
            fullscreen = topBarLayoutFullscreen,
            portrait = topBarLayoutPortrait,
            onToggle = container.settings.playerTopBar::setVisible,
            onMove = container.settings.playerTopBar::moveItem,
            onDismiss = { showTopBarSettings = false },
        )
    }

    if (showSponsorCategories) {
        SponsorCategoriesDialog(
            enabledIds = sponsorCategories,
            onToggle = container.settings::setSponsorCategoryEnabled,
            onDismiss = { showSponsorCategories = false },
        )
    }

    if (switchAccounts) {
        AccountSwitchDialog(
            sessions = sessions,
            activeMid = account?.mid,
            onDismiss = { switchAccounts = false },
            onSwitch = { mid ->
                switchAccounts = false
                container.scope.launch { container.session.switchAccount(mid) }
            },
            onRemove = { mid ->
                container.scope.launch { container.session.removeAccount(mid) }
            },
            onAdd = {
                switchAccounts = false
                nav.push(Route.Login)
            },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            message = "只退出当前账号。还有别的已保存账号时会自动换过去，没有的话需要重新扫码登录。",
            confirmText = "退出",
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                container.scope.launch { container.session.logout() }
            },
        )
    }
}

/** 下标即存储值：0 跟随系统，1 浅色，2 深色。 */
private val DARK_MODE_OPTIONS = listOf("跟随系统", "浅色", "深色")
private val COVER_ASPECT_OPTIONS = CoverAspect.entries.map { it.label }
private val FEED_SOURCE_OPTIONS = listOf("App 端推荐", "网页端推荐")
private val AUTO_ROTATE_OPTIONS = listOf("关闭", "迟钝 (防误触, ±15°)", "标准 (±25°)", "灵敏 (±35°)")
private val LONG_PRESS_SPEED_OPTIONS = listOf(150, 200, 250, 300, 400)
private val SPONSOR_SKIP_MODES = listOf("自动跳过", "手动提示", "仅标记片段")
