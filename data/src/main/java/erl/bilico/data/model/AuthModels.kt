package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BaseResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
)

@Serializable
data class NavResponse(
    val code: Int = 0,
    val message: String = "",
    val data: NavData? = null,
)

@Serializable
data class NavData(
    @SerialName("isLogin") val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    val mid: Long = 0,
    @SerialName("wbi_img") val wbiImg: WbiImg? = null,
)

@Serializable
data class WbiImg(
    @SerialName("img_url") val imgUrl: String = "",
    @SerialName("sub_url") val subUrl: String = "",
)

@Serializable
data class QrCodeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: QrCodeData? = null,
)

@Serializable
data class QrCodeData(
    val url: String? = null,
    @SerialName("qrcode_key") val qrcodeKey: String? = null,
)

@Serializable
data class QrPollResponse(
    val code: Int = 0,
    val message: String = "",
    val data: QrPollData? = null,
)

@Serializable
data class QrPollData(
    val url: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val timestamp: Long = 0,
    val code: Int = -1,
    val message: String = "",
)
