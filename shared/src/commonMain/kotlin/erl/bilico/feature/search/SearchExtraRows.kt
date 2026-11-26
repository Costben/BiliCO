package erl.bilico.feature.search

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import erl.bilico.platform.LocalScreenConfig
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.SearchLive
import erl.bilico.data.model.SearchPgc
import erl.bilico.data.model.SearchUser
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.util.Formatters
import erl.bilico.ui.ExpandOrigin
import erl.bilico.ui.RowScrollState
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.attachToRow
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.expandOrigin
import erl.bilico.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

private val PgcCoverWidth = 90.dp
private val PgcCoverHeight = 120.dp
private val LiveCoverWidth = 144.dp
private val LiveCoverHeight = LiveCoverWidth * 9f / 16f

@Composable
internal fun SearchAllPgcStrip(
    items: List<SearchPgc>,
    onClick: (SearchPgc) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = items,
            key = { "all_pgc_item_${it.seasonId}" },
            contentType = { "search-pgc" },
        ) { pgc ->
            SearchPgcRow(
                pgc = pgc,
                onClick = { onClick(pgc) },
                modifier = Modifier.width(304.dp),
            )
        }
    }
}

@Composable
internal fun SearchPgcRow(
    pgc: SearchPgc,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { PgcCoverWidth.roundToPx() } }
    val coverHeightPx = remember(density) { with(density) { PgcCoverHeight.roundToPx() } }
    val coverUrl = remember(pgc.cover, coverWidthPx, coverHeightPx) {
        Formatters.sizedImageUrl(pgc.cover, coverWidthPx, coverHeightPx)
    }

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(8.dp),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(PgcCoverWidth)
                    .height(PgcCoverHeight)
                    .clip(SquircleShape(8.dp))
                    .background(COUITheme.colorScheme.surfaceContainerHigh),
            ) {
                CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                if (pgc.seasonTypeName.isNotBlank()) {
                    Text(
                        text = pgc.seasonTypeName,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(SquircleShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = PgcCoverHeight)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = pgc.title,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildString {
                        if (pgc.scoreText.isNotBlank()) append(pgc.scoreText)
                        if (pgc.areas.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(pgc.areas)
                        }
                        if (pgc.pubdateText.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(pgc.pubdateText)
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = if (pgc.scoreText.isNotBlank()) {
                                COUITheme.colorScheme.primary
                            } else {
                                COUITheme.colorScheme.onSurfaceSecondary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val subMeta = buildString {
                        if (pgc.styles.isNotBlank()) append(pgc.styles)
                        if (pgc.indexShow.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(pgc.indexShow)
                        }
                    }
                    if (subMeta.isNotBlank()) {
                        Text(
                            text = subMeta,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pgc.desc.isNotBlank()) {
                    Text(
                        text = pgc.desc,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchLiveRow(
    live: SearchLive,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { LiveCoverWidth.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val coverUrl = remember(live.cover, coverWidthPx, coverHeightPx) {
        Formatters.sizedImageUrl(live.cover, coverWidthPx, coverHeightPx)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(8.dp),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(LiveCoverWidth)
                    .height(LiveCoverHeight)
                    .clip(SquircleShape(8.dp))
                    .background(COUITheme.colorScheme.surfaceContainerHigh),
            ) {
                CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                if (live.cateName.isNotBlank()) {
                    Text(
                        text = live.cateName,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(SquircleShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                if (live.onlineText.isNotBlank()) {
                    Text(
                        text = live.onlineText,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(SquircleShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = LiveCoverHeight)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = live.title,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = live.anchor.ifBlank { "未知主播" },
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SearchUserRow(
    user: SearchUser,
    onClick: () -> Unit,
    onVideoClick: (SearchVideo, ExpandOrigin?) -> Unit,
    rowScrollState: RowScrollState? = null,
    rowKey: Any? = null,
    topBarBounds: TopBarBounds? = null,
) {
    val avatarUrl = remember(user.face) { Formatters.sizedImageUrl(user.face, 132, 132) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(url = avatarUrl, size = 44.dp, officialType = user.officialType)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = user.name,
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (user.level > 0) {
                            Text(
                                text = "Lv.${user.level}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = COUITheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = "${user.fansText} · ${user.videoCountText}",
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = user.officialDesc.ifBlank { user.sign }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (user.recentVideos.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    user.recentVideos.forEachIndexed { index, video ->
                        val itemKey = "${index}_${video.bvid}"
                        key(itemKey) {
                            val origin = rememberExpandOrigin(8.dp)
                            SideEffect {
                                origin.attachToRow(
                                    listState = rowScrollState,
                                    rowKey = rowKey,
                                    topBar = topBarBounds,
                                    itemKey = itemKey,
                                )
                            }
                            UserRecentVideoCard(
                                video = video,
                                onClick = {
                                    if (video.bvid.isNotBlank()) {
                                        onVideoClick(video, origin)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .expandOrigin(origin),
                            )
                        }
                    }
                    if (user.recentVideos.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRecentVideoCard(
    video: SearchVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidthDp = LocalScreenConfig.current.screenWidthDp
    val density = LocalDensity.current
    val coverUrl = remember(video.cover, screenWidthDp, density) {
        val widthPx = with(density) { ((screenWidthDp.dp - 64.dp) / 2).coerceAtLeast(120.dp).roundToPx() }
        Formatters.sizedImageUrl(video.cover, widthPx, widthPx * 9 / 16)
    }

    Column(
        modifier = modifier
            .clip(SquircleShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(SquircleShape(8.dp))
                .background(COUITheme.colorScheme.surfaceContainerHigh),
        ) {
            CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
            SearchVideoCoverBadges(playText = video.playText, durationText = video.durationText)
        }
        Text(
            text = video.title,
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = COUITheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (video.pubdateText.isNotBlank()) {
            Text(
                text = video.pubdateText,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
