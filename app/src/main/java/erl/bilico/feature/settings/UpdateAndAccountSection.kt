package erl.bilico.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import erl.bilico.BuildConfig
import erl.bilico.data.model.Account
import erl.bilico.data.session.StoredSession
import erl.bilico.ui.components.PreferenceDivider
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference

@Composable
internal fun UpdateSettingsCard(
    checkingUpdate: Boolean,
    autoCheckUpdate: Boolean,
    acceptPrerelease: Boolean,
    inAppUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onOpenChangelog: () -> Unit,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    onAcceptPrereleaseChange: (Boolean) -> Unit,
    onInAppUpdateChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        ArrowPreference(
            title = "检查更新",
            modifier = Modifier.testTag("settings_update"),
            summary = if (checkingUpdate) "正在检查…" else "当前版本 v${BuildConfig.VERSION_NAME}",
            cardListPosition = CardListPosition.Head,
            onClick = onCheckUpdate,
        )
        PreferenceDivider()
        ArrowPreference(
            title = "更新日志",
            modifier = Modifier.testTag("settings_changelog"),
            summary = "检查更新页面 · 历史版本与发布说明",
            cardListPosition = CardListPosition.Middle,
            onClick = onOpenChangelog,
        )
        PreferenceDivider()
        SwitchPreference(
            checked = autoCheckUpdate,
            onCheckedChange = onAutoCheckUpdateChange,
            title = "自动检查更新",
            modifier = Modifier.testTag("settings_auto_update"),
            cardListPosition = CardListPosition.Middle,
        )
        PreferenceDivider()
        SwitchPreference(
            checked = acceptPrerelease,
            onCheckedChange = onAcceptPrereleaseChange,
            title = "接收预发布版本",
            modifier = Modifier.testTag("settings_prerelease"),
            cardListPosition = CardListPosition.Middle,
        )
        PreferenceDivider()
        SwitchPreference(
            checked = inAppUpdate,
            onCheckedChange = onInAppUpdateChange,
            title = "应用内更新",
            modifier = Modifier.testTag("settings_in_app_update"),
            cardListPosition = CardListPosition.Tail,
        )
    }
}

@Composable
internal fun AccountSettingsCard(
    account: Account?,
    sessions: List<StoredSession>,
    onSwitchAccount: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        // 名册里还有账号时留一个切换入口：设置页也能直接换号，不必绕回「我的」。
        if (sessions.isNotEmpty()) {
            ArrowPreference(
                title = "切换账号",
                modifier = Modifier.testTag("settings_switch_account"),
                summary = "已保存 ${sessions.size} 个账号",
                cardListPosition = if (account != null) CardListPosition.Head else CardListPosition.None,
                onClick = onSwitchAccount,
            )
        }
        if (account != null) {
            ArrowPreference(
                title = "退出登录",
                modifier = Modifier.testTag("settings_logout"),
                summary = account.name,
                cardListPosition = if (sessions.isNotEmpty()) {
                    CardListPosition.Tail
                } else {
                    CardListPosition.None
                },
                onClick = onLogout,
            )
        }
    }
}
