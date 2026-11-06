package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.LocalDismissState

/** 菜单里的一行：文案加要做的事。 */
internal class CouiMenuItem(val label: String, val onClick: () -> Unit)

/**
 * COUI 那套弹层菜单：面板半透明，后面是背景模糊，边缘有一圈高光和内阴影，
 * 打开时按重力路径从锚点弹出来，收起时缩回锚点。
 *
 * 必须和锚点（那个三点按钮）放在同一个 [androidx.compose.foundation.layout.Box] 里做兄弟节点，
 * 菜单是按这个 Box 的位置定位的。
 */
@Composable
internal fun CouiPopupMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    items: List<CouiMenuItem>,
) {
    OverlayListPopup(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        val dismiss = LocalDismissState.current
        ListPopupColumn {
            items.forEachIndexed { index, item ->
                DropdownImpl(
                    item = DropdownItem(text = item.label),
                    optionSize = items.size,
                    isSelected = false,
                    index = index,
                    groupKey = "menu",
                    role = Role.Button,
                    onSelectedIndexChange = {
                        item.onClick()
                        dismiss?.invoke()
                    },
                )
            }
        }
    }
}
