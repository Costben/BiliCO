package erl.bilico.data.repository

import erl.bilico.data.model.EditableAccountProfile
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 当前登录账号的个人资料读取与修改：昵称（ID）、个人简介、性别与头像上传。
 */
class MemberProfileRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
) {
    /** 标记需要跳转网页版完成验证/改名的错误，方便 UI 提示网页兜底入口。 */
    class WebFallbackException(message: String) : IllegalStateException(message)

    suspend fun fetchEditableProfile(): Result<EditableAccountProfile> = withContext(Dispatchers.IO) {
        runCatching {
            check(session.isLoggedIn()) { "登录后可用" }
            buvid.ensure()
            val response = api.memberAccount()
            if (response.code != 0) {
                error(mapProfileError(response.code, response.message, "读取账号资料失败"))
            }
            val data = response.data ?: error("账号资料为空")
            val current = session.account.value
            val mid = data.mid.takeIf { it > 0L } ?: current?.mid ?: session.mid()
            val face = Formatters.normalizeImageUrl(current?.face.orEmpty())
            val sign = Formatters.decodeHtmlEntities(data.sign)
            val uname = data.uname.ifBlank { current?.name.orEmpty() }
            session.applyUpdatedProfile(name = uname, sign = sign)
            EditableAccountProfile(
                mid = mid,
                uname = uname,
                userid = data.userid,
                face = face,
                sign = sign,
                sex = data.sex.ifBlank { "保密" },
                birthday = data.birthday,
                nickFree = data.nickFree,
            )
        }
    }

    suspend fun updateProfile(
        uname: String? = null,
        sign: String? = null,
        sex: String? = null,
        current: EditableAccountProfile? = null,
    ): Result<EditableAccountProfile> = withContext(Dispatchers.IO) {
        runCatching {
            check(session.isLoggedIn()) { "登录后可用" }
            val csrf = session.csrf()
            check(csrf.isNotBlank()) { "登录校验信息缺失，请重新登录" }
            val nextUname = uname?.trim()
            if (uname != null) {
                require(!nextUname.isNullOrEmpty()) { "昵称不能为空" }
            }
            buvid.ensure()

            val unameChanged = nextUname != null && nextUname != current?.uname
            val sexChanged = sex != null && sex != current?.sex
            val signChanged = sign != null && sign != current?.sign

            if (!unameChanged && !sexChanged && !signChanged) {
                return@runCatching current ?: fetchEditableProfile().getOrThrow()
            }

            if (unameChanged || sexChanged) {
                val fields = buildMap {
                    put("uname", nextUname ?: current?.uname.orEmpty())
                    put("usersign", sign ?: current?.sign.orEmpty())
                    put("sex", (sex ?: current?.sex).orEmpty().ifBlank { "保密" })
                    current?.birthday?.takeIf { it.isNotBlank() }?.let { put("birthday", it) }
                    put("csrf", csrf)
                }
                val resp = api.updateMemberProfile(fields)
                if (resp.code != 0) {
                    val msg = mapProfileError(resp.code, resp.message, "修改资料失败")
                    if (isWebVerificationRequired(resp.code, msg)) {
                        throw WebFallbackException(msg)
                    }
                    error(msg)
                }
                // 部分账号仅在独立签名接口下更新签名：若同时改了简介且与目标不一致，再补一次 sign/update。
                if (sign != null && signChanged) {
                    val after = runCatching { api.memberAccount().data?.sign }.getOrNull()
                    if (after != null && after != sign) {
                        val signResp = api.updateMemberSign(mapOf("user_sign" to sign, "csrf" to csrf))
                        if (signResp.code != 0) {
                            error(mapProfileError(signResp.code, signResp.message, "修改个人简介失败"))
                        }
                    }
                }
            } else if (sign != null && signChanged) {
                val resp = api.updateMemberSign(mapOf("user_sign" to sign, "csrf" to csrf))
                if (resp.code != 0) {
                    val msg = mapProfileError(resp.code, resp.message, "修改个人简介失败")
                    if (isWebVerificationRequired(resp.code, msg)) {
                        throw WebFallbackException(msg)
                    }
                    error(msg)
                }
            }

            session.applyUpdatedProfile(
                name = nextUname,
                sign = sign,
            )
            session.refreshProfile()
            fetchEditableProfile().getOrElse {
                EditableAccountProfile(
                    mid = current?.mid ?: session.mid(),
                    uname = nextUname ?: current?.uname.orEmpty(),
                    userid = current?.userid.orEmpty(),
                    face = session.account.value?.face ?: current?.face.orEmpty(),
                    sign = sign ?: current?.sign.orEmpty(),
                    sex = sex ?: current?.sex.orEmpty().ifBlank { "保密" },
                    birthday = current?.birthday.orEmpty(),
                    nickFree = if (unameChanged) false else (current?.nickFree ?: false),
                )
            }
        }
    }

    suspend fun updateAvatar(
        bytes: ByteArray,
        fileName: String = "avatar.jpg",
        mimeType: String = "image/jpeg",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(session.isLoggedIn()) { "登录后可用" }
            val csrf = session.csrf()
            check(csrf.isNotBlank()) { "登录校验信息缺失，请重新登录" }
            buvid.ensure()

            val part = MultipartBody.Part.createFormData(
                name = "face",
                filename = fileName,
                body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
            )
            val resp = api.updateMemberFace(
                csrf = csrf,
                dopost = "save".toRequestBody(FORM_TEXT),
                displayRank = "10000".toRequestBody(FORM_TEXT),
                face = part,
            )
            if (resp.code != 0) {
                val msg = mapProfileError(resp.code, resp.message, "更换头像失败")
                if (isWebVerificationRequired(resp.code, msg)) {
                    throw WebFallbackException(msg)
                }
                error(msg)
            }
            val rawUrl = Formatters.normalizeImageUrl(resp.data?.url.orEmpty())
            val refreshed = session.refreshProfile()
            val finalUrl = rawUrl.ifBlank { refreshed?.face.orEmpty() }
            if (finalUrl.isNotBlank()) {
                session.applyUpdatedProfile(face = finalUrl)
            }
            finalUrl
        }
    }

    private fun isWebVerificationRequired(code: Int, message: String): Boolean =
        code in setOf(40016, 40017, -400, -412, -509, 61001, 61002) ||
            message.contains("验证") ||
            message.contains("绑定手机")

    private fun mapProfileError(code: Int, rawMessage: String, fallback: String): String {
        val cleaned = rawMessage.trim().takeUnless { it == "0" || it.isEmpty() }
        return when (code) {
            -101 -> "登录已失效，请重新登录"
            -111 -> "登录凭证校验失败，请重新登录"
            40012 -> "头像图片格式不支持，请选择 JPG 或 PNG 图片"
            40013 -> "头像文件过大，请控制在 2MB 以内"
            40014 -> cleaned ?: "该昵称已被占用"
            40015 -> cleaned ?: "内容包含敏感词，请修改后重试"
            40016, 40017 -> cleaned ?: "修改资料需要先绑定手机号"
            else -> cleaned ?: "$fallback($code)"
        }
    }

    private companion object {
        val FORM_TEXT = "text/plain; charset=utf-8".toMediaType()
    }
}
