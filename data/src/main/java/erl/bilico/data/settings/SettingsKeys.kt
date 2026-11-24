package erl.bilico.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object SettingsKeys {
    const val HISTORY_LIMIT = 12
    const val DEFAULT_LONG_PRESS_SPEED = 200

    val KEY_DARK_MODE = intPreferencesKey("dark_mode")
    val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
    val KEY_ACCENT_HYPERLINK = booleanPreferencesKey("accent_hyperlink")
    val KEY_ME_ICON_COLOR_MODE = intPreferencesKey("me_icon_color_mode")
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
    val KEY_GESTURE_VOL_BRIGHT = booleanPreferencesKey("gesture_vol_bright")
    val KEY_FS_GESTURE_HINT = booleanPreferencesKey("fullscreen_gesture_hint")
    val KEY_PLAYER_CONIC_LIGHT = booleanPreferencesKey("player_conic_light")
    val KEY_SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")
    val KEY_SPONSOR_BADGE_ENABLED = booleanPreferencesKey("sponsor_badge_enabled")
    val KEY_PROMOTION_BADGE_ENABLED = booleanPreferencesKey("promotion_badge_enabled")
    val KEY_SPONSOR_SKIP_MODE = intPreferencesKey("sponsor_skip_mode")
    val KEY_SPONSOR_CATEGORIES = stringPreferencesKey("sponsor_categories")
    val KEY_SPONSOR_USER_ID = stringPreferencesKey("sponsor_block_user_id")
    val KEY_AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
    val KEY_ACCEPT_PRERELEASE = booleanPreferencesKey("accept_prerelease")
    val KEY_IN_APP_UPDATE = booleanPreferencesKey("in_app_update")
    val KEY_UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
    val KEY_SKIPPED_UPDATE = stringPreferencesKey("skipped_update_version")
    val KEY_SHOW_VIDEO_DISLIKE = booleanPreferencesKey("show_video_dislike")
}
