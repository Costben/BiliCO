package erl.bilico.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 底栏与「我的」页全套图标（线性态统一 1.80dp；底栏面性态纯实心无透明度并保留负空间镂空，「我的」页保持双色透明对比）。 */
internal val NavHomeOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "NavHomeOutline",
        paths = listOf(
            "M3.1,11.2C3.1,9.85 3.62,9.18 4.72,8.26L10.35,3.58C11.32,2.77 12.68,2.77 13.65,3.58" +
                "L19.28,8.26C20.38,9.18 20.9,9.85 20.9,11.2V17.45C20.9,19.75 19.75,20.9 17.45,20.9" +
                "H15.8C15.2,20.9 14.9,20.6 14.9,20V16.65C14.9,15.05 13.6,13.75 12,13.75" +
                "C10.4,13.75 9.1,15.05 9.1,16.65V20C9.1,20.6 8.8,20.9 8.2,20.9H6.55" +
                "C4.25,20.9 3.1,19.75 3.1,17.45V11.2Z",
        ),
    )
}

internal val NavHomeFilledIcon: ImageVector by lazy {
    dualToneVector(
        name = "NavHomeFilled",
        layers = listOf(
            VectorLayer(
                path = "M2.2,11.2C2.2,9.55 2.78,8.75 4.05,7.68L10.05,2.66C11.18,1.72 12.82,1.72 13.95,2.66" +
                    "L19.95,7.68C21.22,8.75 21.8,9.55 21.8,11.2V17.65C21.8,20.35 20.35,21.8 17.65,21.8" +
                    "H15.3V16.65C15.3,14.83 13.82,13.35 12,13.35C10.18,13.35 8.7,14.83 8.7,16.65" +
                    "V21.8H6.35C3.65,21.8 2.2,20.35 2.2,17.65V11.2Z",
            ),
        ),
    )
}

internal val NavDynamicOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "NavDynamicOutline",
        paths = listOf(
            "M6.8,3.5H17.2C19.65,3.5 20.9,4.75 20.9,7.2V14.1C20.9,16.55 19.65,17.8 17.2,17.8" +
                "H10.2C9.2,17.8 8.65,17.95 7.85,18.52L5.05,20.52C4.05,21.22 3.1,20.72 3.1,19.5" +
                "V7.2C3.1,4.75 4.35,3.5 6.8,3.5Z",
            "M7.9,8.9H16.1",
            "M7.9,12.6H13.5",
        ),
    )
}

internal val NavDynamicFilledIcon: ImageVector by lazy {
    val barTop = roundedRectPath(6.4f, 7.7f, 11.2f, 2.0f, 1.0f)
    val barBottom = roundedRectPath(6.4f, 11.8f, 8.2f, 2.0f, 1.0f)
    dualToneVector(
        name = "NavDynamicFilled",
        layers = listOf(
            VectorLayer(
                path = "M6.6,2.6H17.4C20.25,2.6 21.8,4.15 21.8,7V14.2C21.8,17.05 20.25,18.6 17.4,18.6" +
                    "H10.2C9.05,18.6 8.45,18.78 7.52,19.45L4.55,21.55C3.35,22.38 2.2,21.75 2.2,20.32" +
                    "V7C2.2,4.15 3.75,2.6 6.6,2.6Z" + barTop + barBottom,
                evenOdd = true,
            ),
        ),
    )
}

internal val NavMeOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "NavMeOutline",
        paths = listOf(
            circlePath(12f, 7.5f, 3.45f),
            "M4.25,18.55C4.25,15.48 7.38,13.75 12,13.75C16.62,13.75 19.75,15.48 19.75,18.55" +
                "C19.75,19.95 18.85,20.85 17.15,20.85H6.85C5.15,20.85 4.25,19.95 4.25,18.55Z",
        ),
    )
}

