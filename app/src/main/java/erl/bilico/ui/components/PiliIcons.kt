package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.R
import io.github.suqi8.coui.kmp.basic.Text

/**
 * PiliPlus 那套图标字体。
 *
 * 字形取自 PiliPlus 的 `custom_icon.ttf`：弹幕开关、弹幕设置、稍后再看这些 COUI 没有的
 * 都在里面。个别通用图标（返回、首页、更多）继续用 COUIIcons，字体里也没有的一律用矢量补，
 * 不硬塞别的字形顶替。
 */
internal object PiliGlyph {
    const val DmOff = "\uE802"
    const val DmOn = "\uE803"
    const val DmSettings = "\uE804"
    const val Repeat = "\uE810"
    const val Share = "\uE811"
    const val ViewHeadline = "\uE823"
    const val WatchLater = "\uE824"
}

internal val PiliIconFont = FontFamily(Font(R.font.piliplus_custom_icons))

/** 字体图标按字号绘制，行高与字号一致，否则上下会多出半行留白。 */
@Composable
internal fun PiliIcon(
    glyph: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = Color.White,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = glyph,
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        fontFamily = PiliIconFont,
        fontSize = fontSize,
        lineHeight = fontSize,
        color = tint,
        textAlign = TextAlign.Center,
    )
}

/** 画中画：Material 的 picture_in_picture_alt 路径，字体里没有这个字形。 */
internal val PipIcon: ImageVector by lazy {
    icon(
        name = "Pip",
        path = "M19,11h-8v6h8v-6zM23,19V5c0,-1.1 -0.9,-2 -2,-2H3C1.9,3 1,3.9 1,5v14c0,1.1 " +
            "0.9,2 2,2h18c1.1,0 2,-0.9 2,-2zM21,19.01H3V4.98h18V19.01z",
    )
}

/**
 * 全屏 / 退出全屏：字体里的 `open_in_full_rotate_45` 其实画的是水平双向箭头，
 * 拿来当全屏会读成「调整宽度」，所以这两个用 Material 的四角括号。
 */
internal val FullscreenIcon: ImageVector by lazy {
    icon(
        name = "Fullscreen",
        path = "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-4z",
    )
}

internal val FullscreenExitIcon: ImageVector by lazy {
    icon(
        name = "FullscreenExit",
        path = "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z",
    )
}

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        fill = SolidColor(Color.Black),
    ).build()
