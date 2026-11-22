package erl.bilico.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

/** 视频详情页操作栏图标：未激活态统一 1.80dp 线宽，激活态为 ColorOS 双色面性对比。 */
internal val StarOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "StarOutline",
        paths = listOf(
            "M11.15,3.68C11.5,2.95 12.5,2.95 12.85,3.68L14.85,7.82C14.99,8.11 15.27,8.31 15.59,8.36" +
                "L20.12,9.02C20.92,9.14 21.24,10.12 20.66,10.68L17.39,13.88" +
                "C17.16,14.1 17.05,14.43 17.11,14.75L17.88,19.28C18.02,20.08 17.18,20.69 16.46,20.31" +
                "L12.42,18.18C12.13,18.03 11.87,18.03 11.58,18.18L7.54,20.31" +
                "C6.82,20.69 5.98,20.08 6.12,19.28L6.89,14.75C6.95,14.43 6.84,14.1 6.61,13.88" +
                "L3.34,10.68C2.76,10.12 3.08,9.14 3.88,9.02L8.41,8.36" +
                "C8.73,8.31 9.01,8.11 9.15,7.82L11.15,3.68Z",
        ),
    )
}

internal val StarFilledIcon: ImageVector by lazy {
    dualToneVector(
        name = "StarFilled",
        layers = listOf(
            VectorLayer(
                path = "M11.02,3.18C11.42,2.35 12.58,2.35 12.98,3.18L15.15,7.66" +
                    "C15.31,7.99 15.62,8.22 15.98,8.28L20.88,9.01C21.78,9.14 22.14,10.25 21.48,10.89" +
                    "L17.94,14.37C17.68,14.62 17.56,14.99 17.62,15.35L18.46,20.25" +
                    "C18.61,21.15 17.66,21.84 16.86,21.42L12.5,19.11C12.18,18.94 11.82,18.94 11.5,19.11" +
                    "L7.14,21.42C6.34,21.84 5.39,21.15 5.54,20.25L6.38,15.35" +
                    "C6.44,14.99 6.32,14.62 6.06,14.37L2.52,10.89C1.86,10.25 2.22,9.14 3.12,9.01" +
                    "L8.02,8.28C8.38,8.22 8.69,7.99 8.85,7.66L11.02,3.18Z",
            ),
            VectorLayer(path = circlePath(18.8f, 4.8f, 2.1f), alpha = SECONDARY_ALPHA),
        ),
    )
}

internal val CoinOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "CoinOutline",
        paths = listOf(
            circlePath(12f, 12f, 8.8f),
            "M9.6,7.9V16.1H12.75C14.18,16.1 15.1,15.22 15.1,13.9C15.1,12.78 14.42,12.02 13.35,11.82" +
                "C14.15,11.58 14.68,10.92 14.68,10C14.68,8.75 13.82,7.9 12.45,7.9H9.6Z",
            "M9.6,11.8H12.7",
        ),
    )
}

internal val CoinFilledIcon: ImageVector by lazy {
    val coinHalo = circlePath(12f, 12f, 9.7f) + circlePath(12f, 12f, 8.2f)
    val coinBodyAndB = circlePath(12f, 12f, 8.2f) +
        "M8.95,7.45H12.45C13.98,7.45 14.98,8.32 14.98,9.68C14.98,10.58 14.48,11.25 13.72,11.58" +
        "C14.72,11.9 15.35,12.7 15.35,13.85C15.35,15.48 14.2,16.58 12.45,16.58H8.95V7.45Z" +
        "M10.72,9.02V10.95H12.22C12.88,10.95 13.3,10.58 13.3,9.98C13.3,9.38 12.88,9.02 12.22,9.02H10.72Z" +
        "M10.72,12.48V14.88H12.42C13.18,14.88 13.65,14.45 13.65,13.68" +
        "C13.65,12.92 13.18,12.48 12.42,12.48H10.72Z"
    dualToneVector(
        name = "CoinFilled",
        layers = listOf(
            VectorLayer(path = coinHalo, alpha = 0.28f, evenOdd = true),
            VectorLayer(path = coinBodyAndB, evenOdd = true),
        ),
    )
}

internal val WatchLaterOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "WatchLaterOutline",
        paths = listOf(
            circlePath(12f, 12f, 8.8f),
            "M12,7.6V12L15,13.8",
        ),
    )
}

internal val DownloadOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "DownloadOutline",
        paths = listOf(
            "M12,3.8V14.4",
            "M8.1,10.7L12,14.6L15.9,10.7",
            "M4.1,14.8V17.1C4.1,19.1 5.1,20.1 7.1,20.1H16.9C18.9,20.1 19.9,19.1 19.9,17.1V14.8",
        ),
    )
}

internal val DownloadFilledIcon: ImageVector
    get() = MeDownloadsIcon

