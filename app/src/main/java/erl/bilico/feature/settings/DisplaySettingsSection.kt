package erl.bilico.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import erl.bilico.data.settings.CoverAspect
import erl.bilico.data.settings.FeedPreferences
import erl.bilico.ui.accentColorSummary
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 设置页「显示」分组条目，包含主题配色、信息流排版与页面返回动效。 */
internal fun displaySettingsEntries(
    darkMode: Int,
    accentColor: Int,
    accentHyperlink: Boolean,
    meIconColorMode: Int,
    coverAspect: CoverAspect,
    feedColumns: Int,
    feedRefreshCount: Int,
    homeTabsSummary: String,
    showVideoDislike: Boolean,
    returnBounce: Boolean,
    predictiveBackFollow: Boolean,
    onDarkModeChange: (Int) -> Unit,
    onPickAccentColor: () -> Unit,
    onAccentHyperlinkChange: (Boolean) -> Unit,
    onMeIconColorModeChange: (Int) -> Unit,
    onCoverAspectChange: (CoverAspect) -> Unit,
    onFeedColumnsChange: (Int) -> Unit,
    onFeedRefreshCountChange: (Int) -> Unit,
    onOpenHomeTabs: () -> Unit,
    onShowVideoDislikeChange: (Boolean) -> Unit,
    onReturnBounceChange: (Boolean) -> Unit,
    onPredictiveBackFollowChange: (Boolean) -> Unit,
): List<SettingsEntry> {
    val accentSummary = accentColorSummary(accentColor)
    return listOf(
        SettingsEntry(
            id = "dark",
            title = "深色模式",
            keywords = "暗黑 浅色 夜间 跟随系统 主题 dark night light mode",
        ) { pos ->
            OverlayDropdownPreference(
                items = DARK_MODE_OPTIONS,
                selectedIndex = darkMode.coerceIn(0, DARK_MODE_OPTIONS.lastIndex),
                title = "深色模式",
                modifier = Modifier.testTag("settings_dark"),
                cardListPosition = pos,
                onSelectedIndexChange = onDarkModeChange,
            )
        },
        SettingsEntry(
            id = "accent_color",
            title = "主题色",
            summary = accentSummary,
            keywords = "强调色 颜色 配色 Jeb 粉色 彩虹 自定义 theme accent color rainbow",
        ) { pos ->
            ArrowPreference(
                title = "主题色",
                summary = accentSummary,
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
                cardListPosition = pos,
                onClick = onPickAccentColor,
            )
        },
        SettingsEntry(
            id = "accent_hyperlink",
            title = "超链接应用主题色",
            summary = "将强调色应用到话题、@提及、网址与回复链接等文本",
            keywords = "强调色 话题 @ 链接 颜色 评论 动态 link url topic mention",
        ) { pos ->
            SwitchPreference(
                checked = accentHyperlink,
                onCheckedChange = onAccentHyperlinkChange,
                title = "超链接应用主题色",
                summary = "将强调色应用到话题、@提及、网址与回复链接等文本",
                modifier = Modifier.testTag("settings_accent_hyperlink"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "me_icon_color_mode",
            title = "「我的」页图标颜色",
            summary = "切换「我的」页面各入口左侧图标的配色方案",
            keywords = "彩色 黑白 强调色 ColorOS 图标 主题色 icon color",
        ) { pos ->
            OverlayDropdownPreference(
                items = ME_ICON_COLOR_OPTIONS,
                selectedIndex = meIconColorMode.coerceIn(0, ME_ICON_COLOR_OPTIONS.lastIndex),
                title = "「我的」页图标颜色",
                summary = "切换「我的」页面各入口左侧图标的配色方案",
                modifier = Modifier.testTag("settings_me_icon_color_mode"),
                cardListPosition = pos,
                onSelectedIndexChange = onMeIconColorModeChange,
            )
        },
        SettingsEntry(
            id = "cover_aspect",
            title = "封面比例",
            summary = "推荐流卡片的封面裁切比例",
            keywords = "16:9 4:3 推荐 封面 裁切",
        ) { pos ->
            OverlayDropdownPreference(
                items = COVER_ASPECT_OPTIONS,
                selectedIndex = coverAspect.code.coerceIn(0, COVER_ASPECT_OPTIONS.lastIndex),
                title = "封面比例",
                summary = "推荐流卡片的封面裁切比例",
                modifier = Modifier.testTag("settings_cover_aspect"),
                cardListPosition = pos,
                onSelectedIndexChange = { index -> onCoverAspectChange(CoverAspect.fromCode(index)) },
            )
        },
        SettingsEntry(
            id = "feed_columns",
            title = "推荐列数",
            summary = "推荐页面每行显示的视频列数",
            keywords = "单列 双列 首页 排版 网格",
        ) { pos ->
            OverlayDropdownPreference(
                items = FEED_COLUMN_OPTIONS,
                selectedIndex = (feedColumns - FeedPreferences.MIN_COLUMNS)
                    .coerceIn(0, FEED_COLUMN_OPTIONS.lastIndex),
                title = "推荐列数",
                summary = "推荐页面每行显示的视频列数",
                modifier = Modifier.testTag("settings_feed_columns"),
                cardListPosition = pos,
                onSelectedIndexChange = { index -> onFeedColumnsChange(FeedPreferences.MIN_COLUMNS + index) },
            )
        },
        SettingsEntry(
            id = "feed_refresh_count",
            title = "下拉刷新数量",
            summary = "每次下拉刷新推荐流时拉取的视频数",
            keywords = "推荐 数量 刷新",
        ) { pos ->
            OverlayDropdownPreference(
                items = FEED_REFRESH_COUNT_LABELS,
                selectedIndex = FeedPreferences.REFRESH_COUNT_OPTIONS
                    .indexOf(feedRefreshCount)
                    .let { if (it >= 0) it else DEFAULT_REFRESH_COUNT_INDEX },
                title = "下拉刷新数量",
                summary = "每次下拉刷新推荐流时拉取的视频数",
                modifier = Modifier.testTag("settings_feed_refresh_count"),
                cardListPosition = pos,
                onSelectedIndexChange = { index ->
                    onFeedRefreshCountChange(FeedPreferences.REFRESH_COUNT_OPTIONS[index])
                },
            )
        },
        SettingsEntry(
            id = "home_tabs",
            title = "首页分类",
            summary = homeTabsSummary,
            keywords = "标签 排序 热门 排行 番剧 影视 直播",
        ) { pos ->
            ArrowPreference(
                title = "首页分类",
                summary = homeTabsSummary,
                modifier = Modifier.testTag("settings_home_tabs"),
                cardListPosition = pos,
                onClick = onOpenHomeTabs,
            )
        },
        SettingsEntry(
            id = "show_video_dislike",
            title = "显示点踩按钮",
            summary = "在视频详情页操作栏显示点踩",
            keywords = "不喜欢 点踩 操作栏",
        ) { pos ->
            SwitchPreference(
                checked = showVideoDislike,
                onCheckedChange = onShowVideoDislikeChange,
                title = "显示点踩按钮",
                summary = "在视频详情页操作栏显示点踩",
                modifier = Modifier.testTag("settings_show_video_dislike"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "return_bounce",
            title = "返回动画回弹",
            summary = "退回卡片时保留轻压回弹效果",
            keywords = "转场 动画 卡片 回弹",
        ) { pos ->
            SwitchPreference(
                checked = returnBounce,
                onCheckedChange = onReturnBounceChange,
                title = "返回动画回弹",
                summary = "退回卡片时保留轻压回弹效果",
                modifier = Modifier.testTag("settings_return_bounce"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "predictive_back_follow",
            title = "返回跟手轨迹",
            summary = "侧滑返回时跟随手指偏移，缩回卡片或融合球时顺着甩出方向回缩",
            keywords = "预测返回 手势 侧滑 融合球 动画",
        ) { pos ->
            SwitchPreference(
                checked = predictiveBackFollow,
                onCheckedChange = onPredictiveBackFollowChange,
                title = "返回跟手轨迹",
                summary = "侧滑返回时跟随手指偏移，缩回卡片或融合球时顺着甩出方向回缩",
                modifier = Modifier.testTag("settings_predictive_back_follow"),
                cardListPosition = pos,
            )
        },
    )
}

/** 下标即存储值：0 跟随系统，1 浅色，2 深色。 */
private val DARK_MODE_OPTIONS = listOf("跟随系统", "浅色", "深色")
/** 下标即存储值：0 默认色（ColorOS 17 彩色），1 黑白色，2 强调色。 */
private val ME_ICON_COLOR_OPTIONS = listOf("默认色", "黑白色", "强调色")
private val COVER_ASPECT_OPTIONS = CoverAspect.entries.map { it.label }
private val FEED_COLUMN_OPTIONS = (FeedPreferences.MIN_COLUMNS..FeedPreferences.MAX_COLUMNS).map { "${it} 列" }
private val FEED_REFRESH_COUNT_LABELS = FeedPreferences.REFRESH_COUNT_OPTIONS.map { "${it} 个" }
private val DEFAULT_REFRESH_COUNT_INDEX = FeedPreferences.REFRESH_COUNT_OPTIONS
    .indexOf(FeedPreferences.DEFAULT_REFRESH_COUNT)
    .coerceAtLeast(0)
