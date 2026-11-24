package erl.bilico.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference

/** 设置页「空降助手」分组卡片，包含商单与推广标记开关。 */
@Composable
internal fun SponsorSettingsCard(
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        SwitchPreference(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            title = "空降助手",
            summary = "跳过或标记视频内的赞助广告、三连与片头片尾",
            modifier = Modifier.testTag("settings_sponsor_block"),
            cardListPosition = CardListPosition.Head,
        )
        OverlayDropdownPreference(
            items = skipModeOptions,
            selectedIndex = skipMode.coerceIn(0, skipModeOptions.lastIndex),
            title = "空降方式",
            enabled = enabled,
            modifier = Modifier.testTag("settings_sponsor_mode"),
            cardListPosition = CardListPosition.Middle,
            onSelectedIndexChange = onSkipModeChange,
        )
        ArrowPreference(
            title = "跳过片段类型",
            summary = "已启用 ${selectedCategoryCount} 类",
            enabled = enabled,
            modifier = Modifier.testTag("settings_sponsor_categories"),
            cardListPosition = CardListPosition.Middle,
            onClick = onOpenCategories,
        )
        SwitchPreference(
            checked = sponsorBadgeEnabled,
            onCheckedChange = onSponsorBadgeChange,
            title = "详情页标记商单",
            summary = "用空降助手识别商单广告，在详情页 BV 号旁标注「商单」或「疑似商单」",
            modifier = Modifier.testTag("settings_sponsor_badge"),
            cardListPosition = CardListPosition.Middle,
        )
        SwitchPreference(
            checked = promotionBadgeEnabled,
            onCheckedChange = onPromotionBadgeChange,
            title = "详情页标记推广",
            summary = "在视频详情页的标题前标注「推广」；关闭后推荐流会过滤掉推广卡片",
            modifier = Modifier.testTag("settings_promotion_badge"),
            cardListPosition = CardListPosition.Tail,
        )
    }
}
