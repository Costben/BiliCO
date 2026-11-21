package erl.bilico.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.repository.BlockKeywordScope
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.KeywordBlockDialog
import erl.bilico.ui.components.PageTabRow
import erl.bilico.ui.components.PreferenceDivider
import erl.bilico.ui.components.RegexHelpButton
import erl.bilico.ui.components.RegexHelpDialog
import erl.bilico.ui.components.SecondaryPagerPage
import erl.bilico.ui.components.SectionTitle
import erl.bilico.ui.components.SingleInputDialog
import erl.bilico.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

private val BLOCK_MANAGER_TABS = listOf("关键词", "UP 主", "视频", "过滤规则")

private val DURATION_OPTIONS = listOf(0, 15, 30, 60, 120, 300)
private val PLAY_OPTIONS = listOf(0, 1000, 5000, 10000, 50000, 100000)
private val LIKE_RATIO_OPTIONS = listOf(0, 1, 2, 3, 5, 10)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockManagerScreen(
    container: AppContainer,
    nav: NavState,
) {
    val blockState by container.blocks.snapshot.collectAsStateWithLifecycle()
    val loggedIn by container.session.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scheme = COUITheme.colorScheme

    val pagerState = rememberPagerState(pageCount = { BLOCK_MANAGER_TABS.size })
    var keywordDialogScope by remember { mutableStateOf<BlockKeywordScope?>(null) }
    var customThresholdKind by remember { mutableStateOf<String?>(null) }
    var showRegexHelp by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        if (loggedIn != null) {
            container.blocks.syncServerBlacklist()
        }
    }

    SecondaryPagerPage(
        title = "屏蔽管理",
        onBack = nav::pop,
        pagerState = pagerState,
        actions = {
            RegexHelpButton(onClick = { showRegexHelp = true })
        },
        header = {
            PageTabRow(
                tabs = BLOCK_MANAGER_TABS,
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
        },
    ) { pageIndex, contentTop ->
        val listState = rememberLazyListState()
        BindScrollToTop(listState, enabled = pageIndex == pagerState.currentPage)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = contentTop,
                end = 12.dp,
                bottom = 24.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (pageIndex) {
                0 -> {
                    BlockKeywordScope.entries.forEachIndexed { idx, kwScope ->
                        val words = when (kwScope) {
                            BlockKeywordScope.VIDEO_TITLE -> blockState.config.videoKeywords
                            BlockKeywordScope.UP_NAME -> blockState.config.upKeywords
                            BlockKeywordScope.COMMENT -> blockState.config.commentKeywords
                            BlockKeywordScope.DYNAMIC -> blockState.config.dynamicKeywords
                        }
                        item(key = "kw-header-${kwScope.name}") {
                            SectionTitle(
                                text = "${kwScope.label}（${words.size}）",
                                topPadding = if (idx == 0) 4.dp else 16.dp,
                                trailing = {
                                    Text(
                                        text = "+ 添加",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = scheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { keywordDialogScope = kwScope }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                },
                            )
                        }
                        item(key = "kw-body-${kwScope.name}") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = CardDefaults.GroupCornerRadius,
                                insideMargin = PaddingValues(12.dp),
                                weight = CardDefaults.GroupWeight,
                            ) {
                                if (words.isEmpty()) {
                                    Text(
                                        text = "暂无${kwScope.label}屏蔽词，点击右上角添加",
                                        fontSize = 13.sp,
                                        color = scheme.onSurfaceSecondary,
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        words.forEach { word ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(scheme.surfaceContainerHigh)
                                                    .clickable {
                                                        container.blocks.removeKeyword(kwScope, word)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                            ) {
                                                Text(
                                                    text = "$word ×",
                                                    fontSize = 12.sp,
                                                    color = scheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item(key = "up-local-title") {
                        SectionTitle("本地屏蔽 UP 主（${blockState.config.blockedUsers.size}）", topPadding = 4.dp)
                    }
                    if (blockState.config.blockedUsers.isEmpty()) {
                        item(key = "up-local-empty") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = CardDefaults.GroupCornerRadius,
                                insideMargin = PaddingValues(12.dp),
                                weight = CardDefaults.GroupWeight,
                            ) {
                                Text(
                                    text = "暂无本地屏蔽的 UP 主",
                                    fontSize = 13.sp,
                                    color = scheme.onSurfaceSecondary,
                                )
                            }
                        }
                    } else {
                        items(
                            items = blockState.config.blockedUsers,
                            key = { "local-user-${it.mid}" },
                        ) { user ->
                            BlockedUserRow(
                                name = user.name.ifBlank { "uid:${user.mid}" },
                                face = user.face,
                                actionLabel = "取消屏蔽",
                                onOpen = { nav.push(Route.Space(user.mid)) },
                                onAction = { container.blocks.unblockUserLocally(user.mid) },
                            )
                        }
                    }

                    item(key = "up-server-title") {
                        SectionTitle(
                            text = "账号黑名单（${blockState.serverBlackUsers.size}）",
                            trailing = if (loggedIn != null) {
                                {
                                    Text(
                                        text = if (blockState.syncingBlacklist) "同步中..." else "刷新同步",
                                        fontSize = 13.sp,
                                        color = scheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = !blockState.syncingBlacklist) {
                                                scope.launch { container.blocks.syncServerBlacklist() }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    if (loggedIn == null) {
                        item(key = "up-server-login") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = CardDefaults.GroupCornerRadius,
                                insideMargin = PaddingValues(12.dp),
                                weight = CardDefaults.GroupWeight,
                            ) {
                                Text(
                                    text = "登录后可查看并同步 B 站账号黑名单",
                                    fontSize = 13.sp,
                                    color = scheme.onSurfaceSecondary,
                                )
                            }
                        }
                    } else if (blockState.serverBlackUsers.isEmpty()) {
                        item(key = "up-server-empty") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = CardDefaults.GroupCornerRadius,
                                insideMargin = PaddingValues(12.dp),
                                weight = CardDefaults.GroupWeight,
                            ) {
                                Text(
                                    text = "账号黑名单为空",
                                    fontSize = 13.sp,
                                    color = scheme.onSurfaceSecondary,
                                )
                            }
                        }
                    } else {
                        items(
                            items = blockState.serverBlackUsers,
                            key = { "server-user-${it.mid}" },
                        ) { user ->
                            BlockedUserRow(
                                name = user.name.ifBlank { "uid:${user.mid}" },
                                face = user.face,
                                actionLabel = "移出黑名单",
                                onOpen = { nav.push(Route.Space(user.mid)) },
                                onAction = {
                                    scope.launch {
                                        container.blocks.toggleServerBlacklist(user.mid, add = false)
                                            .onFailure {
                                                Toast.makeText(context, it.message ?: "操作失败", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                },
                            )
                        }
                    }
                }

                2 -> {
                    item(key = "videos-title") {
                        SectionTitle("已屏蔽视频（${blockState.config.blockedVideos.size}）", topPadding = 4.dp)
                    }
                    if (blockState.config.blockedVideos.isEmpty()) {
                        item(key = "videos-empty") {
                            EmptyState("暂无已屏蔽的视频")
                        }
                    } else {
                        items(
                            items = blockState.config.blockedVideos,
                            key = { "blocked-video-${it.bvid}" },
                        ) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                cornerRadius = CardDefaults.GroupCornerRadius,
                                insideMargin = PaddingValues(12.dp),
                                weight = CardDefaults.GroupWeight,
                                onClick = { nav.push(Route.Video(item.bvid)) },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = item.title.ifBlank { item.bvid },
                                            style = COUITheme.textStyles.body2,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = buildString {
                                                if (item.upName.isNotBlank()) append(item.upName).append(" · ")
                                                append(item.bvid)
                                            },
                                            fontSize = 12.sp,
                                            color = scheme.onSurfaceSecondary,
                                            maxLines = 1,
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "恢复",
                                        fontSize = 13.sp,
                                        color = scheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { container.blocks.unblockVideo(item.bvid) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    val cfg = blockState.config
                    item(key = "rules-title") {
                        SectionTitle("屏蔽规则与推荐过滤", topPadding = 4.dp)
                    }
                    item(key = "rules-card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = CardDefaults.GroupCornerRadius,
                            insideMargin = PaddingValues(0.dp),
                            weight = CardDefaults.GroupWeight,
                        ) {
                            OverlayDropdownPreference(
                                title = "最短视频时长",
                                items = DURATION_OPTIONS.map { sec ->
                                    if (sec == 0) "不限制" else "${sec} 秒"
                                },
                                selectedIndex = DURATION_OPTIONS.indexOf(cfg.minDurationSec).coerceAtLeast(0),
                                onSelectedIndexChange = { idx ->
                                    container.blocks.updateRecommendThresholds(minDurationSec = DURATION_OPTIONS[idx])
                                },
                                cardListPosition = CardListPosition.Head,
                            )
                            PreferenceDivider()
                            ArrowPreference(
                                title = "自定义最短时长（秒）",
                                summary = if (cfg.minDurationSec == 0) "当前不限制" else "当前：${cfg.minDurationSec} 秒",
                                cardListPosition = CardListPosition.Middle,
                                onClick = { customThresholdKind = "duration" },
                            )
                            PreferenceDivider()
                            OverlayDropdownPreference(
                                title = "最低播放量",
                                items = PLAY_OPTIONS.map { count ->
                                    if (count == 0) "不限制" else "$count"
                                },
                                selectedIndex = PLAY_OPTIONS.indexOf(cfg.minPlayCount).coerceAtLeast(0),
                                onSelectedIndexChange = { idx ->
                                    container.blocks.updateRecommendThresholds(minPlayCount = PLAY_OPTIONS[idx])
                                },
                                cardListPosition = CardListPosition.Middle,
                            )
                            PreferenceDivider()
                            OverlayDropdownPreference(
                                title = "最低点赞率",
                                items = LIKE_RATIO_OPTIONS.map { pct ->
                                    if (pct == 0) "不限制" else "$pct%"
                                },
                                selectedIndex = LIKE_RATIO_OPTIONS.indexOf(cfg.minLikeRatioPercent).coerceAtLeast(0),
                                onSelectedIndexChange = { idx ->
                                    container.blocks.updateRecommendThresholds(minLikeRatioPercent = LIKE_RATIO_OPTIONS[idx])
                                },
                                cardListPosition = CardListPosition.Middle,
                            )
                            PreferenceDivider()
                            SwitchPreference(
                                title = "已关注 UP 主免于过滤",
                                summary = "不屏蔽已关注 UP 主的推荐视频",
                                checked = cfg.exemptFollowed,
                                onCheckedChange = {
                                    container.blocks.updateRecommendThresholds(exemptFollowed = it)
                                },
                                cardListPosition = CardListPosition.Middle,
                            )
                            PreferenceDivider()
                            SwitchPreference(
                                title = "应用于视频详情页相关推荐",
                                checked = cfg.applyToRelatedVideos,
                                onCheckedChange = {
                                    container.blocks.updateRecommendThresholds(applyToRelatedVideos = it)
                                },
                                cardListPosition = CardListPosition.Tail,
                            )
                        }
                    }
                }
            }
        }
    }

    keywordDialogScope?.let { kwScope ->
        KeywordBlockDialog(
            allowedScopes = listOf(kwScope),
            initialScope = kwScope,
            onDismiss = { keywordDialogScope = null },
        )
    }

    if (showRegexHelp) {
        RegexHelpDialog(onDismiss = { showRegexHelp = false })
    }

    customThresholdKind?.let { kind ->
        SingleInputDialog(
            title = "自定义最短时长（秒）",
            hint = "输入整数，0 表示不限制",
            initial = blockState.config.minDurationSec.toString(),
            confirmText = "保存",
            onDismiss = { customThresholdKind = null },
            onConfirm = { value ->
                val sec = value.trim().toIntOrNull()?.coerceAtLeast(0)
                if (sec != null && kind == "duration") {
                    container.blocks.updateRecommendThresholds(minDurationSec = sec)
                }
            },
        )
    }
}

@Composable
private fun BlockedUserRow(
    name: String,
    face: String,
    actionLabel: String,
    onOpen: () -> Unit,
    onAction: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(12.dp),
        weight = CardDefaults.GroupWeight,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(url = face, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                style = COUITheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = actionLabel,
                fontSize = 13.sp,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
