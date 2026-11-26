package erl.bilico.data.network

import erl.bilico.data.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

private const val API_BASE = "https://api.bilibili.com/"
private const val PASSPORT_BASE = "https://passport.bilibili.com/"

private fun resolveApiUrl(pathOrUrl: String): String =
    if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl else "$API_BASE$pathOrUrl"

internal class BiliApiImpl(private val http: HttpClient) : BiliApi {

    private suspend inline fun <reified T> get(
        pathOrUrl: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): T = http.get(resolveApiUrl(pathOrUrl)) {
        params.forEach { (k, v) -> parameter(k, v) }
        headers.forEach { (k, v) -> header(k, v) }
    }.body()

    private suspend inline fun <reified T> postForm(
        pathOrUrl: String,
        fields: Map<String, String>,
        queryParams: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): T = http.submitForm(
        url = resolveApiUrl(pathOrUrl),
        formParameters = Parameters.build {
            fields.forEach { (k, v) -> append(k, v) }
        },
    ) {
        queryParams.forEach { (k, v) -> parameter(k, v) }
        headers.forEach { (k, v) -> header(k, v) }
    }.body()

    override suspend fun nav(): NavResponse = get("x/web-interface/nav")

    override suspend fun navStat(): NavStatResponse = get("x/web-interface/nav/stat")

    override suspend fun recommend(params: Map<String, String>): RecommendResponse =
        get("x/web-interface/wbi/index/top/feed/rcmd", params)

    override suspend fun appRecommend(buvid: String, params: Map<String, String>): AppFeedResponse =
        get("https://app.bilibili.com/x/v2/feed/index", params, headers = mapOf("buvid" to buvid))

    override suspend fun liveMultipleUsers(uids: List<Long>, attributes: List<String>): LiveMultipleUsersResponse =
        http.get("https://api.live.bilibili.com/user/v3/User/getMultiple") {
            uids.forEach { parameter("uids[]", it) }
            attributes.forEach { parameter("attributes[]", it) }
        }.body()

    override suspend fun appFeedDislike(params: Map<String, String>): BaseResponse =
        get("https://app.bilibili.com/x/feed/dislike", params)

    override suspend fun appFeedDislikeCancel(params: Map<String, String>): BaseResponse =
        get("https://app.bilibili.com/x/feed/dislike/cancel", params)

    override suspend fun videoInfo(bvid: String): VideoInfoResponse =
        get("x/web-interface/view", mapOf("bvid" to bvid))

    override suspend fun videoInfoByAid(aid: Long): VideoInfoResponse =
        get("x/web-interface/view", mapOf("aid" to aid.toString()))

    override suspend fun archiveTags(bvid: String): ArchiveTagsResponse =
        get("x/tag/archive/tags", mapOf("bvid" to bvid))

    override suspend fun playUrl(params: Map<String, String>): PlayUrlResponse =
        get("x/player/wbi/playurl", params)

    override suspend fun playUrlLegacy(params: Map<String, String>): PlayUrlResponse =
        get("x/player/playurl", params)

    override suspend fun playerInfo(params: Map<String, String>): PlayerInfoResponse =
        get("x/player/wbi/v2", params)

    override suspend fun playerInfoLegacy(params: Map<String, String>): PlayerInfoResponse =
        get("x/player/v2", params)

    override suspend fun videoshot(bvid: String, cid: Long, index: Int): VideoshotResponse =
        get("x/player/videoshot", mapOf("bvid" to bvid, "cid" to cid.toString(), "index" to index.toString()))

    override suspend fun mainComments(params: Map<String, String>): ReplyResponse =
        get("x/v2/reply/wbi/main", params)

    override suspend fun legacyComments(params: Map<String, String>): ReplyResponse =
        get("x/v2/reply", params)

    override suspend fun subComments(params: Map<String, String>): ReplyResponse =
        get("x/v2/reply/reply", params)

