package erl.bilico.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.SponsorCategory
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/** 单选弹窗：和 Cobo 的设置项同一套组件，胶囊高亮由 ListPopupColumn 自己铺。 */
@Composable
internal fun OptionPicker(
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

@Composable
internal fun SponsorCategoriesDialog(
    enabledIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "跳过片段类型",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!dismissed) {
                dismissed = true
                onDismiss()
            }
        },
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .scrollEndHaptic(),
            ) {
                SponsorCategory.entries.forEachIndexed { index, category ->
                    val pos = when (index) {
                        0 -> CardListPosition.Head
                        SponsorCategory.entries.lastIndex -> CardListPosition.Tail
                        else -> CardListPosition.Middle
                    }
                    SwitchPreference(
                        checked = category.id in enabledIds,
                        onCheckedChange = { checked -> onToggle(category.id, checked) },
                        title = category.label,
                        startAction = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(category.colorArgb)),
                            )
                        },
                        cardListPosition = pos,
                    )
                }
            }
            DialogButtonBar(
                negative = null,
                positive = DialogButtonBarAction(text = "完成", onClick = { show = false }),
            )
        }
    }
}
