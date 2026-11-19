package erl.bilico.feature.cast

data class DlnaDevice(
    val usn: String,
    val friendlyName: String,
    val location: String,
    val avTransportControlUrl: String,
)

data class DlnaPosition(
    val relTimeMs: Long = 0L,
    val durationMs: Long = 0L,
)

data class CastMediaRequest(
    val title: String,
    val bvid: String = "",
    val cid: Long = 0L,
    val epId: Long = 0L,
    val qn: Int = 80,
    val liveStreamUrl: String = "",
    val initialPositionMs: Long = 0L,
)

data class CastUiState(
    val scanning: Boolean = false,
    val devices: List<DlnaDevice> = emptyList(),
    val activeDevice: DlnaDevice? = null,
    val castingTitle: String = "",
    val isPlaying: Boolean = false,
    val isBusy: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val useProxy: Boolean = true,
    val error: String? = null,
)
