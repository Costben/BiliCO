package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
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
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Home

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

/** 表情：Material 的 mood 路径，评论输入框的工具栏用。 */
internal val EmojiIcon: ImageVector by lazy {
    icon(
        name = "Emoji",
        path = "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
            "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
            "M15.5,11c0.83,0 1.5,-0.67 1.5,-1.5S16.33,8 15.5,8 14,8.67 14,9.5s0.67,1.5 1.5,1.5z" +
            "M8.5,11c0.83,0 1.5,-0.67 1.5,-1.5S9.33,8 8.5,8 7,8.67 7,9.5 7.67,11 8.5,11z" +
            "M12,17.5c2.33,0 4.31,-1.46 5.11,-3.5H6.89c0.8,2.04 2.78,3.5 5.11,3.5z",
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

/** 亮度：手势调亮度时那根竖条上方的太阳。 */
internal val BrightnessIcon: ImageVector by lazy {
    icon(
        name = "Brightness",
        path = "M20,15.31L23.31,12L20,8.69V4h-4.69L12,0.69L8.69,4H4v4.69L0.69,12L4,15.31V20h4.69L12,23.31" +
            "L15.31,20H20V15.31zM12,17.5c-3.04,0 -5.5,-2.46 -5.5,-5.5s2.46,-5.5 5.5,-5.5 5.5,2.46 5.5,5.5 " +
            "-2.46,5.5 -5.5,5.5zM12,8.5c-1.93,0 -3.5,1.57 -3.5,3.5s1.57,3.5 3.5,3.5 3.5,-1.57 3.5,-3.5 " +
            "-1.57,-3.5 -3.5,-3.5z",
    )
}

/** 音量：手势调音量时那根竖条上方的小喇叭。 */
internal val VolumeIcon: ImageVector by lazy {
    icon(
        name = "Volume",
        path = "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 " +
            "2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 " +
            "7,-8.77s-2.99,-7.86 -7,-8.77z",
    )
}

/**
 * 全屏 / 退出全屏：字体里的 `open_in_full_rotate_45` 其实画的是水平双向箭头，
 * 拿来当全屏会读成「调整宽度」，所以这两个用 Material 的四角括号。
 *
 * Material 原路径只占 24 视口的 5..19，和 COUII 图标并排时小一圈，
 * 这里绕中心放大 1.4 倍（占 2.2..21.8），视觉大小才和旁边的图标一致。
 */
internal val FullscreenIcon: ImageVector by lazy {
    icon(
        name = "Fullscreen",
        path = "M5,14.8H2.2v7h7v-2.8H5v-4.2zM2.2,9.2h2.8V5h4.2V2.2H2.2v7z" +
            "M19,19h-4.2v2.8h7v-7h-2.8v4.2zM14.8,2.2v2.8h4.2v4.2h2.8V2.2h-5.6z",
    )
}

internal val FullscreenExitIcon: ImageVector by lazy {
    icon(
        name = "FullscreenExit",
        path = "M2.2,17.6h4.2v4.2h2.8v-7H2.2v2.8zM6.4,6.4H2.2v2.8h7V2.2H6.4v4.2z" +
            "M14.8,21.8h2.8v-4.2h4.2v-2.8h-7v7zM17.6,6.4V2.2h-2.8v7h7V6.4h-4.2z",
    )
}

/**
 * 底栏三枚导航图标的「线性（未选中）/ 面性（选中）」成对矢量：
 * 每一对均严格共享 COUI 17 的外轮廓坐标，切换时外边界零跳动，内部在镂空线性与实心面性之间平滑切换。
 */
internal val NavHomeFilledIcon: ImageVector
    get() = COUIIcons.Home

internal val NavHomeOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "NavHomeOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = listOf(
            // 外轮廓：与 COUIIcons.Regular.Home 完全一致
            PathNode.MoveTo(2.243f, 10.404f),
            PathNode.CurveTo(2.125f, 10.848f, 2.125f, 11.328f, 2.125f, 12.286f),
            PathNode.LineTo(2.125f, 17.205f),
            PathNode.CurveTo(2.125f, 18.883f, 2.125f, 19.722f, 2.451f, 20.363f),
            PathNode.CurveTo(2.738f, 20.927f, 3.196f, 21.386f, 3.761f, 21.673f),
            PathNode.CurveTo(4.402f, 22.0f, 5.241f, 22.0f, 6.919f, 22.0f),
            PathNode.LineTo(7.164f, 22.0f),
            PathNode.CurveTo(7.836f, 22.0f, 8.171f, 22.0f, 8.428f, 21.869f),
            PathNode.CurveTo(8.653f, 21.754f, 8.836f, 21.571f, 8.951f, 21.346f),
            PathNode.CurveTo(9.082f, 21.089f, 9.082f, 20.754f, 9.082f, 20.082f),
            PathNode.LineTo(9.082f, 17.57f),
            PathNode.CurveTo(9.082f, 15.959f, 10.388f, 14.653f, 11.999f, 14.653f),
            PathNode.CurveTo(13.609f, 14.653f, 14.915f, 15.959f, 14.915f, 17.57f),
            PathNode.LineTo(14.915f, 20.082f),
            PathNode.CurveTo(14.915f, 20.754f, 14.915f, 21.089f, 15.047f, 21.346f),
            PathNode.CurveTo(15.161f, 21.571f, 15.345f, 21.754f, 15.57f, 21.869f),
            PathNode.CurveTo(15.826f, 22.0f, 16.162f, 22.0f, 16.833f, 22.0f),
            PathNode.LineTo(17.08f, 22.0f),
            PathNode.CurveTo(18.758f, 22.0f, 19.597f, 22.0f, 20.238f, 21.673f),
            PathNode.CurveTo(20.802f, 21.386f, 21.261f, 20.927f, 21.548f, 20.363f),
            PathNode.CurveTo(21.875f, 19.722f, 21.875f, 18.883f, 21.875f, 17.205f),
            PathNode.LineTo(21.875f, 12.286f),
            PathNode.CurveTo(21.875f, 11.327f, 21.875f, 10.848f, 21.756f, 10.404f),
            PathNode.CurveTo(21.65f, 10.01f, 21.476f, 9.638f, 21.243f, 9.303f),
            PathNode.CurveTo(20.979f, 8.926f, 20.611f, 8.619f, 19.877f, 8.002f),
            PathNode.LineTo(14.055f, 3.114f),
            PathNode.CurveTo(13.324f, 2.501f, 12.959f, 2.194f, 12.55f, 2.078f),
            PathNode.CurveTo(12.19f, 1.974f, 11.808f, 1.974f, 11.449f, 2.078f),
            PathNode.CurveTo(11.04f, 2.194f, 10.675f, 2.501f, 9.944f, 3.114f),
            PathNode.LineTo(4.122f, 8.002f),
            PathNode.CurveTo(3.387f, 8.619f, 3.02f, 8.926f, 2.756f, 9.303f),
            PathNode.CurveTo(2.523f, 9.638f, 2.349f, 10.01f, 2.243f, 10.404f),
            PathNode.Close,
            // 内镂空轮廓：保持 1.82dp 等距壁厚
            PathNode.MoveTo(11.45f, 4.18f),
            PathNode.CurveTo(11.77f, 3.91f, 12.23f, 3.91f, 12.55f, 4.18f),
            PathNode.LineTo(18.68f, 9.33f),
            PathNode.CurveTo(19.68f, 10.17f, 20.05f, 10.66f, 20.05f, 12.15f),
            PathNode.LineTo(20.05f, 17.25f),
            PathNode.CurveTo(20.05f, 19.28f, 19.35f, 20.18f, 17.35f, 20.18f),
            PathNode.LineTo(16.73f, 20.18f),
            PathNode.LineTo(16.73f, 17.57f),
            PathNode.CurveTo(16.73f, 14.96f, 14.61f, 12.83f, 11.999f, 12.83f),
            PathNode.CurveTo(9.39f, 12.83f, 7.27f, 14.96f, 7.27f, 17.57f),
            PathNode.LineTo(7.27f, 20.18f),
            PathNode.LineTo(6.65f, 20.18f),
            PathNode.CurveTo(4.65f, 20.18f, 3.95f, 19.28f, 3.95f, 17.25f),
            PathNode.LineTo(3.95f, 12.15f),
            PathNode.CurveTo(3.95f, 10.66f, 4.32f, 10.17f, 5.32f, 9.33f),
            PathNode.LineTo(11.45f, 4.18f),
            PathNode.Close,
        ),
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
    ).build()
}

