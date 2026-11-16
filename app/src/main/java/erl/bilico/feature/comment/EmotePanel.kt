package erl.bilico.feature.comment

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.CommentEmotePackage
import erl.bilico.data.model.FollowingUser
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import io.github.suqi8.coui.kmp.utils.overScrollVertical

internal val EMOTE_PANEL_HEIGHT = 240.dp
internal val EMPTY_PANEL_HEIGHT = 56.dp
private val CATEGORY_BAR_HEIGHT = 40.dp

/**
 * 拦截子列表滑到顶部/底部并回弹后的剩余垂直滚动与抛滑速度，防止手势穿透到外层
 * `OverlayBottomSheet` 导致误滑关闭发布面板。
 */
private object SheetScrollBlocker : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(0f, available.y)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = Velocity(0f, available.y)
}

@Composable
internal fun EmoteGrid(
    packages: List<CommentEmotePackage>,
    failed: Boolean,
    manageUrl: String,
    onOpenManage: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    val hasPackages = packages.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (hasPackages) EMOTE_PANEL_HEIGHT else EMPTY_PANEL_HEIGHT)
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hasPackages -> {
                var selectedIndex by rememberSaveable(packages.size) { mutableIntStateOf(0) }
                val safeIndex = selectedIndex.coerceIn(0, packages.lastIndex)
                val currentPkg = packages[safeIndex]
                val gridState = rememberLazyGridState()

                LaunchedEffect(safeIndex) {
                    gridState.scrollToItem(0)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CATEGORY_BAR_HEIGHT),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .overScrollHorizontal(nestedScrollToParent = false),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(packages.size) { index ->
                                val pkg = packages[index]
                                EmoteCategoryTab(
                                    pkg = pkg,
                                    selected = index == safeIndex,
                                    onClick = { selectedIndex = index },
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        EmoteManageButton(onClick = { onOpenManage(manageUrl) })
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = COUITheme.colorScheme.dividerLine,
                    )

                    val isTextOnly = currentPkg.type == 4 || currentPkg.emotes.all { it.url.isBlank() }
                    val hasLarge = currentPkg.emotes.any { it.large }
                    val minCellSize = when {
                        isTextOnly -> 84.dp
                        hasLarge -> 58.dp
                        else -> 44.dp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .nestedScroll(SheetScrollBlocker)
                            .overScrollVertical(nestedScrollToParent = false),
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minCellSize),
                            state = gridState,
                            overscrollEffect = null,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (isTextOnly) 6.dp else 4.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isTextOnly) 6.dp else 4.dp),
                        ) {
                            items(
                                count = currentPkg.emotes.size,
                                key = { index -> "${currentPkg.id}_$index" },
                            ) { index ->
                                val emote = currentPkg.emotes[index]
                                if (isTextOnly || emote.url.isBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .clip(SquircleShape(8.dp))
                                            .clickable { onPick(emote.insert) }
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = emote.text,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = COUITheme.colorScheme.onSurface,
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(if (emote.large) 54.dp else 42.dp)
                                            .clip(SquircleShape(8.dp))
                                            .clickable { onPick(emote.insert) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AsyncImage(
                                            model = emote.url,
                                            contentDescription = emote.text,
                                            modifier = Modifier.size(if (emote.large) 44.dp else 28.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            failed -> Text(
                text = "登录后才能使用表情",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )

            else -> CircularProgressIndicator(size = 18.dp)
        }
    }
}

@Composable
private fun EmoteCategoryTab(
    pkg: CommentEmotePackage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = pkg.text.ifBlank { if (pkg.type == 4) "颜文字" else "表情" }
    Row(
        modifier = Modifier
            .height(32.dp)
            .squircleSurface(
                color = if (selected) COUITheme.colorScheme.secondaryContainer else Color.Transparent,
                cornerRadius = 10.dp,
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pkg.url.isNotBlank()) {
            AsyncImage(
                model = pkg.url,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmoteManageButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .squircleSurface(
                color = COUITheme.colorScheme.surfaceContainer,
                cornerRadius = 10.dp,
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = COUIIcons.Add,
            contentDescription = "管理表情包",
            modifier = Modifier.size(18.dp),
            tint = COUITheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AtList(
    users: List<FollowingUser>,
    failed: Boolean,
    onPick: (FollowingUser) -> Unit,
) {
    val showList = users.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showList) EMOTE_PANEL_HEIGHT else EMPTY_PANEL_HEIGHT)
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            showList -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(SheetScrollBlocker)
                    .overScrollVertical(nestedScrollToParent = false),
            ) {
                LazyColumn(
                    overscrollEffect = null,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(users.size) { index ->
                        val user = users[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(10.dp))
                                .clickable { onPick(user) }
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = user.face,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = user.name,
                                style = COUITheme.textStyles.body2,
                                maxLines = 1,
                                color = COUITheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            failed -> Text(
                text = "登录后才能 @ 关注的人",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )

            else -> CircularProgressIndicator(size = 18.dp)
        }
    }
}

@Composable
internal fun ActionCapsule(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(Brush.verticalGradient(ACTION_GRADIENT))
            .border(BorderStroke(1.dp, Brush.verticalGradient(ACTION_EDGE_GRADIENT)), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = trimmedLineHeight,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
internal fun ComposerToolButton(
    active: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = if (pressed) {
            tween(durationMillis = 70)
        } else {
            spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)
        },
        label = "composerToolScale",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .squircleSurface(
                color = if (active) COUITheme.colorScheme.secondaryContainer else Color.Unspecified,
                cornerRadius = 8.dp,
            )
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

internal val trimmedLineHeight: TextStyle = TextStyle(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

internal val ACTION_HEIGHT = 44.dp
private val ACTION_GRADIENT = listOf(Color(0xFF1A8CFE), Color(0xFF3198FF))
private val ACTION_EDGE_GRADIENT = listOf(
    Color.White.copy(alpha = 0.18f),
    Color.Transparent,
    Color.White.copy(alpha = 0.15f),
)
