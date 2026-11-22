package erl.bilico.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import erl.bilico.data.settings.AutoNextPreferences
import erl.bilico.feature.video.rememberDraftIntSliderState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference

/** 设置页「自动连播」卡片：推荐视频弹窗开关与 3~10 秒倒计时滑条。 */
@Composable
internal fun AutoNextSettingsCard(
    recommendPopup: Boolean,
    countdownSeconds: Int,
    onRecommendPopupChange: (Boolean) -> Unit,
    onCountdownSecondsChange: (Int) -> Unit,
) {
    val sliderState = rememberDraftIntSliderState(
        countdownSeconds.coerceIn(
            AutoNextPreferences.MIN_COUNTDOWN_SECONDS,
            AutoNextPreferences.MAX_COUNTDOWN_SECONDS,
        ),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        SwitchPreference(
            checked = recommendPopup,
            onCheckedChange = onRecommendPopupChange,
            title = "推荐视频连播弹窗",
            summary = "自动连播且非分批播放时，在右下角弹出下一个推荐视频",
            modifier = Modifier.testTag("settings_auto_next_popup"),
            cardListPosition = CardListPosition.Head,
        )
        SliderPreference(
            value = sliderState.draftValue,
            onValueChange = { sliderState.draftValue = it },
            onValueChangeFinished = { sliderState.commit(onCountdownSecondsChange) },
            title = "连播倒计时",
            summary = "倒计时结束后自动播放下一个推荐视频",
            valueText = "${sliderState.roundedInt} 秒",
            enabled = recommendPopup,
            valueRange = AutoNextPreferences.MIN_COUNTDOWN_SECONDS.toFloat()..
                AutoNextPreferences.MAX_COUNTDOWN_SECONDS.toFloat(),
            steps = AutoNextPreferences.MAX_COUNTDOWN_SECONDS -
                AutoNextPreferences.MIN_COUNTDOWN_SECONDS - 1,
            modifier = Modifier.testTag("settings_auto_next_countdown"),
            cardListPosition = CardListPosition.Tail,
        )
    }
}
