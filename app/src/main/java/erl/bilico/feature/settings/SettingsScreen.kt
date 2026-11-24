package erl.bilico.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import erl.bilico.BuildConfig
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.settings.CoverAspect
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.data.settings.FeedPreferences
import erl.bilico.data.settings.StatsOverlayPreferences
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.accentColorSummary
import erl.bilico.ui.components.PreferenceDivider
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    nav: NavState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val accentColor by container.settings.accentColor.collectAsStateWithLifecycle()
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
    val playerConicLight by container.settings.playerConicLight.collectAsStateWithLifecycle()
    val showVideoDislike by container.settings.showVideoDislike.collectAsStateWithLifecycle()
    val autoNextRecommendPopup by container.settings.autoNext.recommendPopup.collectAsStateWithLifecycle()
    val autoNextCountdownSec by container.settings.autoNext.countdownSeconds.collectAsStateWithLifecycle()
    val coverAspect by container.settings.feed.coverAspect.collectAsStateWithLifecycle()
    val feedColumns by container.settings.feed.columns.collectAsStateWithLifecycle()
    val feedRefreshCount by container.settings.feed.refreshCount.collectAsStateWithLifecycle()
    val homeTabsLayout by container.settings.feed.homeTabs.collectAsStateWithLifecycle()
    val topBarLayoutFullscreen by container.settings.playerTopBar.fullscreen.collectAsStateWithLifecycle()
    val topBarLayoutPortrait by container.settings.playerTopBar.portrait.collectAsStateWithLifecycle()
    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val advancedPlusOne by container.settings.advancedPlusOne.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by container.settings.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sponsorBadgeEnabled by container.settings.sponsorBadgeEnabled.collectAsStateWithLifecycle()
    val promotionBadgeEnabled by container.settings.promotionBadgeEnabled.collectAsStateWithLifecycle()
    val sponsorSkipMode by container.settings.sponsorSkipMode.collectAsStateWithLifecycle()
    val sponsorCategories by container.settings.sponsorCategories.collectAsStateWithLifecycle()
    val statsOverlayEnabled by container.settings.statsOverlay.enabled.collectAsStateWithLifecycle()
    val statsOverlayX by container.settings.statsOverlay.xFraction.collectAsStateWithLifecycle()
    val statsOverlayY by container.settings.statsOverlay.yFraction.collectAsStateWithLifecycle()
    val statsOverlayFields by container.settings.statsOverlay.visibleFields.collectAsStateWithLifecycle()
    val autoCheckUpdate by container.settings.autoCheckUpdate.collectAsStateWithLifecycle()
    val acceptPrerelease by container.settings.acceptPrerelease.collectAsStateWithLifecycle()
    val inAppUpdate by container.settings.inAppUpdate.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()
    val sessions by container.session.sessions.collectAsStateWithLifecycle()
    val downloadTasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val downloadBytes by container.downloads.totalCachedBytes.collectAsStateWithLifecycle()

    var pickAccentColor by remember { mutableStateOf(false) }
    var pickCdn by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showTopBarSettings by remember { mutableStateOf(false) }
    var showHomeTabsSettings by remember { mutableStateOf(false) }
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
                ArrowPreference(
                    title = "主题色",
                    summary = accentColorSummary(accentColor),
                    endActions = {
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(COUITheme.colorScheme.primary)
                                .border(1.dp, COUITheme.colorScheme.outline, CircleShape),
                        )
                    },
                    modifier = Modifier.testTag("settings_accent_color"),
                    cardListPosition = CardListPosition.Middle,
                    onClick = { pickAccentColor = true },
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
                OverlayDropdownPreference(
                    items = FEED_COLUMN_OPTIONS,
                    selectedIndex = (feedColumns - FeedPreferences.MIN_COLUMNS)
                        .coerceIn(0, FEED_COLUMN_OPTIONS.lastIndex),
                    title = "推荐列数",
                    summary = "推荐页面每行显示的视频列数",
                    modifier = Modifier.testTag("settings_feed_columns"),
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = { index ->
                        container.settings.feed.setColumns(FeedPreferences.MIN_COLUMNS + index)
                    },
                )
                OverlayDropdownPreference(
                    items = FEED_REFRESH_COUNT_LABELS,
                    selectedIndex = FeedPreferences.REFRESH_COUNT_OPTIONS
                        .indexOf(feedRefreshCount)
                        .let { if (it >= 0) it else DEFAULT_REFRESH_COUNT_INDEX },
                    title = "下拉刷新数量",
                    summary = "每次下拉刷新推荐流时拉取的视频数",
                    modifier = Modifier.testTag("settings_feed_refresh_count"),
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = { index ->
                        container.settings.feed.setRefreshCount(FeedPreferences.REFRESH_COUNT_OPTIONS[index])
                    },
                )
                ArrowPreference(
                    title = "首页分类",
                    summary = homeTabsLayout.visibleTabs().joinToString(" · ") { it.label },
                    modifier = Modifier.testTag("settings_home_tabs"),
                    cardListPosition = CardListPosition.Middle,
                    onClick = { showHomeTabsSettings = true },
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
                ArrowPreference(
                    title = "统计信息浮窗",
                    summary = StatsOverlayPreferences.summary(
                        enabled = statsOverlayEnabled,
                        xFraction = statsOverlayX,
                        yFraction = statsOverlayY,
                        visibleCount = statsOverlayFields.size,
                    ),
                    modifier = Modifier.testTag("settings_stats_overlay"),
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.StatsOverlaySettings) },
                )
                SwitchPreference(
                    checked = playerConicLight,
                    onCheckedChange = container.settings::setPlayerConicLight,
                    title = "进度条光感",
                    summary = "播放或拖动视频进度条时显示锥形光效",
                    modifier = Modifier.testTag("settings_player_conic_light"),
                    cardListPosition = CardListPosition.Middle,
                )
                SwitchPreference(
                    checked = showVideoDislike,
                    onCheckedChange = container.settings::setShowVideoDislike,
                    title = "显示点踩按钮",
                    summary = "在视频详情页操作栏显示点踩",
                    modifier = Modifier.testTag("settings_show_video_dislike"),
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

        item(key = "auto-next-title") { SectionTitle("自动连播") }

        item(key = "auto-next") {
            AutoNextSettingsCard(
                recommendPopup = autoNextRecommendPopup,
                countdownSeconds = autoNextCountdownSec,
                onRecommendPopupChange = container.settings.autoNext::setRecommendPopup,
                onCountdownSecondsChange = container.settings.autoNext::setCountdownSeconds,
            )
        }

        item(key = "sponsor-title") { SectionTitle("空降助手") }

        item(key = "sponsor") {
            SponsorSettingsCard(
                enabled = sponsorBlockEnabled,
                skipMode = sponsorSkipMode,
                skipModeOptions = SPONSOR_SKIP_MODES,
                selectedCategoryCount = sponsorCategories.size,
                sponsorBadgeEnabled = sponsorBadgeEnabled,
                promotionBadgeEnabled = promotionBadgeEnabled,
                onEnabledChange = container.settings::setSponsorBlockEnabled,
                onSkipModeChange = container.settings::setSponsorSkipMode,
                onOpenCategories = { showSponsorCategories = true },
                onSponsorBadgeChange = container.settings::setSponsorBadgeEnabled,
                onPromotionBadgeChange = container.settings::setPromotionBadgeEnabled,
            )
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
                val doneCount = remember(downloadTasks) { downloadTasks.count { it.isCompleted } }
                val activeCount = remember(downloadTasks) { downloadTasks.count { it.isActive } }
                ArrowPreference(
                    title = "离线缓存",
                    modifier = Modifier.testTag("settings_downloads"),
                    summary = when {
                        activeCount > 0 -> "$activeCount 个下载中 · 占用 ${Formatters.formatFileSize(downloadBytes)}"
                        doneCount > 0 -> "已缓存 $doneCount 个视频 · 占用 ${Formatters.formatFileSize(downloadBytes)}"
                        else -> "暂无缓存视频"
                    },
                    cardListPosition = CardListPosition.Head,
                    onClick = { nav.push(Route.Downloads) },
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "清除图片缓存",
                    modifier = Modifier.testTag("settings_cache"),
                    summary = if (cacheCleared > 0) "已清除" else "只清本地图片，不影响账号",
                    cardListPosition = CardListPosition.Tail,
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
            UpdateSettingsCard(
                checkingUpdate = container.updater.checkingUpdate,
                autoCheckUpdate = autoCheckUpdate,
                acceptPrerelease = acceptPrerelease,
                inAppUpdate = inAppUpdate,
                onCheckUpdate = { scope.launch { container.updater.checkUpdate(manual = true) } },
                onOpenChangelog = { nav.push(Route.Update) },
                onAutoCheckUpdateChange = container.settings::setAutoCheckUpdate,
                onAcceptPrereleaseChange = container.settings::setAcceptPrerelease,
                onInAppUpdateChange = container.settings::setInAppUpdate,
            )
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
                AccountSettingsCard(
                    account = account,
                    sessions = sessions,
                    onSwitchAccount = { switchAccounts = true },
                    onLogout = { confirmLogout = true },
                )
            }
        }
    }

    SettingsDialogsHost(
        container = container,
        nav = nav,
        darkMode = darkMode,
        accentColor = accentColor,
        cdnOption = cdnOption,
        danmakuSettings = danmakuSettings,
        topBarLayoutFullscreen = topBarLayoutFullscreen,
        topBarLayoutPortrait = topBarLayoutPortrait,
        homeTabsLayout = homeTabsLayout,
        sponsorCategories = sponsorCategories,
        account = account,
        sessions = sessions,
        pickAccentColor = pickAccentColor,
        pickCdn = pickCdn,
        showDanmakuSettings = showDanmakuSettings,
        showTopBarSettings = showTopBarSettings,
        showHomeTabsSettings = showHomeTabsSettings,
        showSponsorCategories = showSponsorCategories,
        switchAccounts = switchAccounts,
        confirmLogout = confirmLogout,
        onDismissAccentColor = { pickAccentColor = false },
        onDismissCdn = { pickCdn = false },
        onDismissDanmaku = { showDanmakuSettings = false },
        onDismissTopBar = { showTopBarSettings = false },
        onDismissHomeTabs = { showHomeTabsSettings = false },
        onDismissSponsorCategories = { showSponsorCategories = false },
        onDismissSwitchAccounts = { switchAccounts = false },
        onDismissLogout = { confirmLogout = false },
    )
}