internal val ShareOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "ShareOutline",
        paths = listOf(
            "M12,13.8V3.6",
            "M8.3,7.2L12,3.4L15.7,7.2",
            "M7.6,9.5H6.8C4.8,9.5 3.8,10.5 3.8,12.5V17.4C3.8,19.4 4.8,20.4 6.8,20.4" +
                "H17.2C19.2,20.4 20.2,19.4 20.2,17.4V12.5C20.2,10.5 19.2,9.5 17.2,9.5H16.4",
        ),
    )
}

// ---------- 播放器顶栏右上角（及顶栏通用）统一尺寸 1.80dp 线性图标 ----------
// 视觉框统一落在 4.4..19.6 之间，避免不同来源图标大小悬殊。

internal val TopBarBackIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarBack",
        paths = listOf("M14.5,5.6L8.2,12L14.5,18.4"),
    )
}

internal val TopBarHomeIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarHome",
        paths = listOf(
            "M4.6,11.3C4.6,10.15 5.05,9.55 5.95,8.8L10.65,4.9C11.45,4.24 12.55,4.24 13.35,4.9" +
                "L18.05,8.8C18.95,9.55 19.4,10.15 19.4,11.3V16.65C19.4,18.55 18.55,19.4 16.65,19.4" +
                "H15.25C14.75,19.4 14.5,19.15 14.5,18.65V15.9C14.5,14.52 13.38,13.4 12,13.4" +
                "C10.62,13.4 9.5,14.52 9.5,15.9V18.65C9.5,19.15 9.25,19.4 8.75,19.4H7.35" +
                "C5.45,19.4 4.6,18.55 4.6,16.65V11.3Z",
        ),
    )
}

internal val TopBarWatchLaterIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarWatchLater",
        paths = listOf(
            circlePath(12f, 12f, 7.6f),
            "M12,8.1V12L14.6,13.5",
        ),
    )
}

internal val TopBarDmSettingsIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarDmSettings",
        paths = listOf(
            roundedRectPath(4.4f, 5.6f, 15.2f, 12.8f, 3.2f),
            "M8.4,10.0H15.6",
            "M8.4,14.0H12.4",
            circlePath(14.8f, 14.0f, 1.3f),
        ),
    )
}

internal val TopBarDmOnIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarDmOn",
        paths = listOf(
            roundedRectPath(4.4f, 5.6f, 15.2f, 12.8f, 3.2f),
            "M8.2,10.0H15.8",
            "M8.2,14.0H13.4",
        ),
    )
}

internal val TopBarDmOffIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarDmOff",
        paths = listOf(
            roundedRectPath(4.4f, 5.6f, 15.2f, 12.8f, 3.2f),
            "M8.2,10.0H11.5",
            "M8.2,14.0H12.8",
            "M5.4,18.6L18.6,5.4",
        ),
    )
}

internal val TopBarCastIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarCast",
        paths = listOf(
            "M4.6,10.5V8.4C4.6,6.4 5.6,5.4 7.6,5.4H16.4C18.4,5.4 19.4,6.4 19.4,8.4V15.6" +
                "C19.4,17.6 18.4,18.6 16.4,18.6H14.5",
            "M4.8,14.0A4.5,4.5 0 0,1 9.3,18.5",
            "M4.8,17.0A1.5,1.5 0 0,1 6.3,18.5",
        ),
    )
}

internal val TopBarShareIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarShare",
        paths = listOf(
            "M12,13.6V4.6",
            "M8.8,7.7L12,4.4L15.2,7.7",
            "M7.8,9.8H7.2C5.4,9.8 4.6,10.6 4.6,12.4V16.6C4.6,18.4 5.4,19.2 7.2,19.2" +
                "H16.8C18.6,19.2 19.4,18.4 19.4,16.6V12.4C19.4,10.6 18.6,9.8 16.8,9.8H16.2",
        ),
    )
}

internal val TopBarAudioOnlyIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarAudioOnly",
        paths = listOf(
            "M5.2,14.2V11.8C5.2,8.0 8.2,5.0 12,5.0C15.8,5.0 18.8,8.0 18.8,11.8V14.2",
            roundedRectPath(4.6f, 13.0f, 3.0f, 5.4f, 1.4f),
            roundedRectPath(16.4f, 13.0f, 3.0f, 5.4f, 1.4f),
        ),
    )
}

internal val TopBarPipIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarPip",
        paths = listOf(
            roundedRectPath(4.4f, 5.6f, 15.2f, 12.8f, 3.0f),
            roundedRectPath(11.6f, 11.4f, 5.4f, 4.4f, 1.2f),
        ),
    )
}

internal val TopBarStatsIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarStats",
        paths = listOf(
            circlePath(12f, 12f, 7.6f),
            "M12,11.2V15.4",
            circlePath(12f, 8.6f, 0.45f),
        ),
    )
}

internal val TopBarSponsorIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarSponsor",
        paths = listOf(
            "M6.6,7.0L13.6,12L6.6,17.0Z",
            "M16.8,7.2V16.8",
        ),
    )
}

internal val TopBarMoreIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "TopBarMore",
        paths = listOf(
            circlePath(6.6f, 12f, 0.7f),
            circlePath(12f, 12f, 0.7f),
            circlePath(17.4f, 12f, 0.7f),
        ),
    )
}
