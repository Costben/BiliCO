package erl.bilico.data.settings

/** 播放器顶栏配置的作用域：全屏播放与竖屏（非全屏）播放各存一份。 */
enum class PlayerTopBarMode {
    /** 全屏播放：横屏全屏，以及竖屏视频的全屏。 */
    FULLSCREEN,

    /** 竖屏播放：详情页里带控件的那条视频顶栏。 */
    PORTRAIT,
}

private val BOTH_MODES = setOf(PlayerTopBarMode.FULLSCREEN, PlayerTopBarMode.PORTRAIT)

/**
 * 播放器顶栏里可以单独增删的按钮。
 *
 * 枚举顺序就是默认排序：左边先返回和标题，其余按钮按这个顺序往右排。
 */
enum class PlayerTopBarItem(
    val id: String,
    val label: String,
    val summary: String,
    val modes: Set<PlayerTopBarMode>,
) {
    BACK(
        id = "back",
        label = "返回",
        summary = "左上角的返回键",
        modes = BOTH_MODES,
    ),
    TITLE(
        id = "title",
        label = "视频标题",
        summary = "顶栏中间的标题",
        modes = BOTH_MODES,
    ),
    WATCH_LATER(
        id = "watchLater",
        label = "稍后再看",
        summary = "加入稍后再看列表",
        modes = BOTH_MODES,
    ),
    DANMAKU_SETTINGS(
        id = "danmakuSettings",
        label = "弹幕设置",
        summary = "打开弹幕设置面板",
        modes = setOf(PlayerTopBarMode.FULLSCREEN),
    ),
    DANMAKU_TOGGLE(
        id = "danmakuToggle",
        label = "弹幕开关",
        summary = "快速开关弹幕显示",
        modes = setOf(PlayerTopBarMode.FULLSCREEN),
    ),
    CAST(
        id = "cast",
        label = "投屏",
        summary = "投到电视或投影",
        modes = BOTH_MODES,
    ),
    SHARE(
        id = "share",
        label = "分享",
        summary = "分享当前视频",
        modes = BOTH_MODES,
    ),
    HOME(
        id = "home",
        label = "首页",
        summary = "回到首页",
        modes = BOTH_MODES,
    ),
    AUDIO_ONLY(
        id = "audioOnly",
        label = "黑听",
        summary = "只保留声音，隐藏画面",
        modes = BOTH_MODES,
    ),
    PIP(
        id = "pip",
        label = "小窗",
        summary = "进入画中画播放",
        modes = BOTH_MODES,
    ),
    STATS(
        id = "stats",
        label = "视频信息统计",
        summary = "分辨率、码率、丢帧等实时数据",
        modes = BOTH_MODES,
    ),
    SPONSOR(
        id = "sponsor",
        label = "空降助手",
        summary = "快捷跳转社区标记的片段，也可以提交新片段",
        modes = BOTH_MODES,
    ),
    SLEEP_TIMER(
        id = "sleepTimer",
        label = "定时关闭",
        summary = "定时暂停播放或播完当前停止",
        modes = BOTH_MODES,
    ),
    DETAIL_AUTO_PLAY(
        id = "detailAutoPlay",
        label = "自动播放",
        summary = "开关进入详情页时是否自动起播",
        modes = BOTH_MODES,
    ),
    MORE(
        id = "more",
        label = "更多菜单",
        summary = "三个点菜单，隐藏后仍可从「设置 - 播放器顶栏」打开此设置",
        modes = BOTH_MODES,
    ),
    ;

    companion object {
        fun fromId(id: String): PlayerTopBarItem? = entries.firstOrNull { it.id == id }

        fun itemsFor(mode: PlayerTopBarMode): List<PlayerTopBarItem> =
            entries.filter { mode in it.modes }
    }
}

/**
 * 一种播放模式的顶栏配置：顺序与隐藏项分开存。
 *
 * 顺序为空时按枚举顺序，新增的按钮会自动补在末尾，不会被旧配置藏起来。
 */
data class PlayerTopBarLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    /** 该模式下参与排序的全部按钮，含已隐藏的。 */
    fun orderedItems(mode: PlayerTopBarMode): List<PlayerTopBarItem> {
        val defaults = PlayerTopBarItem.itemsFor(mode)
        if (order.isEmpty()) return defaults
        val known = order.mapNotNull(PlayerTopBarItem::fromId)
            .filter { mode in it.modes }
            .distinct()
        return known + defaults.filterNot { it in known }
    }

    fun visibleItems(mode: PlayerTopBarMode): List<PlayerTopBarItem> =
        orderedItems(mode).filter { it.id !in hidden }
}
