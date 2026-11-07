package erl.bilico.feature.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import erl.bilico.data.model.ArticleBlock
import erl.bilico.data.model.ArticleDetail
import erl.bilico.data.model.ArticleSpan
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.CommentTab
import erl.bilico.feature.dynamic.DynamicImageViewer
import erl.bilico.ui.NavState
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 专栏详情。入口目前有两处：搜索结果与动态里的专栏卡片。 */
@Composable
fun ArticleScreen(
    container: AppContainer,
    nav: NavState,
    cvId: Long,
    initialTitle: String,
) {
    val viewModel: ArticleViewModel = viewModel(key = "article-$cvId") { ArticleViewModel() }
    LaunchedEffect(cvId) {
        viewModel.load(cvId) { container.articles.article(cvId) }
    }
    ArticlePage(
        container = container,
        nav = nav,
        viewModel = viewModel,
        initialTitle = initialTitle,
        reload = { viewModel.load(cvId) { container.articles.article(cvId) } },
    )
}

/** 图文动态 / 动态详情。dynamicId 是 t.bilibili.com 后那段数字。 */
@Composable
fun DynamicArticleScreen(
    container: AppContainer,
    nav: NavState,
    dynamicId: String,
    initialTitle: String,
) {
    val viewModel: ArticleViewModel = viewModel(key = "dynamic-article-$dynamicId") { ArticleViewModel() }
    LaunchedEffect(dynamicId) {
        viewModel.load(dynamicId) { container.articles.dynamic(dynamicId) }
    }
    ArticlePage(
        container = container,
        nav = nav,
        viewModel = viewModel,
        initialTitle = initialTitle,
        reload = { viewModel.load(dynamicId) { container.articles.dynamic(dynamicId) } },
    )
}

