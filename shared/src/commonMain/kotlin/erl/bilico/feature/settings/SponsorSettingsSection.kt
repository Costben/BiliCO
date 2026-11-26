package erl.bilico.feature.settings

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** 设置页「空降助手」分组条目，包含商单与推广标记开关。 */
internal fun sponsorSettingsEntries(
    enabled: Boolean,
    skipMode: Int,
    skipModeOptions: List<String>,
    selectedCategoryCount: Int,
    sponsorBadgeEnabled: Boolean,
    promotionBadgeEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSkipModeChange: (Int) -> Unit,
    onOpenCategories: () -> Unit,
    onSponsorBadgeChange: (Boolean) -> Unit,
    onPromotionBadgeChange: (Boolean) -> Unit,
): List<SettingsEntry> = listOf(
    SettingsEntry(
        id = "sponsor_block",
        title = "空降助手",
        summary = "跳过或标记视频内的赞助广告、三连与片头片尾",
        keywords = "SponsorBlock sponsor ad skip 广告 恰饭 跳过",
    ) { pos ->
        SettingsSwitchRow(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            title = "空降助手",
            summary = "跳过或标记视频内的赞助广告、三连与片头片尾",
            modifier = Modifier.testTag("settings_sponsor_block"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "sponsor_mode",
        title = "空降方式",
        keywords = "SponsorBlock 自动跳过 手动提示 标记",
    ) { pos ->
        SettingsDropdownRow(
            items = skipModeOptions,
            selectedIndex = skipMode.coerceIn(0, skipModeOptions.lastIndex),
            title = "空降方式",
            enabled = enabled,
            modifier = Modifier.testTag("settings_sponsor_mode"),
            cardListPosition = pos,
            onSelectedIndexChange = onSkipModeChange,
        )
    },
    SettingsEntry(
        id = "sponsor_categories",
        title = "跳过片段类型",
        summary = "已启用 ${selectedCategoryCount} 类",
        keywords = "SponsorBlock 赞助 广告 三连 片头 片尾",
    ) { pos ->
        SettingsArrowRow(
            title = "跳过片段类型",
            summary = "已启用 ${selectedCategoryCount} 类",
            enabled = enabled,
            modifier = Modifier.testTag("settings_sponsor_categories"),
            cardListPosition = pos,
            onClick = onOpenCategories,
        )
    },
    SettingsEntry(
        id = "sponsor_badge",
        title = "详情页标记商单",
        summary = "用空降助手识别商单广告，在详情页 BV 号旁标注「商单」或「疑似商单」",
        keywords = "恰饭 广告 商单 标识",
    ) { pos ->
        SettingsSwitchRow(
            checked = sponsorBadgeEnabled,
            onCheckedChange = onSponsorBadgeChange,
            title = "详情页标记商单",
            summary = "用空降助手识别商单广告，在详情页 BV 号旁标注「商单」或「疑似商单」",
            modifier = Modifier.testTag("settings_sponsor_badge"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "promotion_badge",
        title = "详情页标记推广",
        summary = "在视频详情页的标题前标注「推广」；关闭后推荐流会过滤掉推广卡片",
        keywords = "商业推广 小火箭 广告 过滤",
    ) { pos ->
        SettingsSwitchRow(
            checked = promotionBadgeEnabled,
            onCheckedChange = onPromotionBadgeChange,
            title = "详情页标记推广",
            summary = "在视频详情页的标题前标注「推广」；关闭后推荐流会过滤掉推广卡片",
            modifier = Modifier.testTag("settings_promotion_badge"),
            cardListPosition = pos,
        )
    },
)