internal val NavDynamicOutlineIcon: ImageVector
    get() = COUIIcons.Community

internal val NavDynamicFilledIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "NavDynamicFilled",
        defaultWidth = 24.0f.dp,
        defaultHeight = 24.0f.dp,
        viewportWidth = 1178.4f,
        viewportHeight = 1178.4f,
    ).apply {
        group(scaleY = -1.0f, translationY = 1178.4f) {
            addPath(
                pathData = listOf(
                    // 外气泡实心轮廓（与 COUIIcons.Regular.Community 严格同源）
                    PathNode.MoveTo(135.4f, 166.9f),
                    PathNode.QuadTo(126.3f, 182.3f, 122.4f, 204.6f),
                    PathNode.QuadTo(118.6f, 226.9f, 118.6f, 281.9f),
                    PathNode.VerticalTo(749.8f),
                    PathNode.QuadTo(118.6f, 838.6f, 121.5f, 876.5f),
                    PathNode.QuadTo(124.4f, 914.4f, 137.9f, 941.6f),
                    PathNode.QuadTo(165.9f, 995.0f, 218.2f, 1021.8f),
                    PathNode.QuadTo(245.4f, 1035.5f, 283.8f, 1038.8f),
                    PathNode.QuadTo(322.2f, 1042.2f, 411.1f, 1042.2f),
                    PathNode.HorizontalTo(767.4f),
                    PathNode.QuadTo(856.3f, 1042.2f, 894.2f, 1038.8f),
                    PathNode.QuadTo(932.1f, 1035.5f, 959.3f, 1021.8f),
                    PathNode.QuadTo(985.3f, 1009.3f, 1006.1f, 988.5f),
                    PathNode.QuadTo(1027.0f, 967.7f, 1039.5f, 941.6f),
                    PathNode.QuadTo(1053.2f, 914.4f, 1056.5f, 876.5f),
                    PathNode.QuadTo(1059.8f, 838.6f, 1059.8f, 749.8f),
                    PathNode.VerticalTo(564.9f),
                    PathNode.QuadTo(1059.8f, 476.1f, 1056.5f, 437.7f),
                    PathNode.QuadTo(1053.2f, 399.3f, 1039.5f, 372.2f),
                    PathNode.QuadTo(1012.3f, 319.9f, 959.3f, 291.9f),
                    PathNode.QuadTo(932.1f, 278.4f, 894.2f, 275.5f),
                    PathNode.QuadTo(856.3f, 272.5f, 767.4f, 272.5f),
                    PathNode.HorizontalTo(506.3f),
                    PathNode.QuadTo(483.4f, 272.5f, 466.9f, 272.1f),
                    PathNode.QuadTo(450.3f, 271.6f, 439.2f, 268.7f),
                    PathNode.QuadTo(420.2f, 266.1f, 397.0f, 255.6f),
                    PathNode.QuadTo(379.7f, 247.2f, 341.1f, 219.7f),
                    PathNode.LineTo(316.3f, 202.5f),
                    PathNode.QuadTo(262.4f, 165.0f, 238.8f, 151.5f),
                    PathNode.QuadTo(215.2f, 138.1f, 196.8f, 137.2f),
                    PathNode.QuadTo(177.1f, 136.4f, 160.3f, 144.7f),
                    PathNode.QuadTo(143.5f, 153.0f, 135.4f, 166.9f),
                    PathNode.Close,
                    // 内部两条横杠负空间镂空
                    PathNode.MoveTo(354.2f, 543.9f),
                    PathNode.VerticalTo(566.8f),
                    PathNode.QuadTo(354.2f, 585.0f, 362.5f, 591.8f),
                    PathNode.QuadTo(370.7f, 598.6f, 388.9f, 598.6f),
                    PathNode.HorizontalTo(693.2f),
                    PathNode.QuadTo(710.4f, 598.6f, 719.2f, 591.8f),
                    PathNode.QuadTo(727.9f, 585.0f, 727.9f, 568.7f),
                    PathNode.VerticalTo(543.9f),
                    PathNode.QuadTo(727.9f, 526.8f, 719.6f, 519.4f),
                    PathNode.QuadTo(711.3f, 512.1f, 693.2f, 512.1f),
                    PathNode.HorizontalTo(388.9f),
                    PathNode.QuadTo(370.9f, 512.1f, 362.6f, 519.3f),
                    PathNode.QuadTo(354.2f, 526.6f, 354.2f, 543.9f),
                    PathNode.Close,
                    PathNode.MoveTo(354.2f, 747.9f),
                    PathNode.VerticalTo(771.6f),
                    PathNode.QuadTo(354.2f, 786.8f, 362.6f, 794.2f),
                    PathNode.QuadTo(370.9f, 801.6f, 387.0f, 801.6f),
                    PathNode.HorizontalTo(790.4f),
                    PathNode.QuadTo(807.5f, 801.6f, 815.9f, 794.7f),
                    PathNode.QuadTo(824.2f, 787.8f, 824.2f, 771.6f),
                    PathNode.VerticalTo(747.9f),
                    PathNode.QuadTo(824.2f, 730.7f, 815.9f, 723.3f),
                    PathNode.QuadTo(807.5f, 716.0f, 790.4f, 716.0f),
                    PathNode.HorizontalTo(387.0f),
                    PathNode.QuadTo(371.7f, 716.0f, 363.0f, 723.4f),
                    PathNode.QuadTo(354.2f, 730.8f, 354.2f, 747.9f),
                    PathNode.Close,
                ),
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.EvenOdd,
            )
        }
    }.build()
}