internal val NavMeFilledIcon: ImageVector by lazy {
    dualToneVector(
        name = "NavMeFilled",
        layers = listOf(
            VectorLayer(path = circlePath(12f, 7.2f, 4.1f)),
            VectorLayer(
                path = "M3.4,18.7C3.4,15.0 7.0,13.0 12,13.0C17.0,13.0 20.6,15.0 20.6,18.7" +
                    "C20.6,20.5 19.4,21.7 17.3,21.7H6.7C4.6,21.7 3.4,20.5 3.4,18.7Z",
            ),
        ),
    )
}

// ---------- 「我的」页面 ColorOS 经典双色面性入口图标 ----------

internal val MeHistoryIcon: ImageVector by lazy {
    val handsCut = "M12,7.2C12.55,7.2 13,7.65 13,8.2V11.58L15.8,13.28" +
        "C16.27,13.56 16.42,14.16 16.14,14.63C15.86,15.1 15.26,15.25 14.79,14.97" +
        "L11.5,12.98C11.19,12.79 11,12.46 11,12.1V8.2C11,7.65 11.45,7.2 12,7.2Z"
    dualToneVector(
        name = "MeHistory",
        layers = listOf(
            VectorLayer(
                path = circlePath(12f, 12f, 9.5f) + handsCut,
                alpha = SECONDARY_ALPHA,
                evenOdd = true,
            ),
            VectorLayer(path = handsCut),
        ),
    )
}

internal val MeMessageIcon: ImageVector by lazy {
    dualToneVector(
        name = "MeMessage",
        layers = listOf(
            VectorLayer(path = roundedRectPath(2.4f, 4.6f, 19.2f, 14.8f, 4.0f), alpha = SECONDARY_ALPHA),
            VectorLayer(
                path = "M3.2,6.8C3.65,5.45 4.92,4.6 6.4,4.6H17.6C19.08,4.6 20.35,5.45 20.8,6.8" +
                    "L13.3,12.25C12.52,12.82 11.48,12.82 10.7,12.25L3.2,6.8Z",
            ),
        ),
    )
}

internal val MeFavoritesIcon: ImageVector by lazy {
    val folder = "M2.5,7.2C2.5,5.1 4.1,3.5 6.2,3.5H9.1C10.15,3.5 11.1,4.02 11.68,4.88" +
        "L12.4,5.95H17.8C19.9,5.95 21.5,7.55 21.5,9.65V16.8C21.5,18.9 19.9,20.5 17.8,20.5" +
        "H6.2C4.1,20.5 2.5,18.9 2.5,16.8V7.2Z"
    val star = "M11.55,9.15C11.75,8.72 12.25,8.72 12.45,9.15L13.45,11.22L15.72,11.55" +
        "C16.18,11.62 16.36,12.18 16.03,12.5L14.39,14.1L14.78,16.36C14.86,16.82 14.38,17.16 13.97,16.95" +
        "L12,15.9L10.03,16.95C9.62,17.16 9.14,16.82 9.22,16.36L9.61,14.1L7.97,12.5" +
        "C7.64,12.18 7.82,11.62 8.28,11.55L10.55,11.22L11.55,9.15Z"
    dualToneVector(
        name = "MeFavorites",
        layers = listOf(
            VectorLayer(path = folder + star, alpha = SECONDARY_ALPHA, evenOdd = true),
            VectorLayer(path = star),
        ),
    )
}

internal val MeWatchLaterIcon: ImageVector by lazy {
    val bar = roundedRectPath(6.5f, 8.4f, 2.0f, 7.2f, 1.0f)
    val play = "M10.2,9.15C10.2,8.42 11.0,7.98 11.62,8.36L16.35,11.2" +
        "C16.96,11.57 16.96,12.43 16.35,12.8L11.62,15.64C11.0,16.02 10.2,15.58 10.2,14.85V9.15Z"
    dualToneVector(
        name = "MeWatchLater",
        layers = listOf(
            VectorLayer(
                path = roundedRectPath(2.6f, 4.2f, 18.8f, 15.6f, 4.4f) + bar + play,
                alpha = SECONDARY_ALPHA,
                evenOdd = true,
            ),
            VectorLayer(path = bar + play),
        ),
    )
}

