package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

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
    /** 1 = 大会员在有效期内。清晰度菜单按它决定要不要列出只有会员才能拿到的档位。 */
    @SerialName("vipStatus") val vipStatus: Int = 0,
    val money: Double? = null,
    @SerialName("level_info") val levelInfo: NavLevelInfo? = null,
    @SerialName("wbi_img") val wbiImg: WbiImg? = null,
)

@Serializable
data class NavLevelInfo(
    @SerialName("current_level") val currentLevel: Int = 0,
    @SerialName("current_min") val currentMin: Int = 0,
    @SerialName("current_exp") val currentExp: Int = 0,
    @SerialName("next_exp") val nextExp: JsonPrimitive? = null,
) {
    /** LV6 时接口返回 `"--"`，其余等级返回升级所需经验数值。 */
    fun nextExpOrNull(): Int? = nextExp?.content?.toIntOrNull()
}

@Serializable
data class NavStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: NavStatData? = null,
)

@Serializable
data class NavStatData(
    val following: Int = 0,
    val follower: Int = 0,
    @SerialName("dynamic_count") val dynamicCount: Int = 0,
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

@Serializable
data class MemberAccountResponse(
    val code: Int = 0,
    val message: String = "",
    val data: MemberAccountData? = null,
)

@Serializable
data class MemberAccountData(
    val mid: Long = 0,
    val uname: String = "",
    val userid: String = "",
    val sign: String = "",
    val birthday: String = "",
    val sex: String = "",
    @SerialName("nick_free") val nickFree: Boolean = false,
    val rank: String = "",
)

@Serializable
data class UpdateFaceResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UpdateFaceData? = null,
)

@Serializable
data class UpdateFaceData(
    val url: String = "",
)
