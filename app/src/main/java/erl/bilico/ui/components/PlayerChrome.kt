package erl.bilico.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 播放器控件条压暗底色：视频本身可能是亮的，白字要能读清。 */
internal val PlayerScrim = Color(0x66000000)

/** 播放器底部控件条的通用内边距。 */
internal val PlayerControlsPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