internal val MeDownloadsIcon: ImageVector by lazy {
    dualToneVector(
        name = "MeDownloads",
        layers = listOf(
            VectorLayer(path = roundedRectPath(3.1f, 15.2f, 17.8f, 5.6f, 2.6f), alpha = SECONDARY_ALPHA),
            VectorLayer(
                path = "M12,2.6C12.62,2.6 13.12,3.1 13.12,3.72V11.05L15.45,8.72" +
                    "C15.88,8.28 16.6,8.28 17.03,8.72C17.46,9.15 17.46,9.86 17.03,10.3" +
                    "L12.78,14.55C12.35,14.98 11.65,14.98 11.22,14.55L6.97,10.3" +
                    "C6.54,9.86 6.54,9.15 6.97,8.72C7.4,8.28 8.12,8.28 8.55,8.72" +
                    "L10.88,11.05V3.72C10.88,3.1 11.38,2.6 12,2.6Z",
            ),
        ),
    )
}

internal val MeBangumiIcon: ImageVector by lazy {
    val play = "M10.1,10.7C10.1,10.02 10.85,9.6 11.43,9.96L15.65,12.56" +
        "C16.21,12.91 16.21,13.71 15.65,14.06L11.43,16.66C10.85,17.02 10.1,16.6 10.1,15.92V10.7Z"
    dualToneVector(
        name = "MeBangumi",
        layers = listOf(
            // 天线末端止于 y=5.3（加 0.9 线帽正好贴齐电视顶边 y=6.2，不进入半透明机身且居中无黑点）
            VectorLayer(path = "M7.8,3.2L10.0,5.3M16.2,3.2L14.0,5.3", strokeWidth = UNIFIED_STROKE_WIDTH),
            VectorLayer(
                path = roundedRectPath(2.6f, 6.2f, 18.8f, 14.2f, 4.2f) + play,
                alpha = SECONDARY_ALPHA,
                evenOdd = true,
            ),
            VectorLayer(path = play),
        ),
    )
}

internal val MeFollowIcon: ImageVector by lazy {
    dualToneVector(
        name = "MeFollow",
        layers = listOf(
            VectorLayer(
                path = circlePath(15.8f, 8.2f, 2.9f) +
                    "M14.2,13.6C17.8,13.6 21.2,15.1 21.2,18.0C21.2,19.3 20.3,20.1 18.8,20.1H15.2Z",
                alpha = SECONDARY_ALPHA,
            ),
            VectorLayer(
                path = circlePath(9.2f, 7.6f, 3.6f) +
                    "M2.6,18.2C2.6,14.8 5.6,13.1 9.2,13.1C12.8,13.1 15.8,14.8 15.8,18.2" +
                    "C15.8,19.8 14.7,20.8 12.9,20.8H5.5C3.7,20.8 2.6,19.8 2.6,18.2Z",
            ),
        ),
    )
}

internal val MeSettingsIcon: ImageVector by lazy {
    // 按圆心 (12, 12) 每 60° 严格旋转对称生成的六齿齿轮，确保六个齿宽度完全一致
    val gearOuter = "M9.226,5.302Q9.586,3.85 10.223,2.413Q12.0,1.9 13.777,2.413Q14.414,3.85 14.774,5.302" +
        "Q15.55,5.851 16.414,6.248Q17.851,5.834 19.414,5.668Q20.747,6.95 21.191,8.745" +
        "Q20.265,10.016 19.188,11.054Q19.1,12.0 19.188,12.946Q20.265,13.984 21.191,15.255" +
        "Q20.747,17.05 19.414,18.332Q17.851,18.166 16.414,17.752Q15.55,18.149 14.774,18.698" +
        "Q14.414,20.15 13.777,21.587Q12.0,22.1 10.223,21.587Q9.586,20.15 9.226,18.698" +
        "Q8.45,18.149 7.586,17.752Q6.149,18.166 4.586,18.332Q3.253,17.05 2.809,15.255" +
        "Q3.735,13.984 4.812,12.946Q4.9,12.0 4.812,11.054Q3.735,10.016 2.809,8.745" +
        "Q3.253,6.95 4.586,5.668Q6.149,5.834 7.586,6.248Q8.45,5.851 9.226,5.302Z"
    val hole = circlePath(12f, 12f, 3.85f)
    dualToneVector(
        name = "MeSettings",
        layers = listOf(
            VectorLayer(path = gearOuter + hole, evenOdd = true),
            VectorLayer(path = hole, alpha = 0.34f),
        ),
    )
}

