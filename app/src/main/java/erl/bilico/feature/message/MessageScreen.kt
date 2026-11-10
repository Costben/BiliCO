package erl.bilico.feature.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.chip.ChipTabButton
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.effect.CouiFloatingRenderer
import erl.bilico.data.model.MessageEntry
import erl.bilico.data.model.MessageKind
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.LikeOutlineIcon
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.AtSignIcon
import erl.bilico.ui.components.BellOutlineIcon
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun MessageScreen(
    container: AppContainer,
    nav: NavState,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val viewModel: MessageViewModel = viewModel {
        MessageViewModel(container.messages, container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { MESSAGE_TABS.size }
    val scope = rememberCoroutineScope()
    val pageColor = pageContainerColor()
    val layerBackdrop = rememberLayerBackdrop()
    val couiBackdrop = rememberCouiBackdrop()
    val renderers = remember(MESSAGE_TABS.size) { List(MESSAGE_TABS.size) { CouiFloatingRenderer.standalone() } }
    val leadingRenderer = remember { CouiFloatingRenderer.standalone() }

    var menuExpanded by remember { mutableStateOf(false) }

    val chipTabItems = remember(MESSAGE_TABS) {
        MESSAGE_TABS.mapIndexed { index, tab ->
            CouiChipTabItem(
                key = index,
                label = tab.label,
                icon = tab.iconVector?.let { vector ->
                    {
                        Icon(
                            imageVector = vector,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        }
    }

    // 滑停一页就补拉这一类；点标签走的是同一个入口。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.ensureLoaded(MESSAGE_TABS[it].kind) }
    }
    LaunchedEffect(account) {
        if (account != null) viewModel.ensureLoaded(MESSAGE_TABS[pagerState.settledPage].kind)
    }

    val density = LocalDensity.current
    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    // 顶栏加分类栏的总高度；列表从它下面起排，静止时第一条不会被分类栏压住。
    var headerHeight by remember { mutableStateOf(HEADER_HEIGHT_FALLBACK) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageColor),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, layerBackdrop)
                .couiBackdrop(couiBackdrop)
                .background(pageColor),
            beyondViewportPageCount = 1,
        ) { index ->
            val kind = MESSAGE_TABS[index].kind
            MessageList(
                kind = kind,
                state = state.of(kind),
                loggedIn = account != null,
                contentTop = headerHeight + 8.dp,
                viewModel = viewModel,
                nav = nav,
            )
        }

        // 顶栏通过 bottomContent 承载分类页签栏，整块区域在同一个渐变毛玻璃材质下，
        // 从状态栏一直连贯模糊覆盖到分类栏下方，消除顶栏与分类栏之间的断层。
        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) layerBackdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = "消息",
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onSizeChanged { size ->
                            with(density) { headerHeight = size.height.toDp() }
                        },
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = BlurEffectsEnabled,
                    gradientGlassExtraHeight = 16.dp,
                    navigationIcon = { BackIconButton(onClick = nav::pop) },
                    bottomContent = {
                        ChipTabRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            items = chipTabItems,
                            selectedKey = pagerState.currentPage,
                            onSelectedKeyChange = { key ->
                                scope.launch { pagerState.animateScrollToPage(key as Int) }
                            },
                            backdrop = couiBackdrop,
                            dark = dark,
                            renderers = renderers,
                            leadingAction = {
                                Box {
                                    ChipTabButton(
                                        onClick = { menuExpanded = true },
                                        contentDescription = "全部分类",
                                        backdrop = couiBackdrop,
                                        dark = dark,
                                        contourGlowEnabled = true,
                                        renderer = leadingRenderer,
                                        icon = {
                                            Icon(
                                                imageVector = COUIIcons.GridView,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                    )
                                    CouiPopupMenu(
                                        show = menuExpanded,
                                        onDismiss = { menuExpanded = false },
                                        items = MESSAGE_TABS.mapIndexed { index, tab ->
                                            CouiMenuItem(
                                                label = tab.label,
                                                selected = pagerState.currentPage == index,
                                                icon = tab.iconVector?.let { vector ->
                                                    { modifier ->
                                                        Icon(
                                                            imageVector = vector,
                                                            contentDescription = null,
                                                            modifier = modifier,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    scope.launch { pagerState.animateScrollToPage(index) }
                                                },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}

/** 首次测量前的内容起点估算：状态栏 + 标题行 + 分类栏。 */
private val HEADER_HEIGHT_FALLBACK = 116.dp

@Composable
private fun MessageList(
    kind: MessageKind,
    state: MessageKindState,
    loggedIn: Boolean,
    contentTop: Dp,
    viewModel: MessageViewModel,
    nav: NavState,
) {
    val listState = rememberLazyListState()
    val itemCount by rememberUpdatedState(state.items.size)

    LaunchedEffect(kind, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= 0 && itemCount > 0 && lastVisible >= itemCount - 3) {
                    viewModel.loadMore(kind)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = contentTop,
            end = 12.dp,
            bottom = 24.dp + navBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!loggedIn) {
            item(key = "login") {
                EmptyState(
                    text = "登录后查看",
                    action = { TextButton(text = "去登录", onClick = nav::popToMe) },
                )
            }
            return@LazyColumn
        }

        when {
            state.isLoading -> item(key = "state") { LoadingState() }
            state.error != null -> item(key = "state") {
                ErrorState(state.error.orEmpty(), onRetry = { viewModel.reload(kind) })
            }
            state.loaded && state.items.isEmpty() -> item(key = "state") {
                EmptyState("暂时没有新消息")
            }
        }

        items(
            count = state.items.size,
            key = { state.items[it].key },
            contentType = { "message" },
        ) { index ->
            MessageRow(entry = state.items[index])
        }

        if (state.isLoadingMore) {
            item(key = "loading-more") { LoadingState() }
        }
    }
}

@Composable
private fun MessageRow(entry: MessageEntry) {
    val avatarUrl = remember(entry.avatar) { Formatters.sizedImageUrl(entry.avatar, 132, 132) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row {
            Avatar(url = avatarUrl, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name.ifBlank { "未知用户" },
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.timeText,
                        fontSize = 11.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
                if (entry.title.isNotBlank()) {
                    Text(
                        text = entry.title,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.content.isNotBlank()) {
                    Text(
                        text = entry.content,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 13.sp,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.extra.isNotBlank()) {
                    Text(
                        text = entry.extra,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 11.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}

private data class MessageTabItem(
    val kind: MessageKind,
    val label: String,
    val iconVector: ImageVector? = null,
)

private val MESSAGE_TABS = listOf(
    MessageTabItem(MessageKind.REPLY, "回复我的", COUIIcons.Reply),
    MessageTabItem(MessageKind.AT, "提及我的", AtSignIcon),
    MessageTabItem(MessageKind.LIKE, "收到的赞", LikeOutlineIcon),
    MessageTabItem(MessageKind.SYSTEM, "系统消息", BellOutlineIcon),
)
