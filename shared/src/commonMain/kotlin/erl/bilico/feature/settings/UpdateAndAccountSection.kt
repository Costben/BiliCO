package erl.bilico.feature.settings

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import erl.bilico.data.model.Account
import erl.bilico.data.session.StoredSession

internal fun updateSettingsEntries(
    versionName: String,
    checkingUpdate: Boolean,
    autoCheckUpdate: Boolean,
    acceptPrerelease: Boolean,
    inAppUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onOpenChangelog: () -> Unit,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    onAcceptPrereleaseChange: (Boolean) -> Unit,
    onInAppUpdateChange: (Boolean) -> Unit,
): List<SettingsEntry> {
    val updateSummary = if (checkingUpdate) "正在检查…" else "当前版本 v$versionName"
    return listOf(
        SettingsEntry(
            id = "update_check",
            title = "检查更新",
            summary = updateSummary,
            keywords = "新版本 升级 版本号",
        ) { pos ->
            SettingsArrowRow(
                title = "检查更新",
                modifier = Modifier.testTag("settings_update"),
                summary = updateSummary,
                cardListPosition = pos,
                onClick = onCheckUpdate,
            )
        },
        SettingsEntry(
            id = "update_changelog",
            title = "更新日志",
            summary = "检查更新页面 · 历史版本与发布说明",
            keywords = "版本记录 发布说明 changelog",
        ) { pos ->
            SettingsArrowRow(
                title = "更新日志",
                modifier = Modifier.testTag("settings_changelog"),
                summary = "检查更新页面 · 历史版本与发布说明",
                cardListPosition = pos,
                onClick = onOpenChangelog,
            )
        },
        SettingsEntry(
            id = "auto_update",
            title = "自动检查更新",
            keywords = "启动 检查 版本",
        ) { pos ->
            SettingsSwitchRow(
                checked = autoCheckUpdate,
                onCheckedChange = onAutoCheckUpdateChange,
                title = "自动检查更新",
                modifier = Modifier.testTag("settings_auto_update"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "prerelease",
            title = "接收预发布版本",
            keywords = "测试版 beta pre 内测",
        ) { pos ->
            SettingsSwitchRow(
                checked = acceptPrerelease,
                onCheckedChange = onAcceptPrereleaseChange,
                title = "接收预发布版本",
                modifier = Modifier.testTag("settings_prerelease"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "in_app_update",
            title = "应用内更新",
            keywords = "下载 安装 APK",
        ) { pos ->
            SettingsSwitchRow(
                checked = inAppUpdate,
                onCheckedChange = onInAppUpdateChange,
                title = "应用内更新",
                modifier = Modifier.testTag("settings_in_app_update"),
                cardListPosition = pos,
            )
        },
    )
}

internal fun accountSettingsEntries(
    account: Account?,
    sessions: List<StoredSession>,
    onSwitchAccount: () -> Unit,
    onLogout: () -> Unit,
): List<SettingsEntry> = buildList {
    // 名册里还有账号时留一个切换入口：设置页也能直接换号，不必绕回「我的」。
    if (sessions.isNotEmpty()) {
        val summary = "已保存 ${sessions.size} 个账号"
        add(
            SettingsEntry(
                id = "switch_account",
                title = "切换账号",
                summary = summary,
                keywords = "多账号 换号 登录",
            ) { pos ->
                SettingsArrowRow(
                    title = "切换账号",
                    modifier = Modifier.testTag("settings_switch_account"),
                    summary = summary,
                    cardListPosition = pos,
                    onClick = onSwitchAccount,
                )
            },
        )
    }
    if (account != null) {
        add(
            SettingsEntry(
                id = "logout",
                title = "退出登录",
                summary = account.name,
                keywords = "注销 登出 账号",
            ) { pos ->
                SettingsArrowRow(
                    title = "退出登录",
                    modifier = Modifier.testTag("settings_logout"),
                    summary = account.name,
                    cardListPosition = pos,
                    onClick = onLogout,
                )
            },
        )
    }
}
