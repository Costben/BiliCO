package erl.bilico.data.model

/** PiliPlus 同款直播流层级树：protocol -> format -> codec -> url_info。 */
data class LiveStreamNode(
    val protocolName: String,
    val formats: List<LiveFormatNode>,
)

data class LiveFormatNode(
    val formatName: String,
    val codecs: List<LiveCodecNode>,
)

data class LiveCodecNode(
    val codecName: String,
    val currentQn: Int,
    val qualityDesc: String,
    val acceptQn: List<Int>,
    val baseUrl: String,
    val urlInfos: List<LiveUrlHostNode>,
)

data class LiveUrlHostNode(
    val host: String,
    val extra: String,
)

/** 对应 PiliPlus 的 4 级索引：streamIndex / formatIndex / codecIndex / liveUrlIndex。 */
data class LiveStreamSelection(
    val streamIndex: Int = 0,
    val formatIndex: Int = 0,
    val codecIndex: Int = 0,
    val urlIndex: Int = 0,
)

/** 直播醒目留言（Super Chat）。 */
data class LiveSuperChat(
    val id: Long,
    val uid: Long,
    val userName: String,
    val userFace: String = "",
    val faceFrame: String = "",
    val nameColor: String = "",
    val medalName: String = "",
    val medalLevel: Int = 0,
    val price: Int,
    val message: String,
    val startTimeSec: Long,
    val endTimeSec: Long,
    val backgroundColor: String = "#EDF5FF",
    val backgroundBottomColor: String = "#2A60B2",
    val backgroundPriceColor: String = "#7497CD",
    val messageFontColor: String = "#FFFFFF",
    val backgroundImage: String = "",
)
