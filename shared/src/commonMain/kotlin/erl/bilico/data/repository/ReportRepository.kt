package erl.bilico.data.repository

import erl.bilico.data.model.AppealTag
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.CookieStore
import erl.bilico.data.session.SessionRepository
import erl.bilico.platform.ioDispatcher
import kotlinx.coroutines.withContext

/** 举报仓库：覆盖视频、UP 主、动态、评论四类举报接口（对齐 PiliPlus）。 */
class ReportRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val cookies: CookieStore,
    private val session: SessionRepository,
) {
    suspend fun appealTags(): Result<List<AppealTag>> = withContext(ioDispatcher) {
        runCatching {
            buvid.ensure()
            val response = api.appealTags()
            if (response.code == 0 && response.data.isNotEmpty()) {
                response.data
            } else {
                DEFAULT_VIDEO_TAGS
            }
        }.recover { DEFAULT_VIDEO_TAGS }
    }

    suspend fun reportVideo(
        aid: Long,
        tid: Int,
        desc: String,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(aid > 0L) { "无效视频 ID" }
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            buvid.ensure()
            val buid = cookies.get("Buid").orEmpty().ifBlank {
                cookies.get("buvid3").orEmpty()
            }
            if (cookies.get("Buid").isNullOrBlank() && buid.isNotBlank()) {
                cookies.put("Buid", buid)
            }
            val response = api.reportVideo(
                buid = buid,
                fields = mapOf(
                    "csrf" to csrf,
                    "aid" to aid.toString(),
                    "tid" to tid.toString(),
                    "desc" to desc,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "举报失败(${response.code})" })
            }
        }
    }

    suspend fun reportMember(
        mid: Long,
        reasonCodes: List<Int>,
        reasonV2: Int? = null,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(mid > 0L) { "无效用户 ID" }
            require(reasonCodes.isNotEmpty()) { "请至少选择一项违规类型" }
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            buvid.ensure()
            val fields = buildMap {
                put("mid", mid.toString())
                put("reason", reasonCodes.joinToString(","))
                if (reasonV2 != null) put("reason_v2", reasonV2.toString())
                put("csrf", csrf)
            }
            val response = api.reportMember(fields)
            val ok = response.status == true || (response.status == null && response.code == 0)
            if (!ok) {
                error(response.message.ifBlank { "举报失败(${response.code})" })
            }
        }
    }

    suspend fun reportDynamic(
        accusedUid: Long,
        dynamicId: String,
        reasonType: Int,
        reasonDesc: String = "",
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(accusedUid > 0L && dynamicId.isNotBlank()) { "无效动态参数" }
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            buvid.ensure()
            val response = api.reportDynamic(
                csrf = csrf,
                fields = mapOf(
                    "accused_uid" to accusedUid.toString(),
                    "dynamic_id" to dynamicId,
                    "reason_type" to reasonType.toString(),
                    "reason_desc" to reasonDesc,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "举报失败(${response.code})" })
            }
        }
    }

    suspend fun reportComment(
        oid: Long,
        rpid: Long,
        reason: Int,
        content: String = "",
        addBlacklist: Boolean = false,
        type: Int = 1,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(oid > 0L && rpid > 0L) { "无效评论参数" }
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            buvid.ensure()
            val fields = buildMap {
                put("add_blacklist", addBlacklist.toString())
                put("csrf", csrf)
                put("gaia_source", "main_h5")
                put("oid", oid.toString())
                put("platform", "android")
                put("reason", reason.toString())
                put("rpid", rpid.toString())
                put("scene", "main")
                put("type", type.toString())
                if (content.isNotBlank()) put("content", content)
            }
            val response = api.reportComment(fields)
            if (response.code != 0) {
                error(response.message.ifBlank { "举报失败(${response.code})" })
            }
        }
    }

    private companion object {
        val DEFAULT_VIDEO_TAGS = listOf(
            AppealTag(tid = 10019, name = "涉政谣言 / 不实信息"),
            AppealTag(tid = 10010, name = "色情低俗"),
            AppealTag(tid = 10011, name = "赌博诈骗"),
            AppealTag(tid = 10012, name = "违法违规"),
            AppealTag(tid = 10013, name = "人身攻击"),
            AppealTag(tid = 10014, name = "侵犯隐私"),
            AppealTag(tid = 10015, name = "虚假宣传 / 广告"),
            AppealTag(tid = 10016, name = "血腥暴力"),
            AppealTag(tid = 10017, name = "引战 / 不良引导"),
            AppealTag(tid = 10018, name = "抄袭 / 侵权"),
        )
    }
}
