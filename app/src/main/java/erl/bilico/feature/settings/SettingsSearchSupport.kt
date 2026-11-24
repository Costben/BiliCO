package erl.bilico.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.bilico.ui.AppBarSurface
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.PreferenceDivider
import erl.bilico.ui.components.SearchClearButton
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.topBarScrollPx
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 一条支持搜索过滤的设置项，渲染时按过滤后的实际位置动态分配 [CardListPosition]。 */
internal class SettingsEntry(
    val id: String,
    val title: String,
    val summary: String = "",
    val keywords: String = "",
    val content: @Composable (CardListPosition) -> Unit,
) {
    fun matchScore(groupTitle: String, terms: List<String>): Int =
        SettingsSearchMatcher.scoreEntry(this, groupTitle, terms)
}

internal data class SettingsGroupSection(
    val key: String,
    val title: String,
    val entries: List<SettingsEntry>,
    val showDividers: Boolean = false,
)

internal fun parseSearchTerms(query: String): List<String> =
    query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

internal fun cardPositionFor(index: Int, total: Int): CardListPosition = when {
    total <= 1 -> CardListPosition.None
    index == 0 -> CardListPosition.Head
    index == total - 1 -> CardListPosition.Tail
    else -> CardListPosition.Middle
}

@Composable
internal fun SettingsGroupCard(
    entries: List<SettingsEntry>,
    showDividers: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        entries.forEachIndexed { index, entry ->
            if (showDividers && index > 0) {
                PreferenceDivider()
            }
            entry.content(cardPositionFor(index, entries.size))
        }
    }
}

/** 设置页顶部栏：默认展示返回与标题、右上角搜索按钮，点击后展开 COUI 搜索栏实时过滤设置项。 */
@Composable
internal fun SettingsSearchTopBar(
    query: String,
    expanded: Boolean,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val pageColor = pageContainerColor()
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageBarHeight = statusBarTop + 44.dp
    Box(Modifier.fillMaxWidth()) {
        AppBarSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = pageColor,
            scrollFraction = {
                AppBarBlurOverlayDefaults.scrollAlpha(listState.topBarScrollPx(), density)
            },
        ) {
            Box(Modifier.fillMaxWidth().height(pageBarHeight))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop)
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            TopAppBarSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                expanded = expanded,
                onExpandedChange = { active ->
                    if (!active) onQueryChange("")
                    onExpandedChange(active)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "搜索设置、拼音或首字母",
                cancelText = "取消",
                onSearch = { /* 输入即实时过滤，无需额外提交动作 */ },
                onCancel = {
                    onQueryChange("")
                    onExpandedChange(false)
                },
                searchButtonContentDescription = "搜索设置",
                gradientColor = Color.Transparent,
                titleContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackIconButton(onClick = onBack)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "设置",
                            fontSize = with(LocalDensity.current) { TopAppBarDefaults.ToolbarTitleTextSize.toSp() },
                            fontWeight = FontWeight(550),
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        SearchClearButton(onClick = { onQueryChange("") })
                    }
                },
            )
        }
    }
}
