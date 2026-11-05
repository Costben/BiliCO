package erl.bilico.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import erl.bilico.BuildConfig
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    nav: NavState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val quality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()

    var pickQuality by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableIntStateOf(0) }

    SecondaryPage(title = "设置", onBack = nav::pop) {
        item(key = "display-title") { SectionTitle("显示与播放") }

        item(key = "display") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "深色模式",
                    modifier = Modifier.testTag("settings_dark"),
                    summary = when (darkMode) {
                        1 -> "浅色"
                        2 -> "深色"
                        else -> "跟随系统"
                    },
                    cardListPosition = CardListPosition.Head,
                    onClick = { container.settings.setDarkMode((darkMode + 1) % 3) },
                )
                ArrowPreference(
                    title = "默认清晰度",
                    modifier = Modifier.testTag("settings_quality"),
                    summary = DefaultQuality.entries
                        .firstOrNull { it.id == quality }
                        ?.label
                        ?: "跟随接口",
                    cardListPosition = CardListPosition.Tail,
                    onClick = { pickQuality = true },
                )
            }
        }

        item(key = "storage-title") { SectionTitle("存储") }

        item(key = "storage") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "清除图片缓存",
                    modifier = Modifier.testTag("settings_cache"),
                    summary = if (cacheCleared > 0) "已清除" else "只清本地图片，不影响账号",
                    cardListPosition = CardListPosition.None,
                    onClick = {
                        scope.launch {
                            val loader = SingletonImageLoader.get(context)
                            loader.diskCache?.clear()
                            loader.memoryCache?.clear()
                            cacheCleared++
                        }
                    },
                )
            }
        }

        item(key = "about-title") { SectionTitle("关于") }

        item(key = "about") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "关于 BiliCO",
                    modifier = Modifier.testTag("settings_about"),
                    summary = "v${BuildConfig.VERSION_NAME}",
                    cardListPosition = CardListPosition.None,
                    onClick = { showAbout = true },
                )
            }
        }

        if (account != null) {
            item(key = "account-title") { SectionTitle("账号") }

            item(key = "account") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "退出登录",
                        modifier = Modifier.testTag("settings_logout"),
                        summary = account?.name,
                        cardListPosition = CardListPosition.None,
                        onClick = { confirmLogout = true },
                    )
                }
            }
        }
    }

    if (pickQuality) {
        OptionPicker(
            title = "默认清晰度",
            options = DefaultQuality.entries.map { it.label },
            selectedIndex = DefaultQuality.entries.indexOfFirst { it.id == quality }
                .coerceAtLeast(0),
            onPick = { index ->
                container.settings.setDefaultQuality(DefaultQuality.entries[index].id)
                pickQuality = false
            },
            onDismiss = { pickQuality = false },
        )
    }

    if (showAbout) {
        ConfirmDialog(
            title = "关于 BiliCO",
            message = "BiliCO v${BuildConfig.VERSION_NAME}\n\n第三方哔哩哔哩客户端，仅用于个人学习与研究。",
            confirmText = "知道了",
            onDismiss = { showAbout = false },
            onConfirm = { showAbout = false },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            message = "会清除本地保存的 Cookie，下次需要重新扫码登录。",
            confirmText = "退出",
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                container.scope.launch { container.session.logout() }
            },
        )
    }
}

/** 单选弹窗：和 Cobo 的设置项同一套组件，胶囊高亮由 ListPopupColumn 自己铺。 */
@Composable
private fun OptionPicker(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = title,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val picked = result
                if (picked != null) onPick(picked) else onDismiss()
            }
        },
    ) {
        Column {
            Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) {
                ListPopupColumn {
                    options.forEachIndexed { index, label ->
                        DropdownImpl(
                            item = DropdownItem(text = label),
                            optionSize = options.size,
                            isSelected = index == selectedIndex,
                            index = index,
                            groupKey = "settings-option",
                            onSelectedIndexChange = {
                                result = index
                                show = false
                            },
                        )
                    }
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = null,
            )
        }
    }
}