    override suspend fun addComment(fields: Map<String, String>): AddReplyResponse =
        postForm("x/v2/reply/add", fields)

    override suspend fun emotePanel(business: String): EmotePanelResponse =
        get("x/emote/user/panel/web", mapOf("business" to business))

    override suspend fun uploadBfs(
        bytes: ByteArray,
        fileName: String,
        mime: String,
        category: String,
        biz: String,
        csrf: String,
    ): UploadBfsResponse = http.submitFormWithBinaryData(
        url = resolveApiUrl("x/dynamic/feed/draw/upload_bfs"),
        formData = formData {
            append(
                key = "file_up",
                value = bytes,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, mime.ifBlank { "image/jpeg" })
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName.ifBlank { "image.jpg" }}\"")
                },
            )
            append("category", category)
            append("biz", biz)
            append("csrf", csrf)
        },
    ).body()

    override suspend fun likeComment(fields: Map<String, String>): BaseResponse =
        postForm("x/v2/reply/action", fields)

    override suspend fun hateComment(fields: Map<String, String>): BaseResponse =
        postForm("x/v2/reply/hate", fields)

    override suspend fun deleteComment(fields: Map<String, String>): BaseResponse =
        postForm("x/v2/reply/del", fields)

    override suspend fun heartbeat(fields: Map<String, String>): BaseResponse =
        postForm("x/click-interface/web/heartbeat", fields)

    override suspend fun sendDanmaku(fields: Map<String, String>): SendDanmakuResponse =
        postForm("x/v2/dm/post", fields)

    override suspend fun reportDanmaku(fields: Map<String, String>): BaseResponse =
        postForm("x/dm/report/add", fields)

    override suspend fun activateBuvid(payload: Map<String, String>): BaseResponse =
        http.post(resolveApiUrl("x/internal/gaia-gateway/ExClimbWuzhi")) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

    override suspend fun dynamicFeed(params: Map<String, String>): DynamicFeedResponse =
        get("x/polymer/web-dynamic/v1/feed/all", params)

    override suspend fun spaceDynamicFeed(params: Map<String, String>): DynamicFeedResponse =
        get("x/polymer/web-dynamic/v1/feed/space", params)

    override suspend fun createDynamic(csrf: String, body: JsonObject): CreateDynamicResponse =
        http.post(resolveApiUrl("x/dynamic/feed/create/dyn")) {
            parameter("csrf", csrf)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    override suspend fun removeDynamic(csrf: String, body: JsonObject): BaseResponse =
        http.post(resolveApiUrl("x/dynamic/feed/operate/remove")) {
            parameter("csrf", csrf)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    override suspend fun thumbDynamic(csrf: String, body: JsonObject): BaseResponse =
        http.post(resolveApiUrl("x/dynamic/feed/dyn/thumb")) {
            parameter("csrf", csrf)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    override suspend fun articleView(params: Map<String, String>): ArticleViewResponse =
        get("x/article/view", params)

    override suspend fun dynamicDetail(params: Map<String, String>): DynamicDetailResponse =
        get("x/polymer/web-dynamic/v1/detail", params)

    override suspend fun opusDetail(params: Map<String, String>): DynamicDetailResponse =
        get("x/polymer/web-dynamic/v1/opus/detail", params)

    override suspend fun history(params: Map<String, String>): HistoryResponse =
        get("x/web-interface/history/cursor", params)

    override suspend fun historySearch(params: Map<String, String>): HistoryResponse =
        get("x/web-interface/history/search", params)

    override suspend fun clearHistory(fields: Map<String, String>): BaseResponse =
        postForm("x/v2/history/clear", fields)

    override suspend fun historyShadowStatus(): HistoryShadowResponse =
        get("x/v2/history/shadow")

    override suspend fun setHistoryShadow(fields: Map<String, String>): BaseResponse =
        postForm("x/v2/history/shadow/set", fields)

    override suspend fun archiveStat(bvid: String): ArchiveStatResponse =
        get("x/web-interface/archive/stat", mapOf("bvid" to bvid))

    override suspend fun liveRooms(params: Map<String, String>): LiveListResponse =
        get("https://api.live.bilibili.com/room/v3/area/getRoomList", params)

    override suspend fun liveFollowing(params: Map<String, String>): LiveFollowingResponse =
        get("https://api.live.bilibili.com/xlive/web-ucenter/user/following", params)

    override suspend fun liveRoomInfo(roomId: Long): LiveRoomInfoResponse =
        get("https://api.live.bilibili.com/room/v1/Room/get_info", mapOf("room_id" to roomId.toString()))

    override suspend fun liveRoomDetail(roomId: Long): LiveRoomDetailResponse =
        get("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom", mapOf("room_id" to roomId.toString()))

    override suspend fun livePlayUrl(params: Map<String, String>): LivePlayUrlResponse =
        get("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo", params)

    override suspend fun liveDanmakuInfo(params: Map<String, String>): LiveDanmakuInfoResponse =
        get("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo", params)

    override suspend fun liveContributionRank(params: Map<String, String>): LiveContributionRankResponse =
        get("https://api.live.bilibili.com/xlive/general-interface/v1/rank/queryContributionRank", params)

    override suspend fun liveSuperChats(roomId: Long): LiveSuperChatResponse =
        get("https://api.live.bilibili.com/av/v1/SuperChat/getMessageList", mapOf("room_id" to roomId.toString()))

    override suspend fun sendLiveDanmaku(fields: Map<String, String>): BaseResponse =
        postForm(
            "https://api.live.bilibili.com/msg/send",
            fields,
            headers = mapOf(
                HttpHeaders.Origin to "https://live.bilibili.com",
                HttpHeaders.Referrer to "https://live.bilibili.com/",
            ),
        )

    override suspend fun searchSquare(params: Map<String, String>): SearchSquareResponse =
        get("x/web-interface/wbi/search/square", params)

    override suspend fun searchSquareLegacy(limit: Int): SearchSquareResponse =
        get("x/web-interface/search/square", mapOf("limit" to limit.toString()))

    override suspend fun searchSuggest(term: String): SearchSuggestResponse =
        get("x/web-interface/suggest", mapOf("term" to term))

    override suspend fun searchAll(params: Map<String, String>): SearchAllResponse =
        get(
            "x/web-interface/wbi/search/all/v2",
            params,
            headers = mapOf(
                HttpHeaders.Origin to "https://search.bilibili.com",
                HttpHeaders.Referrer to "https://search.bilibili.com/",
            ),
        )

    override suspend fun searchType(params: Map<String, String>): SearchTypeResponse =
        get(
            "x/web-interface/wbi/search/type",
            params,
            headers = mapOf(
                HttpHeaders.Origin to "https://search.bilibili.com",
                HttpHeaders.Referrer to "https://search.bilibili.com/",
            ),
        )

    override suspend fun spaceInfo(params: Map<String, String>): SpaceInfoResponse =
        get("x/space/wbi/acc/info", params)

    override suspend fun spaceLiveRoom(mid: Long): SpaceLiveRoomOldResponse =
        get("https://api.live.bilibili.com/room/v1/Room/getRoomInfoOld", mapOf("mid" to mid.toString()))

    override suspend fun spaceVideos(params: Map<String, String>): SpaceVideoResponse =
        get("x/space/wbi/arc/search", params)

    override suspend fun spaceSeasonsSeries(params: Map<String, String>): SpaceSeasonsSeriesResponse =
        get("x/polymer/web-space/home/seasons_series", params)

    override suspend fun spaceSeasonArchives(params: Map<String, String>): SpaceSeasonArchivesResponse =
        get("x/polymer/web-space/seasons_archives_list", params)

    override suspend fun spaceSeriesArchives(params: Map<String, String>): SpaceSeriesArchivesResponse =
        get("x/series/archives", params)

    override suspend fun spaceLikedVideos(mid: Long): SpaceLikedVideosResponse =
        get("x/space/like/video", mapOf("vmid" to mid.toString()))

    override suspend fun spaceOpusFeed(params: Map<String, String>): SpaceOpusFeedResponse =
        get("x/polymer/web-dynamic/v1/opus/feed/space", params)

    override suspend fun relationStat(mid: Long): RelationStatResponse =
        get("x/relation/stat", mapOf("vmid" to mid.toString()))

    override suspend fun upStat(mid: Long): UpStatResponse =
        get("x/space/upstat", mapOf("mid" to mid.toString()))

    override suspend fun favFolders(
        mid: Long,
        rid: Long?,
        type: Int?,
        webLocation: String,
    ): FavFolderResponse = get(
        "x/v3/fav/folder/created/list-all",
        buildMap {
            put("up_mid", mid.toString())
            rid?.let { put("rid", it.toString()) }
            type?.let { put("type", it.toString()) }
            put("web_location", webLocation)
        },
    )

    override suspend fun favResources(params: Map<String, String>): FavResourceResponse =
        get("x/v3/fav/resource/list", params)

    override suspend fun watchLater(params: Map<String, String>): WatchLaterResponse =
        get("x/v2/history/toview/web", params)

    override suspend fun followings(params: Map<String, String>): FollowingsResponse =
        get("x/relation/followings", params)

    override suspend fun danmakuSegment(type: Int, oid: Long, segmentIndex: Int): ByteArray =
        http.get("https://api.bilibili.com/x/v2/dm/web/seg.so") {
            parameter("type", type)
            parameter("oid", oid)
            parameter("segment_index", segmentIndex)
        }.bodyAsBytes()

    override suspend fun pgcSeason(params: Map<String, String>): PgcSeasonResponse =
        get("pgc/view/web/season", params)

    override suspend fun pgcPlayUrl(params: Map<String, String>): String =
        http.get(resolveApiUrl("pgc/player/web/v2/playurl")) {
            params.forEach { (k, v) -> parameter(k, v) }
        }.bodyAsText()

    override suspend fun pgcPlayUrlLegacy(params: Map<String, String>): String =
        http.get(resolveApiUrl("pgc/player/web/playurl")) {
            params.forEach { (k, v) -> parameter(k, v) }
        }.bodyAsText()

    override suspend fun pgcRank(params: Map<String, String>): PgcRankResponse =
        get("pgc/web/rank/list", params)

    override suspend fun pgcSeasonRank(params: Map<String, String>): PgcRankResponse =
        get("pgc/season/rank/web/list", params)

    override suspend fun bangumiFollow(params: Map<String, String>): BangumiFollowResponse =
        get("x/space/bangumi/follow/list", params)

    override suspend fun followBangumi(fields: Map<String, String>): BaseResponse =
        postForm("pgc/web/follow/add", fields)

    override suspend fun unfollowBangumi(fields: Map<String, String>): BaseResponse =
        postForm("pgc/web/follow/del", fields)

    override suspend fun ranking(params: Map<String, String>): RankingResponse =
        get("x/web-interface/ranking/v2", params)

    override suspend fun popular(params: Map<String, String>): RankingResponse =
        get("x/web-interface/popular", params)

    override suspend fun related(bvid: String): RelatedResponse =
        get("x/web-interface/archive/related", mapOf("bvid" to bvid))

    override suspend fun videoRelation(aid: Long): VideoRelationResponse =
        get("x/web-interface/archive/relation", mapOf("aid" to aid.toString()))

    override suspend fun userRelation(fid: Long): UserRelationResponse =
        get("x/relation", mapOf("fid" to fid.toString()))

    override suspend fun likeVideo(fields: Map<String, String>): BaseResponse =
        postForm("x/web-interface/archive/like", fields)

    override suspend fun tripleVideo(fields: Map<String, String>): TripleResponse =
        postForm("x/web-interface/archive/like/triple", fields)

    override suspend fun coinVideo(fields: Map<String, String>): BaseResponse =
        postForm("x/web-interface/coin/add", fields)

    override suspend fun favoriteResource(fields: Map<String, String>): BaseResponse =
        postForm("x/v3/fav/resource/deal", fields)

    override suspend fun batchDelFavorite(fields: Map<String, String>): BaseResponse =
        postForm("x/v3/fav/resource/batch-del", fields)

    override suspend fun createFavFolder(fields: Map<String, String>): FavFolderCreateResponse =
        postForm("x/v3/fav/folder/add", fields)

    override suspend fun addWatchLater(fields: Map<String, String>): BaseResponse =
        postForm("x/v2/history/toview/add", fields)

    override suspend fun delWatchLater(fields: Map<String, String>): BaseResponse =
        postForm("x/v2/history/toview/del", fields)

    override suspend fun modifyRelation(fields: Map<String, String>): BaseResponse =
        postForm("x/relation/modify", fields)

    override suspend fun memberCard(mid: Long, photo: Boolean): MemberCardResponse =
        get("x/web-interface/card", mapOf("mid" to mid.toString(), "photo" to photo.toString()))

    override suspend fun blackList(params: Map<String, String>): BlackListResponse =
        get("x/relation/blacks", params)

    override suspend fun appealTags(): AppealTagsResponse =
        get("x/web-interface/archive/appeal/tags")

    override suspend fun reportVideo(buid: String, fields: Map<String, String>): BaseResponse =
        postForm("x/web-interface/appeal/v2/submit", fields, headers = mapOf("buid" to buid))

    override suspend fun reportMember(fields: Map<String, String>): MemberReportResponse =
        postForm(
            "https://space.bilibili.com/ajax/report/add",
            fields,
            headers = mapOf(
                HttpHeaders.Origin to "https://space.bilibili.com",
                HttpHeaders.Referrer to "https://space.bilibili.com/",
            ),
        )

    override suspend fun reportDynamic(csrf: String, fields: Map<String, String>): BaseResponse =
        postForm("x/dynamic/feed/dynamic_report/add", fields, queryParams = mapOf("csrf" to csrf))

    override suspend fun reportComment(fields: Map<String, String>): BaseResponse =
        postForm("x/v2/reply/report", fields)

    override suspend fun messageUnread(params: Map<String, String>): MessageUnreadResponse =
        get("https://api.bilibili.com/x/msgfeed/unread", params)

    override suspend fun imSingleUnread(params: Map<String, String>): ImSingleUnreadResponse =
        get("https://api.vc.bilibili.com/session_svr/v1/session_svr/single_unread", params)

    override suspend fun messageReply(params: Map<String, String>): MessageReplyResponse =
        get("https://api.bilibili.com/x/msgfeed/reply", params)

    override suspend fun messageAt(params: Map<String, String>): MessageAtResponse =
        get("https://api.bilibili.com/x/msgfeed/at", params)

    override suspend fun messageLike(params: Map<String, String>): MessageLikeResponse =
        get("https://api.bilibili.com/x/msgfeed/like", params)

    override suspend fun systemNotices(params: Map<String, String>): SystemNoticeResponse =
        get("https://message.bilibili.com/x/sys-msg/query_notify_list", params)

    override suspend fun imSessions(params: Map<String, String>): ImSessionListResponse =
        get("https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions", params)

    override suspend fun imFetchMsgs(params: Map<String, String>): ImFetchMsgsResponse =
        get("https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs", params)

    override suspend fun imSendMsg(params: Map<String, String>): ImSendMsgResponse =
        postForm("https://api.vc.bilibili.com/web_im/v1/web_im/send_msg", params)

    override suspend fun imUpdateAck(params: Map<String, String>): BaseResponse =
        postForm("https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack", params)

    override suspend fun voteInfo(voteId: Long): VoteInfoResponse =
        get("https://api.vc.bilibili.com/vote_svr/v1/vote_svr/vote_info", mapOf("vote_id" to voteId.toString()))

    override suspend fun doVote(fields: Map<String, String>): BaseResponse =
        postForm("https://api.vc.bilibili.com/vote_svr/v1/vote_svr/do_vote", fields)

    override suspend fun memberAccount(): MemberAccountResponse =
        get(
            "x/member/web/account",
            headers = mapOf(
                HttpHeaders.Origin to "https://account.bilibili.com",
                HttpHeaders.Referrer to "https://account.bilibili.com/account/setting",
            ),
        )

    override suspend fun updateMemberProfile(fields: Map<String, String>): BaseResponse =
        postForm(
            "x/member/web/update",
            fields,
            headers = mapOf(
                HttpHeaders.Origin to "https://account.bilibili.com",
                HttpHeaders.Referrer to "https://account.bilibili.com/account/setting",
            ),
        )

    override suspend fun updateMemberSign(fields: Map<String, String>): BaseResponse =
        postForm(
            "x/member/web/sign/update",
            fields,
            headers = mapOf(
                HttpHeaders.Origin to "https://account.bilibili.com",
                HttpHeaders.Referrer to "https://account.bilibili.com/account/setting",
            ),
        )

    override suspend fun updateMemberFace(
        csrf: String,
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): UpdateFaceResponse = http.submitFormWithBinaryData(
        url = resolveApiUrl("x/member/web/face/update"),
        formData = formData {
            append("dopost", "save")
            append("Displayrank", "10000")
            append(
                key = "face",
                value = bytes,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, mime.ifBlank { "image/jpeg" })
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName.ifBlank { "avatar.jpg" }}\"")
                },
            )
        },
    ) {
        parameter("csrf", csrf)
        header(HttpHeaders.Origin, "https://account.bilibili.com")
        header(HttpHeaders.Referrer, "https://account.bilibili.com/account/face")
    }.body()
}

