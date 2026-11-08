package erl.bilico.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.PreferenceDivider
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import erl.bilico.update.AppUpdater
import erl.bilico.update.UpdateInfo
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 检查更新与更新日志页面（对齐 Cobo ChangelogScreen + c001apk 更新设置）：
 * - 顶部状态卡：展示当前版本、更新通道、上次检查时间，并支持一键立即检查更新
 * - 更新策略卡：自动检查更新、接收预发布版本、应用内更新开关
 * - 历史版本列表：按版本从新到旧展示 GitHub Releases，任意版本均可预览更新弹窗或直接下载安装
 */
@Composable
fun UpdateScreen(
    container: AppContainer,
    nav: NavState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { AppUpdater.currentVersion(context) }

    val autoCheckUpdate by container.settings.autoCheckUpdate.collectAsStateWithLifecycle()
    val acceptPrerelease by container.settings.acceptPrerelease.collectAsStateWithLifecycle()
    val inAppUpdate by container.settings.inAppUpdate.collectAsStateWithLifecycle()
    val updateCheckedAt by container.settings.updateCheckedAt.collectAsStateWithLifecycle()
    val skippedVersion by container.settings.skippedUpdateVersion.collectAsStateWithLifecycle()

    var releases by remember { mutableStateOf<List<UpdateInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload, acceptPrerelease) {
        error = null
        runCatching { AppUpdater.releases(includePrerelease = acceptPrerelease) }
            .onSuccess { releases = it }
            .onFailure {
                releases = null
                error = it.message ?: "获取更新日志失败"
            }
    }

    val latestRelease = releases?.firstOrNull()
    val hasNewerRelease = latestRelease != null &&
        AppUpdater.compareVersion(latestRelease.version, current) > 0

    SecondaryPage(
        title = "检查更新",
        onBack = nav::pop,
        actions = {
            HighlightedIconButton(
                onClick = { reload++ },
                modifier = Modifier.couiTopAppBarActionItem().testTag("update_reload"),
            ) {
                Icon(COUIIcons.Refresh, "刷新", Modifier.size(24.dp))
            }
        },
    ) {
        item(key = "status-card") {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("update_status_card"),
                insideMargin = PaddingValues(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "BiliCO v$current",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Tag(if (current.contains('-')) "预发布" else "正式版")
                        }
                        Spacer(Modifier.height(4.dp))
                        val statusLine = when {
                            container.updater.checkingUpdate -> "正在连接 GitHub 检查最新版本…"
                            hasNewerRelease -> "发现新版本 v${latestRelease.version}"
                            releases != null -> "当前已是最新版本"
                            else -> "点击右侧按钮检查最新版本"
                        }
                        Text(
                            text = statusLine,
                            fontSize = 13.sp,
                            color = if (hasNewerRelease) {
                                COUITheme.colorScheme.primary
                            } else {
                                COUITheme.colorScheme.onSurfaceSecondary
                            },
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatCheckedTime(updateCheckedAt),
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        text = if (container.updater.checkingUpdate) "检查中…" else "检查更新",
                        enabled = !container.updater.checkingUpdate,
                        onClick = {
                            scope.launch {
                                container.updater.checkUpdate(manual = true)
                                reload++
                            }
                        },
                        modifier = Modifier.testTag("update_check_btn"),
                    )
                }
            }
        }

        item(key = "update-options-title") { SectionTitle("更新选项") }

        item(key = "update-options") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                SwitchPreference(
                    title = "自动检查更新",
                    summary = "每次启动时自动检查最新版本（12 小时节流）",
                    checked = autoCheckUpdate,
                    onCheckedChange = container.settings::setAutoCheckUpdate,
                    modifier = Modifier.testTag("update_auto_switch"),
                    cardListPosition = CardListPosition.Head,
                )
                PreferenceDivider()
                SwitchPreference(
                    title = "接收预发布版本",
                    summary = "包含 pre / beta 预发布更新；关闭时只接收正式版",
                    checked = acceptPrerelease,
                    onCheckedChange = container.settings::setAcceptPrerelease,
                    modifier = Modifier.testTag("update_prerelease_switch"),
                    cardListPosition = CardListPosition.Middle,
                )
                PreferenceDivider()
                SwitchPreference(
                    title = "应用内更新",
                    summary = "开启后在应用内多镜像加速下载 APK 并调起安装器；关闭时跳转浏览器下载",
                    checked = inAppUpdate,
                    onCheckedChange = container.settings::setInAppUpdate,
                    modifier = Modifier.testTag("update_in_app_switch"),
                    cardListPosition = CardListPosition.Tail,
                )
            }
        }

        item(key = "changelog-title") { SectionTitle("版本历史与更新日志") }

        val list = releases
        when {
            error != null -> item(key = "error") {
                ErrorState(error!!) { reload++ }
            }

            list == null -> item(key = "loading") {
                LoadingState()
            }

            list.isEmpty() -> item(key = "empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = if (acceptPrerelease) {
                            "暂无已发布的版本记录"
                        } else {
                            "暂无正式版发布记录（可开启上方「接收预发布版本」查看预发布版）"
                        },
                        fontSize = 13.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }

            else -> items(list, key = { it.version }) { release ->
                ReleaseCard(
                    release = release,
                    isCurrent = release.version == current,
                    isSkipped = release.version == skippedVersion,
                    onPreview = { container.updater.previewUpdate(release) },
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: UpdateInfo,
    isCurrent: Boolean,
    isSkipped: Boolean,
    onPreview: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("changelog_${release.version}"),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "v${release.version}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (isCurrent) Tag("当前版本")
                if (release.prerelease) Tag("预发布") else Tag("正式版")
                if (isSkipped && !isCurrent) Tag("已跳过")
                Spacer(Modifier.weight(1f))
                Text(
                    release.publishedAt.take(10),
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                MarkdownText(release.notes)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = if (isCurrent) "预览更新弹窗" else "查看更新弹窗 / 安装",
                    onClick = onPreview,
                    modifier = Modifier.testTag("changelog_preview_${release.version}"),
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = COUITheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp),
    )
}

private fun formatCheckedTime(epochMs: Long): String {
    if (epochMs <= 0L) return "上次检查：尚未检查"
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return "上次检查：${fmt.format(Date(epochMs))}"
}