@Composable
private fun ArticlePage(
    container: AppContainer,
    nav: NavState,
    viewModel: ArticleViewModel,
    initialTitle: String,
    reload: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showComments by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }

    SecondaryPage(
        title = initialTitle.ifBlank { "阅读" },
        onBack = nav::pop,
        actions = {
            state.article?.let { article ->
                if (article.commentOid > 0L) {
                    TextButton(
                        text = "评论",
                        onClick = { showComments = true },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        },
    ) {
        when {
            state.isLoading -> item(key = "loading") { LoadingState() }
            state.error != null -> item(key = "error") {
                ErrorState(state.error.orEmpty(), onRetry = { reload() })
            }

            state.article == null -> item(key = "empty") {
                ErrorState("内容为空", onRetry = { reload() })
            }

            else -> articleContent(
                article = state.article!!,
                onOpenUser = { mid -> nav.push(erl.bilico.ui.Route.Space(mid)) },
                onOpenComments = { showComments = true },
                onOpenImage = { images, index -> viewer = images to index },
            )
        }
    }

    state.article?.let { article ->
        viewer?.let { (images, index) ->
            DynamicImageViewer(images = images, initialIndex = index, onDismiss = { viewer = null })
        }
        if (showComments && article.commentOid > 0L) {
            ArticleCommentSheet(
                container = container,
                nav = nav,
                oid = article.commentOid,
                type = if (article.commentType > 0) article.commentType else 12,
                title = article.title,
                commentCount = article.stats?.reply ?: 0L,
                onDismiss = { showComments = false },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.articleContent(
    article: ArticleDetail,
    onOpenUser: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
) {
    if (article.banner.isNotBlank()) {
        item(key = "banner") {
            BannerImage(
                url = article.banner,
                title = article.title,
                onClick = { onOpenImage(listOf(DynamicImage(article.banner, 0, 0)), 0) },
            )
        }
    }
    item(key = "header") { ArticleHeader(article, onOpenUser) }
    article.blocks.forEachIndexed { index, block ->
        item(key = "block-$index") {
            ArticleBlockView(block = block, article = article, onOpenImage = onOpenImage)
        }
    }
    item(key = "footer") { ArticleFooter(article, onOpenComments) }
}

@Composable
private fun BannerImage(
    url: String,
    title: String,
    onClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val widthPx = remember(density, screenWidthDp) {
        with(density) { screenWidthDp.dp.roundToPx() }
    }
    AsyncImage(
        model = Formatters.widthImageUrl(url, widthPx),
        contentDescription = title,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
private fun ArticleHeader(article: ArticleDetail, onOpenUser: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (article.title.isNotBlank()) {
            Text(
                text = article.title,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = COUITheme.colorScheme.onSurface,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                url = if (article.authorFace.isBlank()) null else article.authorFace,
                size = 36.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = article.authorMid > 0L) { onOpenUser(article.authorMid) },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = article.authorName.ifBlank { "未知作者" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val date = Formatters.formatTimeAgo(article.publishTimeSec)
                if (date.isNotBlank()) {
                    Text(
                        text = date,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
        if (article.summary.isNotBlank() && article.summary != article.title && !article.summaryRepeatsBody()) {
            Text(
                text = article.summary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun ArticleBlockView(
    block: ArticleBlock,
    article: ArticleDetail,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = COUITheme.colorScheme.primary
    val textColor = COUITheme.colorScheme.onSurface
    val secondaryColor = COUITheme.colorScheme.onSurfaceSecondary
    val onOpenLink: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }

    when (block) {
        is ArticleBlock.Heading -> {
            val rich = rememberArticleRichText(
                spans = block.spans,
                baseFontSize = 18,
                defaultColor = textColor,
                linkColor = linkColor,
                onOpenLink = onOpenLink,
            )
            Text(
                text = rich.text,
                inlineContent = rich.inlineContent,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        is ArticleBlock.Paragraph -> {
            val rich = rememberArticleRichText(
                spans = block.spans,
                baseFontSize = 16,
                defaultColor = textColor,
                linkColor = linkColor,
                onOpenLink = onOpenLink,
            )
            Text(
                text = rich.text,
                inlineContent = rich.inlineContent,
                fontSize = 16.sp,
                lineHeight = 27.sp,
                color = textColor,
            )
        }

        is ArticleBlock.Quote -> {
            val rich = rememberArticleRichText(
                spans = block.spans,
                baseFontSize = 16,
                defaultColor = secondaryColor,
                linkColor = linkColor,
                onOpenLink = onOpenLink,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(COUITheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = rich.text,
                    inlineContent = rich.inlineContent,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = secondaryColor,
                )
            }
        }

        is ArticleBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, spans ->
                val rich = rememberArticleRichText(
                    spans = spans,
                    baseFontSize = 16,
                    defaultColor = textColor,
                    linkColor = linkColor,
                    onOpenLink = onOpenLink,
                )
                Row {
                    Text(
                        text = if (block.ordered) "${index + 1}." else "•",
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = secondaryColor,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        text = rich.text,
                        inlineContent = rich.inlineContent,
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        is ArticleBlock.Code -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(COUITheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp),
        ) {
            Text(
                text = block.content,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace,
                color = textColor,
            )
        }

        is ArticleBlock.Image -> ArticleImageBlock(block, article, onOpenImage)

        is ArticleBlock.LinkCard -> LinkCardView(block)

        ArticleBlock.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = COUITheme.colorScheme.dividerLine,
        )
    }
}

@Composable
private fun ArticleImageBlock(
    block: ArticleBlock.Image,
    article: ArticleDetail,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val widthPx = remember(density, screenWidthDp) {
        with(density) { (screenWidthDp.dp - 24.dp).roundToPx() }
    }
    val url = remember(block.url, widthPx) { Formatters.widthImageUrl(block.url, widthPx) }
    val ratio = articleImageAspectRatio(block.width, block.height)
    // 组内图片索引按图块出现顺序算，点开时能落到正确的那一张。
    val group = remember(article.blocks) {
        article.blocks.filterIsInstance<ArticleBlock.Image>()
            .map { DynamicImage(it.url, it.width, it.height) }
    }
    val index = remember(article.blocks, block.url) { group.indexOfFirst { it.url == block.url } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpenImage(group, index.coerceAtLeast(0)) },
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (ratio != null) Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = false) else Modifier)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.FillWidth,
        )
    }
}

@Composable
private fun LinkCardView(block: ArticleBlock.LinkCard) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(COUITheme.colorScheme.surfaceContainerHigh)
            .clickable {
                if (block.card.url.isNotBlank()) runCatching { uriHandler.openUri(block.card.url) }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (block.card.cover.isNotBlank()) {
            AsyncImage(
                model = Formatters.sizedImageUrl(block.card.cover, 160, 100),
                contentDescription = null,
                modifier = Modifier
                    .width(80.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = block.card.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (block.card.description.isNotBlank()) {
                Text(
                    text = block.card.description,
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArticleFooter(article: ArticleDetail, onOpenComments: () -> Unit) {
    val stats = article.stats
    val replyCount = stats?.reply ?: 0L
    val parts = buildList {
        stats?.view?.takeIf { it > 0 }?.let { add("${Formatters.formatStat(it)} 阅读") }
        stats?.like?.takeIf { it > 0 }?.let { add("${Formatters.formatStat(it)} 点赞") }
        stats?.reply?.takeIf { it > 0 }?.let { add("${Formatters.formatStat(it)} 评论") }
        stats?.share?.takeIf { it > 0 }?.let { add("${Formatters.formatStat(it)} 分享") }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString(" · "),
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
        HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
        if (article.commentOid > 0L) {
            Text(
                text = if (replyCount > 0L) {
                    "查看全部 ${Formatters.formatStat(replyCount)} 条评论"
                } else {
                    "查看评论"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenComments)
                    .padding(vertical = 10.dp),
                fontSize = 14.sp,
                color = COUITheme.colorScheme.primary,
            )
        }
    }
}

/** 评论单独开一屏：正文页是 LazyColumn，评论也是，嵌套滚动会互相抢手势。 */
@Composable
private fun ArticleCommentSheet(
    container: AppContainer,
    nav: NavState,
    oid: Long,
    type: Int,
    title: String,
    commentCount: Long,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(pageContainerColor()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackIconButton(onClick = onDismiss)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = "评论",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = COUITheme.colorScheme.onSurface,
                    )
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (commentCount > 0L) {
                    Text(
                        text = Formatters.formatStat(commentCount),
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
            HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
            CommentTab(
                container = container,
                oid = oid,
                type = type,
                onOpenUser = { mid -> nav.push(erl.bilico.ui.Route.Space(mid)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        }
    }
}
