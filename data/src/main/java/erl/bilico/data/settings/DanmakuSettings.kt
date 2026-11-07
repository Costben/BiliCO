package erl.bilico.data.settings

/** 默认清晰度档位，值就是 B 站的 quality 编号。 */
enum class DefaultQuality(val id: Int, val label: String) {
    P1080(80, "1080P 高清"),
    P720(64, "720P 高清"),
    P480(32, "480P 清晰"),
    P360(16, "360P 流畅"),
}

/**
 * 弹幕完整配置（对齐霹雳派 DanmakuConfig 与霹雳Plus PlDanmakuPanel）：
 * - 按类型屏蔽：滚动 / 顶部 / 底部 / 彩色 / 高级
 * - 功能开关：合并重复弹幕 / 海量弹幕模式
 * - 显示样式：区域 / 不透明度 / 字号 / 弹幕速度 / 描边粗细 / 字体粗细 / 行高
 */
data class DanmakuSettings(
    val enabled: Boolean = true,
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val allowColorful: Boolean = true,
    val allowSpecial: Boolean = true,
    val mergeDuplicates: Boolean = true,
    val massiveMode: Boolean = false,
    /** 0 = 1/4 屏, 1 = 半屏, 2 = 3/4 屏, 3 = 全屏 */
    val area: Int = 1,
    /** 10..100 (%) */
    val opacity: Int = 100,
    /** 50..200 (%) */
    val fontScale: Int = 100,
    /** 50..200 (%)，100% 对应 8 秒滚屏时长 */
    val speed: Int = 100,
    /** 0..30，对应 0.0..3.0dp 描边宽度（默认 16 = 1.6dp） */
    val strokeWidth: Int = 16,
    /** 1..9，对应字重 100..900（默认 6 = 600 SemiBold） */
    val fontWeight: Int = 6,
    /** 100..200 (%)，对应 1.0x..2.0x 行高（默认 135 = 1.35x） */
    val lineHeight: Int = 135,
) {
    val areaFraction: Float
        get() = when (area) {
            0 -> 0.25f
            1 -> 0.50f
            2 -> 0.75f
            else -> 1.00f
        }

    val areaLabel: String
        get() = when (area) {
            0 -> "1/4 屏"
            1 -> "半屏"
            2 -> "3/4 屏"
            else -> "全屏"
        }

    val strokeWidthDp: Float
        get() = (strokeWidth.coerceIn(0, 30)) / 10f

    val lineHeightRatio: Float
        get() = (lineHeight.coerceIn(100, 200)) / 100f

    val scrollDurationMs: Long
        get() = (8_000L * 100L / speed.coerceIn(50, 200)).coerceIn(3_000L, 16_000L)
}
