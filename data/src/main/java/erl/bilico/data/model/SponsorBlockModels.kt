package erl.bilico.data.model

/**
 * 空降助手（BiliSponsorBlock）片段分类与对应进度条标识色。
 */
enum class SponsorCategory(
    val id: String,
    val label: String,
    val colorArgb: Long,
) {
    Sponsor("sponsor", "赞助广告", 0xFF00D400L),
    SelfPromo("selfpromo", "无偿/自我推广", 0xFFFFFF00L),
    Interaction("interaction", "一键三连", 0xFFCC00FFL),
    Intro("intro", "片头/开场", 0xFF00FFFFL),
    Outro("outro", "片尾/鸣谢", 0xFF0202EDL),
    Preview("preview", "回顾/预告", 0xFF008FD6L),
    Filler("filler", "离题/灌水", 0xFF7300FFL),
    MusicOfftopic("music_offtopic", "非音乐部分", 0xFFFF9900L),
    PoiHighlight("poi_highlight", "精彩时刻", 0xFFFF1684L),
    ExclusiveAccess("exclusive_access", "独家合作", 0xFF008A5CL),
    ;

    companion object {
        val DEFAULT_IDS: Set<String> = entries
            .filter { it != ExclusiveAccess }
            .map { it.id }
            .toSet()

        fun fromId(id: String): SponsorCategory? = entries.firstOrNull { it.id == id }
    }
}

enum class SponsorActionType(val id: String) {
    Skip("skip"),
    Mute("mute"),
    Poi("poi"),
    Full("full"),
    Unknown(""),
    ;

    companion object {
        fun fromId(id: String): SponsorActionType = entries.firstOrNull { it.id == id } ?: Unknown
    }
}

data class SponsorSegment(
    val uuid: String,
    val cid: Long,
    val category: SponsorCategory,
    val actionType: SponsorActionType,
    val startMs: Long,
    val endMs: Long,
    val videoDurationMs: Long,
    val votes: Int,
    val description: String,
)
