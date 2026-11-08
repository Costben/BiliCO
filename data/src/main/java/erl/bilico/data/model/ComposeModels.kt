package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 评论表情面板：`x/emote/user/panel/web?business=reply`。
 * 面板按包分组，包本身只有类型和封面，没有名字。
 */
@Serializable
data class EmotePanelResponse(
    val code: Int = 0,
    val message: String = "",
    val data: EmotePanelData? = null,
)

@Serializable
data class EmotePanelData(
    val packages: List<EmotePackageDto> = emptyList(),
)

@Serializable
data class EmotePackageDto(
    /** 4 表示纯文字表情（颜文字），其余是图片。 */
    val type: Int = 0,
    val url: String = "",
    val emote: List<EmoteDto> = emptyList(),
)

@Serializable
data class EmoteDto(
    val text: String = "",
    val url: String = "",
    val meta: ReplyEmoteMeta? = null,
)

/** 评论图片上传：`x/dynamic/feed/draw/upload_bfs`。 */
@Serializable
data class UploadBfsResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UploadBfsData? = null,
)

@Serializable
data class UploadBfsData(
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
)

/** 发表评论时 `pictures` 字段里的一项。 */
data class UploadedPicture(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
) {
    fun toJson(): String =
        """{"img_src":"$url","img_width":${width.coerceAtLeast(1)},""" +
            """"img_height":${height.coerceAtLeast(1)}}"""
}
