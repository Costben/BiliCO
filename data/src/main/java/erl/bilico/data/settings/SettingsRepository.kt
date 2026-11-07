package erl.bilico.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 默认清晰度档位，值就是 B 站的 quality 编号。 */
enum class DefaultQuality(val id: Int, val label: String) {
    P1080(80, "1080P 高清"),
    P720(64, "720P 高清"),
    P480(32, "480P 清晰"),
    P360(16, "360P 流畅"),
}

/**
 * 弹幕完整配置（对齐霹雳派 DanmakuConfig 与霹雳Plus PlDanmakuPanel）：
 * - 按类型屏蔽：滚动 / 顶部 / 底部 / 彩色 / 高级
 * - 功能开关：合并重复弹幕 / 海量弹幕模式
 * - 显示样式：区域 / 不透明度 / 字号 / 弹幕速度 / 描边粗细 / 字体粗细 / 行高
 */
data class DanmakuSettings(
    val enabled: Boolean = true,
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val allowColorful: Boolean = true,
    val allowSpecial: Boolean = true,
    val mergeDuplicates: Boolean = true,
    val massiveMode: Boolean = false,
    /** 0 = 1/4 屏, 1 = 半屏, 2 = 3/4 屏, 3 = 全屏 */
    val area: Int = 1,
    /** 10..100 (%) */
    val opacity: Int = 100,
    /** 50..200 (%) */
    val fontScale: Int = 100,
    /** 50..200 (%)，100% 对应 8 秒滚屏时长 */
    val speed: Int = 100,
    /** 0..30，对应 0.0..3.0dp 描边宽度（默认 16 = 1.6dp） */
    val strokeWidth: Int = 16,
    /** 1..9，对应字重 100..900（默认 6 = 600 SemiBold） */
    val fontWeight: Int = 6,
    /** 100..200 (%)，对应 1.0x..2.0x 行高（默认 135 = 1.35x） */
    val lineHeight: Int = 135,
) {
    val areaFraction: Float
        get() = when (area) {
            0 -> 0.25f
            1 -> 0.50f
            2 -> 0.75f
            else -> 1.00f
        }

    val areaLabel: String
        get() = when (area) {
            0 -> "1/4 屏"
            1 -> "半屏"
            2 -> "3/4 屏"
            else -> "全屏"
        }

    val strokeWidthDp: Float
        get() = (strokeWidth.coerceIn(0, 30)) / 10f

    val lineHeightRatio: Float
        get() = (lineHeight.coerceIn(100, 200)) / 100f

    val scrollDurationMs: Long
        get() = (8_000L * 100L / speed.coerceIn(50, 200)).coerceIn(3_000L, 16_000L)
}

/**
 * 本地设置：深色模式、默认清晰度、弹幕设置、播放手势与搜索历史。
 * 值是 StateFlow，Compose 直接 collect；写入走 DataStore，进程重启后保留。
 */
class SettingsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _darkMode = MutableStateFlow(0)
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    private val _defaultQuality = MutableStateFlow(DefaultQuality.P1080.id)
    val defaultQuality: StateFlow<Int> = _defaultQuality.asStateFlow()

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

    private val _autoRotateMode = MutableStateFlow(1)
    val autoRotateMode: StateFlow<Int> = _autoRotateMode.asStateFlow()

    private val _gestureVolumeBrightness = MutableStateFlow(true)
    val gestureVolumeBrightness: StateFlow<Boolean> = _gestureVolumeBrightness.asStateFlow()

    private val _fullscreenGestureHint = MutableStateFlow(true)
    val fullscreenGestureHint: StateFlow<Boolean> = _fullscreenGestureHint.asStateFlow()

    init {
        scope.launch {
            val prefs = context.biliDataStore.data.first()
            _darkMode.value = prefs[KEY_DARK_MODE] ?: 0
            _defaultQuality.value = prefs[KEY_QUALITY] ?: DefaultQuality.P1080.id
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
            _autoRotateMode.value = prefs[KEY_AUTO_ROTATE_MODE] ?: 1
            _gestureVolumeBrightness.value = prefs[KEY_GESTURE_VOL_BRIGHT] ?: true
            _fullscreenGestureHint.value = prefs[KEY_FS_GESTURE_HINT] ?: true
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
        val KEY_GESTURE_VOL_BRIGHT = booleanPreferencesKey("gesture_vol_bright")
        val KEY_FS_GESTURE_HINT = booleanPreferencesKey("fullscreen_gesture_hint")
    }
}
