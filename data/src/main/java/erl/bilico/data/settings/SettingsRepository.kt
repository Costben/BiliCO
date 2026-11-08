package erl.bilico.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
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

    private val _backgroundPlay = MutableStateFlow(true)
    val backgroundPlay: StateFlow<Boolean> = _backgroundPlay.asStateFlow()

    private val _autoPip = MutableStateFlow(true)
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()

    private val _gestureHaptic = MutableStateFlow(true)
    val gestureHaptic: StateFlow<Boolean> = _gestureHaptic.asStateFlow()

    /** 长按倍速的倍数，按百分比存：200 = 2x。 */
    private val _longPressSpeed = MutableStateFlow(DEFAULT_LONG_PRESS_SPEED)
    val longPressSpeed: StateFlow<Int> = _longPressSpeed.asStateFlow()

    private val _autoRotateMode = MutableStateFlow(1)
    val autoRotateMode: StateFlow<Int> = _autoRotateMode.asStateFlow()

    private val _gestureVolumeBrightness = MutableStateFlow(true)
    val gestureVolumeBrightness: StateFlow<Boolean> = _gestureVolumeBrightness.asStateFlow()

    private val _fullscreenGestureHint = MutableStateFlow(true)
    val fullscreenGestureHint: StateFlow<Boolean> = _fullscreenGestureHint.asStateFlow()

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
            _defaultQuality.value = prefs[KEY_QUALITY] ?: DefaultQuality.P1080.id
            _cdnOption.value = CdnOption.fromId(prefs[KEY_CDN]).id
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
            )
            syncDanmakuState(dm)
            _backgroundPlay.value = prefs[KEY_BACKGROUND_PLAY] ?: true
            _autoPip.value = prefs[KEY_AUTO_PIP] ?: true
            _gestureHaptic.value = prefs[KEY_GESTURE_HAPTIC] ?: true
            _longPressSpeed.value = (prefs[KEY_LONG_PRESS_SPEED] ?: DEFAULT_LONG_PRESS_SPEED)
                .coerceIn(125, 400)
            _autoRotateMode.value = prefs[KEY_AUTO_ROTATE_MODE] ?: 1
            _gestureVolumeBrightness.value = prefs[KEY_GESTURE_VOL_BRIGHT] ?: true
            _fullscreenGestureHint.value = prefs[KEY_FS_GESTURE_HINT] ?: true
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

    fun setLongPressSpeed(percent: Int) {
        _longPressSpeed.value = percent.coerceIn(125, 400)
        scope.launch { context.biliDataStore.edit { it[KEY_LONG_PRESS_SPEED] = _longPressSpeed.value } }
    }

    fun setGestureHaptic(enabled: Boolean) {
        _gestureHaptic.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_GESTURE_HAPTIC] = enabled } }
    }

    fun setBackgroundPlay(enabled: Boolean) {
        _backgroundPlay.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_BACKGROUND_PLAY] = enabled } }
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
            }
        }
    }

    fun setDarkMode(mode: Int) {
        _darkMode.value = mode.coerceIn(0, 2)
        scope.launch { context.biliDataStore.edit { it[KEY_DARK_MODE] = _darkMode.value } }
    }

    fun setDefaultQuality(quality: Int) {
        _defaultQuality.value = quality
        scope.launch { context.biliDataStore.edit { it[KEY_QUALITY] = quality } }
    }

    fun setCdnOption(id: String) {
        val value = CdnOption.fromId(id).id
        _cdnOption.value = value
        scope.launch { context.biliDataStore.edit { it[KEY_CDN] = value } }
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
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
        val KEY_DANMAKU_ENABLED = booleanPreferencesKey("danmaku_enabled")
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
        val KEY_BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_pip")
        val KEY_GESTURE_HAPTIC = booleanPreferencesKey("gesture_haptic")
        val KEY_AUTO_ROTATE_MODE = intPreferencesKey("auto_rotate_mode")
        val KEY_LONG_PRESS_SPEED = intPreferencesKey("long_press_speed")
        const val DEFAULT_LONG_PRESS_SPEED = 200
        val KEY_GESTURE_VOL_BRIGHT = booleanPreferencesKey("gesture_vol_bright")
        val KEY_FS_GESTURE_HINT = booleanPreferencesKey("fullscreen_gesture_hint")
        val KEY_AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        val KEY_ACCEPT_PRERELEASE = booleanPreferencesKey("accept_prerelease")
        val KEY_IN_APP_UPDATE = booleanPreferencesKey("in_app_update")
        val KEY_UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
        val KEY_SKIPPED_UPDATE = stringPreferencesKey("skipped_update_version")
    }
}
