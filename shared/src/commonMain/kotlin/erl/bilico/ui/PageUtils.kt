package erl.bilico.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults

/**
 * 系统应用画在卡片后面的页面底色：浅色取主题 surface，深色是 `#303030`。
 * COUI 深色的 background 是 `#1E1E1E`，比系统页面暗一整档，卡片会浮在更深的底上。
 */
@Composable
fun pageContainerColor(): Color = AppBarBlurOverlayDefaults.containerColor()
