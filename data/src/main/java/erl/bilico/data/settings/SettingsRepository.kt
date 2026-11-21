package erl.bilico.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.model.SponsorCategory
import erl.bilico.data.repository.CdnOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 本地设置：深色模式、默认清晰度、弹幕设置、播放手势、应用更新与搜索历史。
 * 值是 StateFlow，Compose 直接 collect；写入走 DataStore，进程重启后保留。
 */
class SettingsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _darkMode = MutableStateFlow(0)
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    private val _defaultQuality = MutableStateFlow(DefaultQuality.P1080.id)
    val defaultQuality: StateFlow<Int> = _defaultQuality.asStateFlow()

    private val _cdnOption = MutableStateFlow(CdnOption.Auto.id)
    val cdnOption: StateFlow<String> = _cdnOption.asStateFlow()

    /** 首页推荐流来源：0 App 端推荐，1 网页端推荐。 */
    private val _feedSource = MutableStateFlow(0)
    val feedSource: StateFlow<Int> = _feedSource.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _danmakuSettings = MutableStateFlow(DanmakuSettings())
    val danmakuSettings: StateFlow<DanmakuSettings> = _danmakuSettings.asStateFlow()

    private val _danmakuEnabled = MutableStateFlow(true)
    val danmakuEnabled: StateFlow<Boolean> = _danmakuEnabled.asStateFlow()

    private val _danmakuOpacity = MutableStateFlow(100)
    val danmakuOpacity: StateFlow<Int> = _danmakuOpacity.asStateFlow()

    private val _danmakuFontScale = MutableStateFlow(100)
    val danmakuFontScale: StateFlow<Int> = _danmakuFontScale.asStateFlow()

    private val _danmakuArea = MutableStateFlow(1)
    val danmakuArea: StateFlow<Int> = _danmakuArea.asStateFlow()

    /** 直播间点击弹幕 +1 时是否附带发送者的粉丝牌。 */
    private val _advancedPlusOne = MutableStateFlow(false)
    val advancedPlusOne: StateFlow<Boolean> = _advancedPlusOne.asStateFlow()

    private val _inAppBackgroundPlay = MutableStateFlow(true)
    val inAppBackgroundPlay: StateFlow<Boolean> = _inAppBackgroundPlay.asStateFlow()

    private val _backgroundPlay = MutableStateFlow(true)
    val backgroundPlay: StateFlow<Boolean> = _backgroundPlay.asStateFlow()

    /** 一个视频播完是否自动接着播下一个（下一 P 或播放队列里的下一条）。 */
    private val _autoPlayNext = MutableStateFlow(true)
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    /** 播完后的连播/循环策略。 */
    private val _playbackEndMode = MutableStateFlow(PlaybackEndMode.AUTO_NEXT)
    val playbackEndMode: StateFlow<PlaybackEndMode> = _playbackEndMode.asStateFlow()

    private val _autoPip = MutableStateFlow(true)
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()

    private val _gestureHaptic = MutableStateFlow(true)
    val gestureHaptic: StateFlow<Boolean> = _gestureHaptic.asStateFlow()

    /** 卡片展开后退回列表时是否保留轻压回弹效果。 */
    private val _returnBounce = MutableStateFlow(true)
    val returnBounce: StateFlow<Boolean> = _returnBounce.asStateFlow()

    /** 详情页预测性返回时是否跟随手指偏移，并顺着甩出方向回缩。 */
    private val _predictiveBackFollow = MutableStateFlow(true)
    val predictiveBackFollow: StateFlow<Boolean> = _predictiveBackFollow.asStateFlow()

    /** 长按倍速的倍数，按百分比存：200 = 2x。 */
    private val _longPressSpeed = MutableStateFlow(DEFAULT_LONG_PRESS_SPEED)
    val longPressSpeed: StateFlow<Int> = _longPressSpeed.asStateFlow()

    /** 长按倍速时是否允许上下滑动调节倍速（步进 0.25x），与下滑锁定互斥。 */
    private val _adjustableLongPressSpeed = MutableStateFlow(false)
    val adjustableLongPressSpeed: StateFlow<Boolean> = _adjustableLongPressSpeed.asStateFlow()

    /** 长按倍速时是否允许向下滑动锁定当前倍速，与可调变速互斥。 */
    private val _swipeDownLockLongPressSpeed = MutableStateFlow(false)
    val swipeDownLockLongPressSpeed: StateFlow<Boolean> = _swipeDownLockLongPressSpeed.asStateFlow()

    /** 是否锁定播放倍速：开启后切视频、切分 P 或切集时自动维持锁定的倍速。 */
    private val _speedLocked = MutableStateFlow(false)
    val speedLocked: StateFlow<Boolean> = _speedLocked.asStateFlow()

    private val _lockedSpeed = MutableStateFlow(1f)
    val lockedSpeed: StateFlow<Float> = _lockedSpeed.asStateFlow()

    private val _autoRotateMode = MutableStateFlow(1)
    val autoRotateMode: StateFlow<Int> = _autoRotateMode.asStateFlow()

    private val _gestureVolumeBrightness = MutableStateFlow(true)
    val gestureVolumeBrightness: StateFlow<Boolean> = _gestureVolumeBrightness.asStateFlow()

    private val _fullscreenGestureHint = MutableStateFlow(true)
    val fullscreenGestureHint: StateFlow<Boolean> = _fullscreenGestureHint.asStateFlow()

    /** 视频解码策略：0 = 自动（系统默认+异常回退），1 = 硬解优先，2 = 软解优先。 */
    private val _decoderMode = MutableStateFlow(0)
    val decoderMode: StateFlow<Int> = _decoderMode.asStateFlow()

    /** 省电渲染：普通 SDR 视频优先使用 SurfaceView（复用替身帧转场，减少 GPU 纹理合成开销）。 */
    private val _preferSurfaceView = MutableStateFlow(false)
    val preferSurfaceView: StateFlow<Boolean> = _preferSurfaceView.asStateFlow()

    /** 播放器顶栏的显示配置，全屏与竖屏各一份。 */
    val playerTopBar = PlayerTopBarPreferences(context, scope)

    /** 信息流外观（封面比例等）。 */
    val feed = FeedPreferences(context, scope)

    /** 空降助手（SponsorBlock）总开关与跳过模式：0 自动跳过，1 手动跳过，2 仅进度条标记。 */
    private val _sponsorBlockEnabled = MutableStateFlow(true)
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _sponsorSkipMode = MutableStateFlow(0)
    val sponsorSkipMode: StateFlow<Int> = _sponsorSkipMode.asStateFlow()

    private val _sponsorCategories = MutableStateFlow(SponsorCategory.DEFAULT_IDS)
    val sponsorCategories: StateFlow<Set<String>> = _sponsorCategories.asStateFlow()

    @Volatile
    private var sponsorUserIdCache: String = ""

    private val _autoCheckUpdate = MutableStateFlow(true)
    val autoCheckUpdate: StateFlow<Boolean> = _autoCheckUpdate.asStateFlow()

    private val _acceptPrerelease = MutableStateFlow(true)
    val acceptPrerelease: StateFlow<Boolean> = _acceptPrerelease.asStateFlow()

    private val _inAppUpdate = MutableStateFlow(true)
    val inAppUpdate: StateFlow<Boolean> = _inAppUpdate.asStateFlow()

    private val _updateCheckedAt = MutableStateFlow(0L)
    val updateCheckedAt: StateFlow<Long> = _updateCheckedAt.asStateFlow()

    private val _skippedUpdateVersion = MutableStateFlow("")
    val skippedUpdateVersion: StateFlow<String> = _skippedUpdateVersion.asStateFlow()

    init {
        scope.launch {
            val prefs = context.biliDataStore.data.first()
            _darkMode.value = prefs[KEY_DARK_MODE] ?: 0
            _defaultQuality.value = DefaultQuality.fromId(prefs[KEY_QUALITY] ?: DefaultQuality.P1080.id).id
            _cdnOption.value = CdnOption.fromId(prefs[KEY_CDN]).id
            _feedSource.value = (prefs[KEY_FEED_SOURCE] ?: 0).coerceIn(0, 1)
            _searchHistory.value = prefs[KEY_SEARCH_HISTORY]
                ?.split('\u0001')
                ?.filter { it.isNotBlank() }
                ?.take(HISTORY_LIMIT)
                .orEmpty()
            val dm = DanmakuSettings(
                enabled = prefs[KEY_DANMAKU_ENABLED] ?: true,
                allowScroll = prefs[KEY_DM_ALLOW_SCROLL] ?: true,
                allowTop = prefs[KEY_DM_ALLOW_TOP] ?: true,
                allowBottom = prefs[KEY_DM_ALLOW_BOTTOM] ?: true,
                allowColorful = prefs[KEY_DM_ALLOW_COLORFUL] ?: true,
                allowSpecial = prefs[KEY_DM_ALLOW_SPECIAL] ?: true,
                mergeDuplicates = prefs[KEY_DM_MERGE_DUP] ?: true,
                massiveMode = prefs[KEY_DM_MASSIVE] ?: false,
                area = (prefs[KEY_DANMAKU_AREA] ?: 1).coerceIn(0, 3),
                opacity = (prefs[KEY_DANMAKU_OPACITY] ?: 100).coerceIn(10, 100),
                fontScale = (prefs[KEY_DANMAKU_FONT] ?: 100).coerceIn(50, 200),
                speed = (prefs[KEY_DM_SPEED] ?: 100).coerceIn(50, 200),
                strokeWidth = (prefs[KEY_DM_STROKE] ?: 16).coerceIn(0, 30),
                fontWeight = (prefs[KEY_DM_WEIGHT] ?: 6).coerceIn(1, 9),
                lineHeight = (prefs[KEY_DM_LINE_HEIGHT] ?: 135).coerceIn(100, 200),
                weightFilterLevel = (prefs[KEY_DM_WEIGHT_FILTER] ?: 0).coerceIn(0, 10),
                cap60Fps = prefs[KEY_DM_CAP_60FPS] ?: false,
            )
            syncDanmakuState(dm)
            _advancedPlusOne.value = prefs[KEY_ADVANCED_PLUS_ONE] ?: false
            _inAppBackgroundPlay.value = prefs[KEY_IN_APP_BACKGROUND_PLAY] ?: true
            _backgroundPlay.value = prefs[KEY_BACKGROUND_PLAY] ?: true
            val legacyAutoPlay = prefs[KEY_AUTO_PLAY_NEXT] ?: true
            val resolvedEndMode = prefs[KEY_PLAYBACK_END_MODE]
                ?.let(PlaybackEndMode::fromCode)
                ?: if (legacyAutoPlay) PlaybackEndMode.AUTO_NEXT else PlaybackEndMode.OFF
            _playbackEndMode.value = resolvedEndMode
            _autoPlayNext.value = resolvedEndMode == PlaybackEndMode.AUTO_NEXT
            _autoPip.value = prefs[KEY_AUTO_PIP] ?: true
            _gestureHaptic.value = prefs[KEY_GESTURE_HAPTIC] ?: true
            _returnBounce.value = prefs[KEY_RETURN_BOUNCE] ?: true
            _predictiveBackFollow.value = prefs[KEY_PREDICTIVE_BACK_FOLLOW] ?: true
            _longPressSpeed.value = (prefs[KEY_LONG_PRESS_SPEED] ?: DEFAULT_LONG_PRESS_SPEED)
                .coerceIn(125, 400)
            val adjustable = prefs[KEY_ADJUSTABLE_LONG_PRESS_SPEED] ?: false
            val swipeDownLock = if (adjustable) false else (prefs[KEY_SWIPE_DOWN_LOCK_LONG_PRESS_SPEED] ?: false)
            _adjustableLongPressSpeed.value = adjustable
            _swipeDownLockLongPressSpeed.value = swipeDownLock
            _speedLocked.value = prefs[KEY_SPEED_LOCKED] ?: false
            _lockedSpeed.value = (prefs[KEY_LOCKED_SPEED] ?: 1f).coerceIn(0.25f, 4f)
            _autoRotateMode.value = prefs[KEY_AUTO_ROTATE_MODE] ?: 1
            _gestureVolumeBrightness.value = prefs[KEY_GESTURE_VOL_BRIGHT] ?: true
            _fullscreenGestureHint.value = prefs[KEY_FS_GESTURE_HINT] ?: true
            _decoderMode.value = (prefs[KEY_DECODER_MODE] ?: 0).coerceIn(0, 2)
            _preferSurfaceView.value = prefs[KEY_PREFER_SURFACE_VIEW] ?: false
            playerTopBar.restore()
            feed.restore()
            _sponsorBlockEnabled.value = prefs[KEY_SPONSOR_BLOCK_ENABLED] ?: true
            _sponsorSkipMode.value = (prefs[KEY_SPONSOR_SKIP_MODE] ?: 0).coerceIn(0, 2)
            _sponsorCategories.value = prefs[KEY_SPONSOR_CATEGORIES]
                ?.split(',')
                ?.filter { SponsorCategory.fromId(it) != null }
                ?.toSet()
                ?: SponsorCategory.DEFAULT_IDS
            sponsorUserIdCache = prefs[KEY_SPONSOR_USER_ID]?.takeIf { it.length >= 30 }.orEmpty()
            _autoCheckUpdate.value = prefs[KEY_AUTO_CHECK_UPDATE] ?: true
            _acceptPrerelease.value = prefs[KEY_ACCEPT_PRERELEASE] ?: true
            _inAppUpdate.value = prefs[KEY_IN_APP_UPDATE] ?: true
            _updateCheckedAt.value = prefs[KEY_UPDATE_CHECKED_AT] ?: 0L
            _skippedUpdateVersion.value = prefs[KEY_SKIPPED_UPDATE] ?: ""
            _loaded.value = true
        }
    }

    private fun syncDanmakuState(dm: DanmakuSettings) {
        _danmakuSettings.value = dm
        _danmakuEnabled.value = dm.enabled
        _danmakuOpacity.value = dm.opacity
        _danmakuFontScale.value = dm.fontScale
        _danmakuArea.value = dm.area
    }

    fun setAutoRotateMode(mode: Int) {
        _autoRotateMode.value = mode.coerceIn(0, 3)
        scope.launch { context.biliDataStore.edit { it[KEY_AUTO_ROTATE_MODE] = _autoRotateMode.value } }
    }

    fun setGestureVolumeBrightness(enabled: Boolean) {
        _gestureVolumeBrightness.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_GESTURE_VOL_BRIGHT] = enabled } }
    }

    fun setFullscreenGestureHint(enabled: Boolean) {
        _fullscreenGestureHint.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_FS_GESTURE_HINT] = enabled } }
    }

    fun setSponsorBlockEnabled(enabled: Boolean) {
        _sponsorBlockEnabled.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_SPONSOR_BLOCK_ENABLED] = enabled } }
    }

    fun setSponsorSkipMode(mode: Int) {
        _sponsorSkipMode.value = mode.coerceIn(0, 2)
        scope.launch { context.biliDataStore.edit { it[KEY_SPONSOR_SKIP_MODE] = _sponsorSkipMode.value } }
    }

    fun ensureSponsorUserId(): String {
        val existing = sponsorUserIdCache
        if (existing.length >= 30) return existing
        val generated = java.util.UUID.randomUUID().toString().replace("-", "")
        sponsorUserIdCache = generated
        scope.launch {
            context.biliDataStore.edit { prefs ->
                val persisted = prefs[KEY_SPONSOR_USER_ID].orEmpty()
                if (persisted.length >= 30) {
                    sponsorUserIdCache = persisted
                } else {
                    prefs[KEY_SPONSOR_USER_ID] = generated
                }
            }
        }
        return generated
    }

    fun setSponsorCategoryEnabled(id: String, enabled: Boolean) {
        if (SponsorCategory.fromId(id) == null) return
        val next = if (enabled) _sponsorCategories.value + id else _sponsorCategories.value - id
        _sponsorCategories.value = next
        val encoded = next.sorted().joinToString(",")
        scope.launch { context.biliDataStore.edit { it[KEY_SPONSOR_CATEGORIES] = encoded } }
    }

    fun setLongPressSpeed(percent: Int) {
        _longPressSpeed.value = percent.coerceIn(125, 400)
        scope.launch { context.biliDataStore.edit { it[KEY_LONG_PRESS_SPEED] = _longPressSpeed.value } }
    }

    fun setAdjustableLongPressSpeed(enabled: Boolean) {
        _adjustableLongPressSpeed.value = enabled
        if (enabled) _swipeDownLockLongPressSpeed.value = false
        scope.launch {
            context.biliDataStore.edit {
                it[KEY_ADJUSTABLE_LONG_PRESS_SPEED] = enabled
                if (enabled) it[KEY_SWIPE_DOWN_LOCK_LONG_PRESS_SPEED] = false
            }
        }
    }

    fun setSwipeDownLockLongPressSpeed(enabled: Boolean) {
        _swipeDownLockLongPressSpeed.value = enabled
        if (enabled) _adjustableLongPressSpeed.value = false
        scope.launch {
            context.biliDataStore.edit {
                it[KEY_SWIPE_DOWN_LOCK_LONG_PRESS_SPEED] = enabled
                if (enabled) it[KEY_ADJUSTABLE_LONG_PRESS_SPEED] = false
            }
        }
    }

    fun setGestureHaptic(enabled: Boolean) {
        _gestureHaptic.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_GESTURE_HAPTIC] = enabled } }
    }

    fun setReturnBounce(enabled: Boolean) {
        _returnBounce.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_RETURN_BOUNCE] = enabled } }
    }

    fun setPredictiveBackFollow(enabled: Boolean) {
        _predictiveBackFollow.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_PREDICTIVE_BACK_FOLLOW] = enabled } }
    }

    fun setInAppBackgroundPlay(enabled: Boolean) {
        _inAppBackgroundPlay.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_IN_APP_BACKGROUND_PLAY] = enabled } }
    }

    fun setBackgroundPlay(enabled: Boolean) {
        _backgroundPlay.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_BACKGROUND_PLAY] = enabled } }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        setPlaybackEndMode(if (enabled) PlaybackEndMode.AUTO_NEXT else PlaybackEndMode.OFF)
    }

    fun setPlaybackEndMode(mode: PlaybackEndMode) {
        _playbackEndMode.value = mode
        _autoPlayNext.value = mode == PlaybackEndMode.AUTO_NEXT
        scope.launch {
            context.biliDataStore.edit {
                it[KEY_PLAYBACK_END_MODE] = mode.code
                it[KEY_AUTO_PLAY_NEXT] = mode == PlaybackEndMode.AUTO_NEXT
            }
        }
    }

    fun setAutoPip(enabled: Boolean) {
        _autoPip.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_AUTO_PIP] = enabled } }
    }

    fun setAutoCheckUpdate(enabled: Boolean) {
        _autoCheckUpdate.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_AUTO_CHECK_UPDATE] = enabled } }
    }

    fun setAcceptPrerelease(enabled: Boolean) {
        _acceptPrerelease.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_ACCEPT_PRERELEASE] = enabled } }
    }

    fun setInAppUpdate(enabled: Boolean) {
        _inAppUpdate.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_IN_APP_UPDATE] = enabled } }
    }

    fun setUpdateCheckedAt(epochMs: Long) {
        _updateCheckedAt.value = epochMs
        scope.launch { context.biliDataStore.edit { it[KEY_UPDATE_CHECKED_AT] = epochMs } }
    }

    fun setSkippedUpdateVersion(version: String) {
        _skippedUpdateVersion.value = version
        scope.launch { context.biliDataStore.edit { it[KEY_SKIPPED_UPDATE] = version } }
    }

    fun setDanmakuEnabled(enabled: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(enabled = enabled))
        scope.launch { context.biliDataStore.edit { it[KEY_DANMAKU_ENABLED] = enabled } }
    }

    fun setAdvancedPlusOne(enabled: Boolean) {
        _advancedPlusOne.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_ADVANCED_PLUS_ONE] = enabled } }
    }

    fun setSpeedLock(locked: Boolean, currentSpeed: Float) {
        val clamped = currentSpeed.coerceIn(0.25f, 4f)
        _speedLocked.value = locked
        if (locked) _lockedSpeed.value = clamped
        scope.launch {
            context.biliDataStore.edit { prefs ->
                prefs[KEY_SPEED_LOCKED] = locked
                if (locked) prefs[KEY_LOCKED_SPEED] = clamped
            }
        }
    }

    fun setLockedSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4f)
        _lockedSpeed.value = clamped
        scope.launch { context.biliDataStore.edit { it[KEY_LOCKED_SPEED] = clamped } }
    }

    fun setDanmakuAllowScroll(allow: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(allowScroll = allow))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_ALLOW_SCROLL] = allow } }
    }

    fun setDanmakuAllowTop(allow: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(allowTop = allow))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_ALLOW_TOP] = allow } }
    }

    fun setDanmakuAllowBottom(allow: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(allowBottom = allow))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_ALLOW_BOTTOM] = allow } }
    }

    fun setDanmakuAllowColorful(allow: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(allowColorful = allow))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_ALLOW_COLORFUL] = allow } }
    }

    fun setDanmakuAllowSpecial(allow: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(allowSpecial = allow))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_ALLOW_SPECIAL] = allow } }
    }

    fun setDanmakuMergeDuplicates(merge: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(mergeDuplicates = merge))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_MERGE_DUP] = merge } }
    }

    fun setDanmakuMassiveMode(massive: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(massiveMode = massive))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_MASSIVE] = massive } }
    }

    fun setDanmakuOpacity(value: Int) {
        val v = value.coerceIn(10, 100)
        syncDanmakuState(_danmakuSettings.value.copy(opacity = v))
        scope.launch { context.biliDataStore.edit { it[KEY_DANMAKU_OPACITY] = v } }
    }

    fun setDanmakuFontScale(value: Int) {
        val v = value.coerceIn(50, 200)
        syncDanmakuState(_danmakuSettings.value.copy(fontScale = v))
        scope.launch { context.biliDataStore.edit { it[KEY_DANMAKU_FONT] = v } }
    }

    fun setDanmakuArea(value: Int) {
        val v = value.coerceIn(0, 3)
        syncDanmakuState(_danmakuSettings.value.copy(area = v))
        scope.launch { context.biliDataStore.edit { it[KEY_DANMAKU_AREA] = v } }
    }

    fun setDanmakuSpeed(value: Int) {
        val v = value.coerceIn(50, 200)
        syncDanmakuState(_danmakuSettings.value.copy(speed = v))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_SPEED] = v } }
    }

    fun setDanmakuStrokeWidth(value: Int) {
        val v = value.coerceIn(0, 30)
        syncDanmakuState(_danmakuSettings.value.copy(strokeWidth = v))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_STROKE] = v } }
    }

    fun setDanmakuFontWeight(value: Int) {
        val v = value.coerceIn(1, 9)
        syncDanmakuState(_danmakuSettings.value.copy(fontWeight = v))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_WEIGHT] = v } }
    }

    fun setDanmakuLineHeight(value: Int) {
        val v = value.coerceIn(100, 200)
        syncDanmakuState(_danmakuSettings.value.copy(lineHeight = v))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_LINE_HEIGHT] = v } }
    }

    fun setDanmakuWeightFilterLevel(value: Int) {
        val v = value.coerceIn(0, 10)
        syncDanmakuState(_danmakuSettings.value.copy(weightFilterLevel = v))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_WEIGHT_FILTER] = v } }
    }

    fun setDanmakuCap60Fps(enabled: Boolean) {
        syncDanmakuState(_danmakuSettings.value.copy(cap60Fps = enabled))
        scope.launch { context.biliDataStore.edit { it[KEY_DM_CAP_60FPS] = enabled } }
    }

    fun setDecoderMode(mode: Int) {
        val v = mode.coerceIn(0, 2)
        _decoderMode.value = v
        scope.launch { context.biliDataStore.edit { it[KEY_DECODER_MODE] = v } }
    }

    fun setPreferSurfaceView(enabled: Boolean) {
        _preferSurfaceView.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_PREFER_SURFACE_VIEW] = enabled } }
    }

    fun resetDanmakuSettings() {
        val defaults = DanmakuSettings(enabled = _danmakuSettings.value.enabled)
        syncDanmakuState(defaults)
        scope.launch {
            context.biliDataStore.edit {
                it[KEY_DM_ALLOW_SCROLL] = defaults.allowScroll
                it[KEY_DM_ALLOW_TOP] = defaults.allowTop
                it[KEY_DM_ALLOW_BOTTOM] = defaults.allowBottom
                it[KEY_DM_ALLOW_COLORFUL] = defaults.allowColorful
                it[KEY_DM_ALLOW_SPECIAL] = defaults.allowSpecial
                it[KEY_DM_MERGE_DUP] = defaults.mergeDuplicates
                it[KEY_DM_MASSIVE] = defaults.massiveMode
                it[KEY_DANMAKU_AREA] = defaults.area
                it[KEY_DANMAKU_OPACITY] = defaults.opacity
                it[KEY_DANMAKU_FONT] = defaults.fontScale
                it[KEY_DM_SPEED] = defaults.speed
                it[KEY_DM_STROKE] = defaults.strokeWidth
                it[KEY_DM_WEIGHT] = defaults.fontWeight
                it[KEY_DM_LINE_HEIGHT] = defaults.lineHeight
                it[KEY_DM_WEIGHT_FILTER] = defaults.weightFilterLevel
                it[KEY_DM_CAP_60FPS] = defaults.cap60Fps
            }
        }
    }

    fun setDarkMode(mode: Int) {
        _darkMode.value = mode.coerceIn(0, 2)
        scope.launch { context.biliDataStore.edit { it[KEY_DARK_MODE] = _darkMode.value } }
    }

    fun setDefaultQuality(quality: Int) {
        val resolved = DefaultQuality.fromId(quality).id
        _defaultQuality.value = resolved
        scope.launch { context.biliDataStore.edit { it[KEY_QUALITY] = resolved } }
    }

    fun setCdnOption(id: String) {
        val value = CdnOption.fromId(id).id
        _cdnOption.value = value
        scope.launch { context.biliDataStore.edit { it[KEY_CDN] = value } }
    }

    fun setFeedSource(mode: Int) {
        val resolved = mode.coerceIn(0, 1)
        _feedSource.value = resolved
        scope.launch { context.biliDataStore.edit { it[KEY_FEED_SOURCE] = resolved } }
    }

    fun addSearchHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        _searchHistory.value = (listOf(trimmed) + _searchHistory.value.filterNot { it == trimmed })
            .take(HISTORY_LIMIT)
        persistHistory()
    }

    fun removeSearchHistory(keyword: String) {
        _searchHistory.value = _searchHistory.value.filterNot { it == keyword }
        persistHistory()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        persistHistory()
    }

    private fun persistHistory() {
        val encoded = _searchHistory.value.joinToString("\u0001")
        scope.launch { context.biliDataStore.edit { it[KEY_SEARCH_HISTORY] = encoded } }
    }

    private companion object {
        const val HISTORY_LIMIT = 12
        val KEY_DARK_MODE = intPreferencesKey("dark_mode")
        val KEY_QUALITY = intPreferencesKey("default_quality")
        val KEY_CDN = stringPreferencesKey("playback_cdn")
        val KEY_FEED_SOURCE = intPreferencesKey("feed_source")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        val KEY_DANMAKU_ENABLED = booleanPreferencesKey("danmaku_enabled")
        val KEY_ADVANCED_PLUS_ONE = booleanPreferencesKey("danmaku_advanced_plus_one")
        val KEY_DANMAKU_OPACITY = intPreferencesKey("danmaku_opacity")
        val KEY_DANMAKU_FONT = intPreferencesKey("danmaku_font_scale")
        val KEY_DANMAKU_AREA = intPreferencesKey("danmaku_area")
        val KEY_DM_ALLOW_SCROLL = booleanPreferencesKey("danmaku_allow_scroll")
        val KEY_DM_ALLOW_TOP = booleanPreferencesKey("danmaku_allow_top")
        val KEY_DM_ALLOW_BOTTOM = booleanPreferencesKey("danmaku_allow_bottom")
        val KEY_DM_ALLOW_COLORFUL = booleanPreferencesKey("danmaku_allow_colorful")
        val KEY_DM_ALLOW_SPECIAL = booleanPreferencesKey("danmaku_allow_special")
        val KEY_DM_MERGE_DUP = booleanPreferencesKey("danmaku_merge_duplicates")
        val KEY_DM_MASSIVE = booleanPreferencesKey("danmaku_massive_mode")
        val KEY_DM_SPEED = intPreferencesKey("danmaku_speed")
        val KEY_DM_STROKE = intPreferencesKey("danmaku_stroke_width")
        val KEY_DM_WEIGHT = intPreferencesKey("danmaku_font_weight")
        val KEY_DM_LINE_HEIGHT = intPreferencesKey("danmaku_line_height")
        val KEY_DM_WEIGHT_FILTER = intPreferencesKey("danmaku_weight_filter")
        val KEY_DM_CAP_60FPS = booleanPreferencesKey("danmaku_cap_60fps")
        val KEY_IN_APP_BACKGROUND_PLAY = booleanPreferencesKey("in_app_background_play")
        val KEY_BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_pip")
        val KEY_AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val KEY_PLAYBACK_END_MODE = intPreferencesKey("playback_end_mode")
        val KEY_GESTURE_HAPTIC = booleanPreferencesKey("gesture_haptic")
        val KEY_RETURN_BOUNCE = booleanPreferencesKey("return_bounce")
        val KEY_PREDICTIVE_BACK_FOLLOW = booleanPreferencesKey("predictive_back_follow")
        val KEY_AUTO_ROTATE_MODE = intPreferencesKey("auto_rotate_mode")
        val KEY_LONG_PRESS_SPEED = intPreferencesKey("long_press_speed")
        val KEY_ADJUSTABLE_LONG_PRESS_SPEED = booleanPreferencesKey("adjustable_long_press_speed")
        val KEY_SWIPE_DOWN_LOCK_LONG_PRESS_SPEED = booleanPreferencesKey("swipe_down_lock_long_press_speed")
        val KEY_SPEED_LOCKED = booleanPreferencesKey("playback_speed_locked")
        val KEY_LOCKED_SPEED = floatPreferencesKey("playback_locked_speed")
        const val DEFAULT_LONG_PRESS_SPEED = 200
        val KEY_GESTURE_VOL_BRIGHT = booleanPreferencesKey("gesture_vol_bright")
        val KEY_FS_GESTURE_HINT = booleanPreferencesKey("fullscreen_gesture_hint")
        val KEY_DECODER_MODE = intPreferencesKey("video_decoder_mode")
        val KEY_PREFER_SURFACE_VIEW = booleanPreferencesKey("prefer_surface_view")
        val KEY_SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")
        val KEY_SPONSOR_SKIP_MODE = intPreferencesKey("sponsor_skip_mode")
        val KEY_SPONSOR_CATEGORIES = stringPreferencesKey("sponsor_categories")
        val KEY_SPONSOR_USER_ID = stringPreferencesKey("sponsor_block_user_id")
        val KEY_AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        val KEY_ACCEPT_PRERELEASE = booleanPreferencesKey("accept_prerelease")
        val KEY_IN_APP_UPDATE = booleanPreferencesKey("in_app_update")
        val KEY_UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
        val KEY_SKIPPED_UPDATE = stringPreferencesKey("skipped_update_version")
    }
}