/** 「我的」线性图标：Material 的 person_outline，放大到和 COUI 那套同宽。 */
internal val NavMeOutlineIcon: ImageVector by lazy {
    icon(
        name = "NavMeOutline",
        path = "M12,5.9c1.16,0 2.1,0.94 2.1,2.1s-0.94,2.1 -2.1,2.1S9.9,9.16 9.9,8 " +
            "10.84,5.9 12,5.9m0,9c2.97,0 6.1,1.46 6.1,2.1v1.1H5.9v-1.1c0,-0.64 3.13,-2.1 6.1,-2.1" +
            "M12,4C9.79,4 8,5.79 8,8s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z" +
            "M12,13c-2.67,0 -8,1.34 -8,4v3h16v-3c0,-2.66 -5.33,-4 -8,-4z",
        scale = MeIconScale,
    )
}

/** 「我的」面性图标：Material 的 person，和线性版同源，边界零跳动。 */
internal val NavMeFilledIcon: ImageVector by lazy {
    icon(
        name = "NavMeFilled",
        path = "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4z" +
            "M12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z",
        scale = MeIconScale,
    )
}

/** 人形字形本身只铺 4..20，放大 1.234 之后正好铺到 2.1..21.9，和 COUI 的底栏图标一样大。 */
private const val MeIconScale = 1.234f

private fun icon(name: String, path: String, scale: Float = 1f): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Material 的字形只占 4..20，COUI 那套铺到 2.1..21.9；放进底栏时按中心放大对齐。
        if (scale != 1f) {
            group(scaleX = scale, scaleY = scale, pivotX = 12f, pivotY = 12f) {
                addPath(
                    pathData = addPathNodes(path),
                    fill = SolidColor(Color.Black),
                )
            }
        } else {
            addPath(
                pathData = addPathNodes(path),
                fill = SolidColor(Color.Black),
            )
        }
    }.build()