/** 下标即存储值：0 跟随系统，1 浅色，2 深色。 */
private val DARK_MODE_OPTIONS = listOf("跟随系统", "浅色", "深色")
private val COVER_ASPECT_OPTIONS = CoverAspect.entries.map { it.label }
private val FEED_COLUMN_OPTIONS = (FeedPreferences.MIN_COLUMNS..FeedPreferences.MAX_COLUMNS).map { "${it} 列" }
private val FEED_REFRESH_COUNT_LABELS = FeedPreferences.REFRESH_COUNT_OPTIONS.map { "${it} 个" }
private val DEFAULT_REFRESH_COUNT_INDEX = FeedPreferences.REFRESH_COUNT_OPTIONS.indexOf(FeedPreferences.DEFAULT_REFRESH_COUNT).coerceAtLeast(0)
private val FEED_SOURCE_OPTIONS = listOf("App 端推荐", "网页端推荐")
private val AUTO_ROTATE_OPTIONS = listOf("关闭", "迟钝 (防误触, ±15°)", "标准 (±25°)", "灵敏 (±35°)")
private val LONG_PRESS_SPEED_OPTIONS = listOf(150, 200, 250, 300, 400)
private val SPONSOR_SKIP_MODES = listOf("自动跳过", "手动提示", "仅标记片段")