internal val MeSwitchAccountIcon: ImageVector by lazy {
    dualToneVector(
        name = "MeSwitchAccount",
        layers = listOf(
            VectorLayer(
                path = "M19.5,15.0H9.6V13.1C9.6,12.35 8.72,11.95 8.15,12.45L4.2,15.45" +
                    "C3.72,15.82 3.72,16.58 4.2,16.95L8.15,19.95C8.72,20.45 9.6,20.05 9.6,19.3V17.4H19.5" +
                    "C20.16,17.4 20.7,16.86 20.7,16.2C20.7,15.54 20.16,15.0 19.5,15.0Z",
                alpha = SECONDARY_ALPHA,
            ),
            VectorLayer(
                path = "M4.5,6.6H14.4V4.7C14.4,3.95 15.28,3.55 15.85,4.05L19.8,7.05" +
                    "C20.28,7.42 20.28,8.18 19.8,8.55L15.85,11.55C15.28,12.05 14.4,11.65 14.4,10.9V9.0H4.5" +
                    "C3.84,9.0 3.3,8.46 3.3,7.8C3.3,7.14 3.84,6.6 4.5,6.6Z",
            ),
        ),
    )
}

internal val MeLogoutIcon: ImageVector by lazy {
    dualToneVector(
        name = "MeLogout",
        layers = listOf(
            VectorLayer(path = roundedRectPath(3.2f, 3.4f, 10.4f, 17.2f, 3.6f), alpha = SECONDARY_ALPHA),
            VectorLayer(
                path = "M9.0,10.8H16.2V8.5C16.2,7.75 17.08,7.35 17.65,7.85L20.9,11.15" +
                    "C21.35,11.62 21.35,12.38 20.9,12.85L17.65,16.15C17.08,16.65 16.2,16.25 16.2,15.5" +
                    "V13.2H9.0C8.34,13.2 7.8,12.66 7.8,12.0C7.8,11.34 8.34,10.8 9.0,10.8Z",
            ),
        ),
    )
}

internal data class VectorLayer(
    val path: String,
    val alpha: Float = 1f,
    val evenOdd: Boolean = false,
    val strokeWidth: Float = 0f,
)

internal fun unifiedStrokeIcon(name: String, paths: List<String>): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = UNIFIED_STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

internal fun dualToneVector(name: String, layers: List<VectorLayer>): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        layers.forEach { layer ->
            if (layer.strokeWidth > 0f) {
                addPath(
                    pathData = addPathNodes(layer.path),
                    stroke = SolidColor(Color.Black),
                    strokeAlpha = layer.alpha,
                    strokeLineWidth = layer.strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            } else {
                addPath(
                    pathData = addPathNodes(layer.path),
                    fill = SolidColor(Color.Black),
                    fillAlpha = layer.alpha,
                    pathFillType = if (layer.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                )
            }
        }
    }.build()

internal fun circlePath(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},$cy A$r,$r 0 1,0 ${cx + r},$cy A$r,$r 0 1,0 ${cx - r},$cy Z"

internal fun roundedRectPath(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    val rr = r.coerceAtMost(minOf(w, h) / 2f)
    val x2 = x + w
    val y2 = y + h
    return "M${x + rr},$y H${x2 - rr} A$rr,$rr 0 0,1 $x2,${y + rr} V${y2 - rr} " +
        "A$rr,$rr 0 0,1 ${x2 - rr},$y2 H${x + rr} A$rr,$rr 0 0,1 $x,${y2 - rr} " +
        "V${y + rr} A$rr,$rr 0 0,1 ${x + rr},$y Z"
}

internal const val UNIFIED_STROKE_WIDTH = 1.8f
internal const val SECONDARY_ALPHA = 0.38f