internal class PassportApiImpl(private val http: HttpClient) : PassportApi {
    override suspend fun generateQrCode(): QrCodeResponse =
        http.get("${PASSPORT_BASE}x/passport-login/web/qrcode/generate").body()

    override suspend fun pollQrCode(key: String): QrPollResponse =
        http.get("${PASSPORT_BASE}x/passport-login/web/qrcode/poll") {
            parameter("qrcode_key", key)
        }.body()

    override suspend fun refreshToken(params: Map<String, String>): TvRefreshResponse =
        http.submitForm(
            url = "${PASSPORT_BASE}x/passport-tv-login/h5/refresh",
            formParameters = Parameters.build {
                params.forEach { (k, v) -> append(k, v) }
            },
        ).body()

    override suspend fun generateTvAuthCode(params: Map<String, String>): TvAuthCodeResponse =
        http.submitForm(
            url = "${PASSPORT_BASE}x/passport-tv-login/qrcode/auth_code",
            formParameters = Parameters.build {
                params.forEach { (k, v) -> append(k, v) }
            },
        ).body()

    override suspend fun confirmTvQrCode(params: Map<String, String>): BaseResponse =
        http.submitForm(
            url = "${PASSPORT_BASE}x/passport-tv-login/h5/qrcode/confirm",
            formParameters = Parameters.build {
                params.forEach { (k, v) -> append(k, v) }
            },
        ) {
            header(HttpHeaders.Origin, "https://passport.bilibili.com")
            header(HttpHeaders.Referrer, "https://passport.bilibili.com/")
        }.body()

    override suspend fun pollTvQrCode(params: Map<String, String>): TvRefreshResponse =
        http.submitForm(
            url = "${PASSPORT_BASE}x/passport-tv-login/qrcode/poll",
            formParameters = Parameters.build {
                params.forEach { (k, v) -> append(k, v) }
            },
        ).body()
}
