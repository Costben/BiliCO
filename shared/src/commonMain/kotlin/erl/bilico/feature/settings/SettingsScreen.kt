package erl.bilico.feature.settings

import erl.bilico.platform.PlatformBackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import erl.bilico.platform.currentAppVersionName
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    nav: NavState,
) {
    val context = LocalPlatformContext.current
    val coilContext = coil3.compose.LocalPlatformContext.current
    val appVersionName = remember(context) { currentAppVersionName(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val accentColor by container.settings.accentColor.collectAsStateWithLifecycle()
    val accentHyperlink by container.settings.accentHyperlink.collectAsStateWithLifecycle()
    val meIconColorMode by container.settings.meIconColorMode.collectAsStateWithLifecycle()
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
    val searchHotGrid by container.settings.searchHotGrid.collectAsStateWithLifecycle()
    val searchResultGrid by container.settings.searchResultGrid.collectAsStateWithLifecycle()
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

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var pickAccentColor by remember { mutableStateOf(false) }
    var pickCdn by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showTopBarSettings by remember { mutableStateOf(false) }
    var showHomeTabsSettings by remember { mutableStateOf(false) }
    var showSponsorCategories by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var switchAccounts by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableIntStateOf(0) }

    val trimmedQuery = searchQuery.trim()
    val searchTerms = remember(trimmedQuery) { parseSearchTerms(trimmedQuery) }

    LaunchedEffect(trimmedQuery) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    PlatformBackHandler(enabled = searchExpanded) {
        searchQuery = ""
        searchExpanded = false
    }

    val doneCount = remember(downloadTasks) { downloadTasks.count { it.isCompleted } }
    val activeCount = remember(downloadTasks) { downloadTasks.count { it.isActive } }
    val downloadSummary = when {
        activeCount > 0 -> "$activeCount 个下载中 · 占用 ${Formatters.formatFileSize(downloadBytes)}"
        doneCount > 0 -> "已缓存 $doneCount 个视频 · 占用 ${Formatters.formatFileSize(downloadBytes)}"
        else -> "暂无缓存视频"
    }
    val homeTabsSummary = homeTabsLayout.visibleTabs().joinToString(" · ") { it.label }
    val cacheSummary = if (cacheCleared > 0) "已清除" else "只清本地图片，不影响账号"

    val groups = buildList {
        add(
            SettingsGroupSection(
                key = "display",
                title = "显示",
                entries = displaySettingsEntries(
                    darkMode = darkMode,
                    accentColor = accentColor,
                    accentHyperlink = accentHyperlink,
                    meIconColorMode = meIconColorMode,
                    coverAspect = coverAspect,
                    feedColumns = feedColumns,
                    feedRefreshCount = feedRefreshCount,
                    searchHotGrid = searchHotGrid,
                    searchResultGrid = searchResultGrid,
                    homeTabsSummary = homeTabsSummary,
                    showVideoDislike = showVideoDislike,
                    returnBounce = returnBounce,
                    predictiveBackFollow = predictiveBackFollow,
                    onDarkModeChange = container.settings::setDarkMode,
                    onPickAccentColor = { pickAccentColor = true },
                    onAccentHyperlinkChange = container.settings::setAccentHyperlink,
                    onMeIconColorModeChange = container.settings::setMeIconColorMode,
                    onCoverAspectChange = container.settings.feed::setCoverAspect,
                    onFeedColumnsChange = container.settings.feed::setColumns,
                    onFeedRefreshCountChange = container.settings.feed::setRefreshCount,
                    onSearchHotGridChange = container.settings::setSearchHotGrid,
                    onSearchResultGridChange = container.settings::setSearchResultGrid,
                    onOpenHomeTabs = { showHomeTabsSettings = true },
                    onShowVideoDislikeChange = container.settings::setShowVideoDislike,
                    onReturnBounceChange = container.settings::setReturnBounce,
                    onPredictiveBackFollowChange = container.settings::setPredictiveBackFollow,
                ),
            ),
        )
        add(
            SettingsGroupSection(
                key = "player",
                title = "播放器",
                entries = playerSettingsEntries(
                    quality = quality,
                    cdnOption = cdnOption,
                    inAppBackgroundPlay = inAppBackgroundPlay,
                    backgroundPlay = backgroundPlay,
                    autoPip = autoPip,
                    autoRotateMode = autoRotateMode,
                    longPressSpeed = longPressSpeed,
                    adjustableLongPressSpeed = adjustableLongPressSpeed,
                    swipeDownLockLongPressSpeed = swipeDownLockLongPressSpeed,
                    gestureVolBright = gestureVolBright,
                    fullscreenGestureHint = fullscreenGestureHint,
                    gestureHaptic = gestureHaptic,
                    playerConicLight = playerConicLight,
                    statsOverlayEnabled = statsOverlayEnabled,
                    statsOverlayX = statsOverlayX,
                    statsOverlayY = statsOverlayY,
                    statsOverlayVisibleCount = statsOverlayFields.size,
                    autoNextRecommendPopup = autoNextRecommendPopup,
                    autoNextCountdownSeconds = autoNextCountdownSec,
                    onQualityChange = container.settings::setDefaultQuality,
                    onPickCdn = { pickCdn = true },
                    onInAppBackgroundPlayChange = container.settings::setInAppBackgroundPlay,
                    onBackgroundPlayChange = container.settings::setBackgroundPlay,
                    onAutoPipChange = container.settings::setAutoPip,
                    onAutoRotateModeChange = container.settings::setAutoRotateMode,
                    onLongPressSpeedChange = container.settings::setLongPressSpeed,
                    onAdjustableLongPressSpeedChange = container.settings::setAdjustableLongPressSpeed,
                    onSwipeDownLockLongPressSpeedChange = container.settings::setSwipeDownLockLongPressSpeed,
                    onGestureVolBrightChange = container.settings::setGestureVolumeBrightness,
                    onFullscreenGestureHintChange = container.settings::setFullscreenGestureHint,
                    onGestureHapticChange = container.settings::setGestureHaptic,
                    onOpenTopBarSettings = { showTopBarSettings = true },
                    onOpenStatsOverlaySettings = { nav.push(Route.StatsOverlaySettings) },
                    onPlayerConicLightChange = container.settings::setPlayerConicLight,
                    onAutoNextRecommendPopupChange = container.settings.autoNext::setRecommendPopup,
                    onAutoNextCountdownSecondsChange = container.settings.autoNext::setCountdownSeconds,
                ),
            ),
        )
        add(
            SettingsGroupSection(
                key = "sponsor",
                title = "空降助手",
                entries = sponsorSettingsEntries(
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
                ),
            ),
        )
        add(
            SettingsGroupSection(
                key = "danmaku",
                title = "弹幕",
                entries = listOf(
                    SettingsEntry(
                        id = "danmaku",
                        title = "弹幕",
                        keywords = "开关 显示 隐藏",
                    ) { pos ->
                        SwitchPreference(
                            checked = danmakuEnabled,
                            onCheckedChange = container.settings::setDanmakuEnabled,
                            title = "弹幕",
                            modifier = Modifier.testTag("settings_danmaku"),
                            cardListPosition = pos,
                        )
                    },
                    SettingsEntry(
                        id = "danmaku_detail",
                        title = "弹幕设置",
                        summary = "不透明度 · 字号 · 显示区域 · 类型屏蔽 · 速度",
                        keywords = "描边 字体 行高 合并 重复 彩色",
                    ) { pos ->
                        ArrowPreference(
                            title = "弹幕设置",
                            modifier = Modifier.testTag("settings_danmaku_detail"),
                            summary = "不透明度 · 字号 · 显示区域 · 类型屏蔽 · 速度",
                            cardListPosition = pos,
                            onClick = { showDanmakuSettings = true },
                        )
                    },
                    SettingsEntry(
                        id = "danmaku_advanced_plus_one",
                        title = "高级+1",
                        summary = "开启后，直播间点击弹幕时在 +1 右侧显示 @+1",
                        keywords = "直播 粉丝牌 +1",
                    ) { pos ->
                        SwitchPreference(
                            checked = advancedPlusOne,
                            onCheckedChange = container.settings::setAdvancedPlusOne,
                            title = "高级+1",
                            summary = "开启后，直播间点击弹幕时在 +1 右侧显示 @+1",
                            modifier = Modifier.testTag("settings_danmaku_advanced_plus_one"),
                            cardListPosition = pos,
                        )
                    },
                ),
            ),
        )
        add(
            SettingsGroupSection(
                key = "block",
                title = "内容过滤与屏蔽",
                showDividers = true,
                entries = listOf(
                    SettingsEntry(
                        id = "feed_source",
                        title = "推荐信息流来源",
                        summary = "切换首页推荐为 App 端或网页端；云端黑名单与「我不想看」偏好按当前账号同步",
                        keywords = "App 网页 推荐 接口",
                    ) { pos ->
                        OverlayDropdownPreference(
                            items = FEED_SOURCE_OPTIONS,
                            selectedIndex = feedSource.coerceIn(0, FEED_SOURCE_OPTIONS.lastIndex),
                            title = "推荐信息流来源",
                            summary = "切换首页推荐为 App 端或网页端；云端黑名单与「我不想看」偏好按当前账号同步",
                            modifier = Modifier.testTag("settings_feed_source"),
                            cardListPosition = pos,
                            onSelectedIndexChange = container.settings::setFeedSource,
                        )
                    },
                    SettingsEntry(
                        id = "block_manager",
                        title = "屏蔽管理",
                        summary = "关键词、UP 主、黑名单、视频与推荐过滤",
                        keywords = "正则 时长 播放量 点赞率 过滤",
                    ) { pos ->
                        ArrowPreference(
                            title = "屏蔽管理",
                            summary = "关键词、UP 主、黑名单、视频与推荐过滤",
                            cardListPosition = pos,
                            onClick = { nav.push(Route.BlockManager) },
                        )
                    },
                ),
            ),
        )
        add(
            SettingsGroupSection(
                key = "storage",
                title = "存储",
                showDividers = true,
                entries = listOf(
                    SettingsEntry(
                        id = "downloads",
                        title = "离线缓存",
                        summary = downloadSummary,
                        keywords = "下载 视频 空间 占用",
                    ) { pos ->
                        ArrowPreference(
                            title = "离线缓存",
                            modifier = Modifier.testTag("settings_downloads"),
                            summary = downloadSummary,
                            cardListPosition = pos,
                            onClick = { nav.push(Route.Downloads) },
                        )
                    },
                    SettingsEntry(
                        id = "cache",
                        title = "清除图片缓存",
                        summary = cacheSummary,
                        keywords = "清理 缓存 图片 磁盘 cache clear storage",
                    ) { pos ->
                        ArrowPreference(
                            title = "清除图片缓存",
                            modifier = Modifier.testTag("settings_cache"),
                            summary = cacheSummary,
                            cardListPosition = pos,
                            onClick = {
                                scope.launch {
                                    val loader = SingletonImageLoader.get(coilContext)
                                    loader.diskCache?.clear()
                                    loader.memoryCache?.clear()
                                    cacheCleared++
                                }
                            },
                        )
                    },
                ),
            ),
        )
        add(
            SettingsGroupSection(
                key = "update",
                title = "更新",
                showDividers = true,
                entries = updateSettingsEntries(
                    versionName = appVersionName,
                    checkingUpdate = container.updater.checkingUpdate,
                    autoCheckUpdate = autoCheckUpdate,
                    acceptPrerelease = acceptPrerelease,
                    inAppUpdate = inAppUpdate,
                    onCheckUpdate = { scope.launch { container.updater.checkUpdate(manual = true) } },
                    onOpenChangelog = { nav.push(Route.Update) },
                    onAutoCheckUpdateChange = container.settings::setAutoCheckUpdate,
                    onAcceptPrereleaseChange = container.settings::setAcceptPrerelease,
                    onInAppUpdateChange = container.settings::setInAppUpdate,
                ),
            ),
        )
        add(
            SettingsGroupSection(
                key = "about",
                title = "关于",
                entries = listOf(
                    SettingsEntry(
                        id = "about",
                        title = "关于 BiliCO",
                        summary = "v$appVersionName",
                        keywords = "开源 仓库 致谢 版本",
                    ) { pos ->
                        ArrowPreference(
                            title = "关于 BiliCO",
                            modifier = Modifier.testTag("settings_about"),
                            summary = "v$appVersionName",
                            cardListPosition = pos,
                            onClick = { nav.push(Route.About) },
                        )
                    },
                ),
            ),
        )
        val accountEntries = accountSettingsEntries(
            account = account,
            sessions = sessions,
            onSwitchAccount = { switchAccounts = true },
            onLogout = { confirmLogout = true },
        )
        if (accountEntries.isNotEmpty()) {
            add(
                SettingsGroupSection(
                    key = "account",
                    title = "账号",
                    entries = accountEntries,
                ),
            )
        }
    }

    val filteredGroups = if (searchTerms.isEmpty()) {
        groups
    } else {
        groups.mapNotNull { group ->
            val scored = group.entries
                .mapNotNull { entry ->
                    val score = entry.matchScore(group.title, searchTerms)
                    if (score > 0) entry to score else null
                }
                .sortedByDescending { it.second }
            if (scored.isEmpty()) {
                null
            } else {
                group.copy(entries = scored.map { it.first }) to scored.first().second
            }
        }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    SecondaryPage(
        title = "设置",
        onBack = nav::pop,
        listState = listState,
        topBar = {
            SettingsSearchTopBar(
                query = searchQuery,
                expanded = searchExpanded,
                onQueryChange = { searchQuery = it },
                onExpandedChange = { searchExpanded = it },
                onBack = nav::pop,
                listState = listState,
            )
        },
    ) {
        if (filteredGroups.isEmpty()) {
            item(key = "search-empty") {
                EmptyState("未找到与「$trimmedQuery」相关的设置")
            }
        } else {
            filteredGroups.forEachIndexed { index, group ->
                item(key = "${group.key}-title") {
                    SectionTitle(
                        text = group.title,
                        topPadding = if (index == 0) 4.dp else 14.dp,
                    )
                }
                item(key = group.key) {
                    SettingsGroupCard(
                        entries = group.entries,
                        showDividers = group.showDividers,
                    )
                }
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

private val FEED_SOURCE_OPTIONS = listOf("App 端推荐", "网页端推荐")
private val SPONSOR_SKIP_MODES = listOf("自动跳过", "手动提示", "仅标记片段")
