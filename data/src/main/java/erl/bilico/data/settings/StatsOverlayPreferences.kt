package erl.bilico.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 统计信息浮窗可开关的指标字段，顺序与统计信息面板保持一致。 */
enum class StatsOverlayField(
    val id: String,
    val label: String,
    val defaultEnabled: Boolean,
) {
    QUALITY("quality", "清晰度", true),
    RESOLUTION("resolution", "分辨率", true),
    FRAME_RATE("frame_rate", "帧率", true),
    PLAYER_TYPE("player_type", "播放器", false),
    VIDEO_CODEC("video_codec", "视频编码", true),
    AUDIO_CODEC("audio_codec", "音频编码", false),
    VIDEO_BITRATE("video_bitrate", "视频码率", true),
    AUDIO_BITRATE("audio_bitrate", "音频码率", false),
    RENDERED_FRAMES("rendered_frames", "已渲染帧", false),
    DROPPED_FRAMES("dropped_frames", "丢帧", true),
    BUFFER("buffer", "缓冲", false),
    BANDWIDTH("bandwidth", "带宽估计", false),
    VIDEO_HOST("video_host", "视频线路", false),
    AUDIO_HOST("audio_host", "音频线路", false),
    SPEED("speed", "播放速度", false);

    companion object {
        val ALL_IDS: Set<String> = entries.map { it.id }.toSet()
        val DEFAULT_IDS: Set<String> = entries.filter { it.defaultEnabled }.map { it.id }.toSet()

        fun fromId(id: String): StatsOverlayField? = entries.firstOrNull { it.id == id }
    }
}

/** 快捷停靠的四角位置；坐标以画面安全区可活动范围的百分比表示。 */
enum class StatsOverlayCorner(
    val label: String,
    val xFraction: Float,
    val yFraction: Float,
) {
    TOP_LEFT("左上", 0f, 0f),
    TOP_RIGHT("右上", 1f, 0f),
    BOTTOM_LEFT("左下", 0f, 1f),
    BOTTOM_RIGHT("右下", 1f, 1f);
}

/** 播放器视频统计信息浮窗的开关、屏幕位置与显示字段配置。 */
class StatsOverlayPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _xFraction = MutableStateFlow(0f)
    val xFraction: StateFlow<Float> = _xFraction.asStateFlow()

    private val _yFraction = MutableStateFlow(0f)
    val yFraction: StateFlow<Float> = _yFraction.asStateFlow()

    private val _visibleFields = MutableStateFlow(StatsOverlayField.DEFAULT_IDS)
    val visibleFields: StateFlow<Set<String>> = _visibleFields.asStateFlow()

    suspend fun restore() {
        val prefs = context.biliDataStore.data.first()
        _enabled.value = prefs[KEY_ENABLED] ?: false
        _xFraction.value = (prefs[KEY_X_FRACTION] ?: 0f).coerceIn(0f, 1f)
        _yFraction.value = (prefs[KEY_Y_FRACTION] ?: 0f).coerceIn(0f, 1f)
        _visibleFields.value = decodeFields(prefs[KEY_FIELDS])
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == _enabled.value) return
        _enabled.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_ENABLED] = enabled } }
    }

    fun toggleEnabled() {
        setEnabled(!_enabled.value)
    }

    fun setPosition(xFraction: Float, yFraction: Float) {
        val nx = xFraction.coerceIn(0f, 1f)
        val ny = yFraction.coerceIn(0f, 1f)
        if (nx == _xFraction.value && ny == _yFraction.value) return
        _xFraction.value = nx
        _yFraction.value = ny
        scope.launch {
            context.biliDataStore.edit {
                it[KEY_X_FRACTION] = nx
                it[KEY_Y_FRACTION] = ny
            }
        }
    }

    fun setCorner(corner: StatsOverlayCorner) {
        setPosition(corner.xFraction, corner.yFraction)
    }

    fun setFieldVisible(fieldId: String, visible: Boolean) {
        if (StatsOverlayField.fromId(fieldId) == null) return
        val next = if (visible) {
            _visibleFields.value + fieldId
        } else {
            _visibleFields.value - fieldId
        }
        applyFields(next)
    }

    fun selectAllFields() {
        applyFields(StatsOverlayField.ALL_IDS)
    }

    fun resetFieldsToDefault() {
        applyFields(StatsOverlayField.DEFAULT_IDS)
    }

    private fun applyFields(next: Set<String>) {
        if (next == _visibleFields.value) return
        _visibleFields.value = next
        val encoded = encodeFields(next)
        scope.launch { context.biliDataStore.edit { it[KEY_FIELDS] = encoded } }
    }

    companion object {
        private const val EMPTY_FIELDS_SENTINEL = "__none__"
        private const val CORNER_SNAP_EPSILON = 0.03f

        private val KEY_ENABLED = booleanPreferencesKey("stats_overlay_enabled")
        private val KEY_X_FRACTION = floatPreferencesKey("stats_overlay_x_fraction")
        private val KEY_Y_FRACTION = floatPreferencesKey("stats_overlay_y_fraction")
        private val KEY_FIELDS = stringPreferencesKey("stats_overlay_fields")

        fun matchingCorner(xFraction: Float, yFraction: Float): StatsOverlayCorner? =
            StatsOverlayCorner.entries.firstOrNull { corner ->
                abs(corner.xFraction - xFraction) <= CORNER_SNAP_EPSILON &&
                    abs(corner.yFraction - yFraction) <= CORNER_SNAP_EPSILON
            }

        fun positionLabel(xFraction: Float, yFraction: Float): String {
            matchingCorner(xFraction, yFraction)?.let { return it.label }
            val px = (xFraction.coerceIn(0f, 1f) * 100).roundToInt()
            val py = (yFraction.coerceIn(0f, 1f) * 100).roundToInt()
            return "自定义 ($px%, $py%)"
        }

        fun summary(
            enabled: Boolean,
            xFraction: Float,
            yFraction: Float,
            visibleCount: Int,
        ): String {
            if (!enabled) return "已关闭"
            return "已开启 · ${positionLabel(xFraction, yFraction)} · ${visibleCount} 项"
        }

        private fun encodeFields(fields: Set<String>): String {
            if (fields.isEmpty()) return EMPTY_FIELDS_SENTINEL
            return StatsOverlayField.entries
                .asSequence()
                .map { it.id }
                .filter { it in fields }
                .joinToString(",")
        }

        private fun decodeFields(raw: String?): Set<String> {
            if (raw == null) return StatsOverlayField.DEFAULT_IDS
            if (raw == EMPTY_FIELDS_SENTINEL || raw.isBlank()) return emptySet()
            return raw.split(',')
                .filter { StatsOverlayField.fromId(it) != null }
                .toSet()
        }
    }
}
