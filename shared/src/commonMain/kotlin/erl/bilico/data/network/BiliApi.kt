package erl.bilico.data.network

import erl.bilico.data.model.AddReplyResponse
import erl.bilico.data.model.AppealTagsResponse
import erl.bilico.data.model.AppFeedResponse
import erl.bilico.data.model.ArchiveStatResponse
import erl.bilico.data.model.ArchiveTagsResponse
import erl.bilico.data.model.ArticleViewResponse
import erl.bilico.data.model.BangumiFollowResponse
import erl.bilico.data.model.BaseResponse
import erl.bilico.data.model.BlackListResponse
import erl.bilico.data.model.CreateDynamicResponse
import erl.bilico.data.model.DynamicDetailResponse
import erl.bilico.data.model.DynamicFeedResponse
import erl.bilico.data.model.EmotePanelResponse
import erl.bilico.data.model.FavFolderCreateResponse
import erl.bilico.data.model.FavFolderResponse
import erl.bilico.data.model.FavResourceResponse
import erl.bilico.data.model.FollowingsResponse
import erl.bilico.data.model.HistoryResponse
import erl.bilico.data.model.HistoryShadowResponse
import erl.bilico.data.model.ImFetchMsgsResponse
import erl.bilico.data.model.ImSendMsgResponse
import erl.bilico.data.model.ImSessionListResponse
import erl.bilico.data.model.ImSingleUnreadResponse
import erl.bilico.data.model.LiveContributionRankResponse
import erl.bilico.data.model.LiveDanmakuInfoResponse
import erl.bilico.data.model.LiveFollowingResponse
import erl.bilico.data.model.LiveListResponse
import erl.bilico.data.model.LiveMultipleUsersResponse
import erl.bilico.data.model.LivePlayUrlResponse
import erl.bilico.data.model.LiveRoomDetailResponse
import erl.bilico.data.model.LiveRoomInfoResponse
import erl.bilico.data.model.LiveSuperChatResponse
import erl.bilico.data.model.MemberAccountResponse
import erl.bilico.data.model.MemberCardResponse
import erl.bilico.data.model.MemberReportResponse
import erl.bilico.data.model.MessageAtResponse
import erl.bilico.data.model.MessageLikeResponse
import erl.bilico.data.model.MessageReplyResponse
import erl.bilico.data.model.MessageUnreadResponse
import erl.bilico.data.model.NavResponse
import erl.bilico.data.model.NavStatResponse
import erl.bilico.data.model.PgcRankResponse
import erl.bilico.data.model.PgcSeasonResponse
import erl.bilico.data.model.PlayUrlResponse
import erl.bilico.data.model.PlayerInfoResponse
import erl.bilico.data.model.QrCodeResponse
import erl.bilico.data.model.QrPollResponse
import erl.bilico.data.model.RankingResponse
import erl.bilico.data.model.RecommendResponse
import erl.bilico.data.model.RelatedResponse
import erl.bilico.data.model.RelationStatResponse
import erl.bilico.data.model.ReplyResponse
import erl.bilico.data.model.SearchAllResponse
import erl.bilico.data.model.SearchSquareResponse
import erl.bilico.data.model.SearchSuggestResponse
import erl.bilico.data.model.SearchTypeResponse
import erl.bilico.data.model.SendDanmakuResponse
import erl.bilico.data.model.SpaceInfoResponse
import erl.bilico.data.model.SpaceLikedVideosResponse
import erl.bilico.data.model.SpaceLiveRoomOldResponse
import erl.bilico.data.model.SpaceOpusFeedResponse
import erl.bilico.data.model.SpaceSeasonArchivesResponse
import erl.bilico.data.model.SpaceSeasonsSeriesResponse
import erl.bilico.data.model.SpaceSeriesArchivesResponse
import erl.bilico.data.model.SpaceVideoResponse
import erl.bilico.data.model.SystemNoticeResponse
import erl.bilico.data.model.TripleResponse
import erl.bilico.data.model.UpStatResponse
import erl.bilico.data.model.UpdateFaceResponse
import erl.bilico.data.model.UploadBfsResponse
import erl.bilico.data.model.UserRelationResponse
import erl.bilico.data.model.VideoInfoResponse
import erl.bilico.data.model.VideoRelationResponse
import erl.bilico.data.model.VideoshotResponse
import erl.bilico.data.model.VoteInfoResponse
import erl.bilico.data.model.WatchLaterResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

interface BiliApi {
    suspend fun nav(): NavResponse

    suspend fun navStat(): NavStatResponse

    suspend fun recommend(params: Map<String, String>): RecommendResponse

    suspend fun appRecommend(buvid: String, params: Map<String, String>): AppFeedResponse

    suspend fun liveMultipleUsers(
        uids: List<Long>,
        attributes: List<String> = listOf("info"),
    ): LiveMultipleUsersResponse

    suspend fun appFeedDislike(params: Map<String, String>): BaseResponse

    suspend fun appFeedDislikeCancel(params: Map<String, String>): BaseResponse

    suspend fun videoInfo(bvid: String): VideoInfoResponse

    suspend fun videoInfoByAid(aid: Long): VideoInfoResponse

    suspend fun archiveTags(bvid: String): ArchiveTagsResponse

    suspend fun playUrl(params: Map<String, String>): PlayUrlResponse

    suspend fun playUrlLegacy(params: Map<String, String>): PlayUrlResponse

    suspend fun playerInfo(params: Map<String, String>): PlayerInfoResponse

    suspend fun playerInfoLegacy(params: Map<String, String>): PlayerInfoResponse

    suspend fun videoshot(bvid: String, cid: Long, index: Int = 1): VideoshotResponse

    suspend fun mainComments(params: Map<String, String>): ReplyResponse

    suspend fun legacyComments(params: Map<String, String>): ReplyResponse

    suspend fun subComments(params: Map<String, String>): ReplyResponse

    suspend fun addComment(fields: Map<String, String>): AddReplyResponse

    suspend fun emotePanel(business: String = "reply"): EmotePanelResponse

    suspend fun uploadBfs(
        bytes: ByteArray,
        fileName: String,
        mime: String,
        category: String,
        biz: String,
        csrf: String,
    ): UploadBfsResponse

    suspend fun likeComment(fields: Map<String, String>): BaseResponse

    suspend fun hateComment(fields: Map<String, String>): BaseResponse

    suspend fun deleteComment(fields: Map<String, String>): BaseResponse

    suspend fun heartbeat(fields: Map<String, String>): BaseResponse

    suspend fun sendDanmaku(fields: Map<String, String>): SendDanmakuResponse

    suspend fun reportDanmaku(fields: Map<String, String>): BaseResponse

    suspend fun activateBuvid(payload: Map<String, String>): BaseResponse

    suspend fun dynamicFeed(params: Map<String, String>): DynamicFeedResponse

    suspend fun spaceDynamicFeed(params: Map<String, String>): DynamicFeedResponse

    suspend fun createDynamic(csrf: String, body: JsonObject): CreateDynamicResponse

    suspend fun removeDynamic(csrf: String, body: JsonObject): BaseResponse

    suspend fun thumbDynamic(csrf: String, body: JsonObject): BaseResponse

    suspend fun articleView(params: Map<String, String>): ArticleViewResponse

    suspend fun dynamicDetail(params: Map<String, String>): DynamicDetailResponse

    suspend fun opusDetail(params: Map<String, String>): DynamicDetailResponse

    suspend fun history(params: Map<String, String>): HistoryResponse

    suspend fun historySearch(params: Map<String, String>): HistoryResponse

    suspend fun clearHistory(fields: Map<String, String>): BaseResponse

    suspend fun historyShadowStatus(): HistoryShadowResponse

    suspend fun setHistoryShadow(fields: Map<String, String>): BaseResponse

    suspend fun archiveStat(bvid: String): ArchiveStatResponse

    suspend fun liveRooms(params: Map<String, String>): LiveListResponse

    suspend fun liveFollowing(params: Map<String, String>): LiveFollowingResponse

    suspend fun liveRoomInfo(roomId: Long): LiveRoomInfoResponse

    suspend fun liveRoomDetail(roomId: Long): LiveRoomDetailResponse

    suspend fun livePlayUrl(params: Map<String, String>): LivePlayUrlResponse

    suspend fun liveDanmakuInfo(params: Map<String, String>): LiveDanmakuInfoResponse

    suspend fun liveContributionRank(params: Map<String, String>): LiveContributionRankResponse

    suspend fun liveSuperChats(roomId: Long): LiveSuperChatResponse

    suspend fun sendLiveDanmaku(fields: Map<String, String>): BaseResponse

    suspend fun searchSquare(params: Map<String, String>): SearchSquareResponse

    suspend fun searchSquareLegacy(limit: Int = 10): SearchSquareResponse

    suspend fun searchSuggest(term: String): SearchSuggestResponse

    suspend fun searchAll(params: Map<String, String>): SearchAllResponse

    suspend fun searchType(params: Map<String, String>): SearchTypeResponse

    suspend fun spaceInfo(params: Map<String, String>): SpaceInfoResponse

    suspend fun spaceLiveRoom(mid: Long): SpaceLiveRoomOldResponse

    suspend fun spaceVideos(params: Map<String, String>): SpaceVideoResponse

    suspend fun spaceSeasonsSeries(params: Map<String, String>): SpaceSeasonsSeriesResponse

    suspend fun spaceSeasonArchives(params: Map<String, String>): SpaceSeasonArchivesResponse

    suspend fun spaceSeriesArchives(params: Map<String, String>): SpaceSeriesArchivesResponse

    suspend fun spaceLikedVideos(mid: Long): SpaceLikedVideosResponse

    suspend fun spaceOpusFeed(params: Map<String, String>): SpaceOpusFeedResponse

    suspend fun relationStat(mid: Long): RelationStatResponse

    suspend fun upStat(mid: Long): UpStatResponse

    suspend fun favFolders(
        mid: Long,
        rid: Long? = null,
        type: Int? = null,
        webLocation: String = "333.1387",
    ): FavFolderResponse

    suspend fun favResources(params: Map<String, String>): FavResourceResponse

    suspend fun watchLater(params: Map<String, String>): WatchLaterResponse

    suspend fun followings(params: Map<String, String>): FollowingsResponse

    suspend fun danmakuSegment(type: Int = 1, oid: Long, segmentIndex: Int): ByteArray

    suspend fun pgcSeason(params: Map<String, String>): PgcSeasonResponse

    suspend fun pgcPlayUrl(params: Map<String, String>): String

    suspend fun pgcPlayUrlLegacy(params: Map<String, String>): String

    suspend fun pgcRank(params: Map<String, String>): PgcRankResponse

    suspend fun pgcSeasonRank(params: Map<String, String>): PgcRankResponse

    suspend fun bangumiFollow(params: Map<String, String>): BangumiFollowResponse

    suspend fun followBangumi(fields: Map<String, String>): BaseResponse

    suspend fun unfollowBangumi(fields: Map<String, String>): BaseResponse

    suspend fun ranking(params: Map<String, String>): RankingResponse

    suspend fun popular(params: Map<String, String>): RankingResponse

    suspend fun related(bvid: String): RelatedResponse

    suspend fun videoRelation(aid: Long): VideoRelationResponse

    suspend fun userRelation(fid: Long): UserRelationResponse

    suspend fun likeVideo(fields: Map<String, String>): BaseResponse

    suspend fun tripleVideo(fields: Map<String, String>): TripleResponse

    suspend fun coinVideo(fields: Map<String, String>): BaseResponse

    suspend fun favoriteResource(fields: Map<String, String>): BaseResponse

    suspend fun batchDelFavorite(fields: Map<String, String>): BaseResponse

    suspend fun createFavFolder(fields: Map<String, String>): FavFolderCreateResponse

    suspend fun addWatchLater(fields: Map<String, String>): BaseResponse

    suspend fun delWatchLater(fields: Map<String, String>): BaseResponse

    suspend fun modifyRelation(fields: Map<String, String>): BaseResponse

    suspend fun memberCard(mid: Long, photo: Boolean = false): MemberCardResponse

    suspend fun blackList(params: Map<String, String>): BlackListResponse

    suspend fun appealTags(): AppealTagsResponse

    suspend fun reportVideo(buid: String, fields: Map<String, String>): BaseResponse

    suspend fun reportMember(fields: Map<String, String>): MemberReportResponse

    suspend fun reportDynamic(csrf: String, fields: Map<String, String>): BaseResponse

    suspend fun reportComment(fields: Map<String, String>): BaseResponse

    suspend fun messageUnread(params: Map<String, String>): MessageUnreadResponse

    suspend fun imSingleUnread(params: Map<String, String>): ImSingleUnreadResponse

    suspend fun messageReply(params: Map<String, String>): MessageReplyResponse

    suspend fun messageAt(params: Map<String, String>): MessageAtResponse

    suspend fun messageLike(params: Map<String, String>): MessageLikeResponse

    suspend fun systemNotices(params: Map<String, String>): SystemNoticeResponse

    suspend fun imSessions(params: Map<String, String>): ImSessionListResponse

    suspend fun imFetchMsgs(params: Map<String, String>): ImFetchMsgsResponse

    suspend fun imSendMsg(params: Map<String, String>): ImSendMsgResponse

    suspend fun imUpdateAck(params: Map<String, String>): BaseResponse

    suspend fun voteInfo(voteId: Long): VoteInfoResponse

    suspend fun doVote(fields: Map<String, String>): BaseResponse

    suspend fun memberAccount(): MemberAccountResponse

    suspend fun updateMemberProfile(fields: Map<String, String>): BaseResponse

    suspend fun updateMemberSign(fields: Map<String, String>): BaseResponse

    suspend fun updateMemberFace(
        csrf: String,
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): UpdateFaceResponse
}

interface PassportApi {
    suspend fun generateQrCode(): QrCodeResponse

    suspend fun pollQrCode(key: String): QrPollResponse

    suspend fun refreshToken(params: Map<String, String>): TvRefreshResponse

    suspend fun generateTvAuthCode(params: Map<String, String>): TvAuthCodeResponse

    suspend fun confirmTvQrCode(params: Map<String, String>): BaseResponse

    suspend fun pollTvQrCode(params: Map<String, String>): TvRefreshResponse
}

@Serializable
data class TvAuthCodeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TvAuthCodeData? = null,
)

@Serializable
data class TvAuthCodeData(
    @SerialName("auth_code") val authCode: String = "",
    val url: String = "",
)

@Serializable
data class TvRefreshResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TvRefreshData? = null,
)

@Serializable
data class TvRefreshData(
    val mid: Long = 0,
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("cookie_info") val cookieInfo: TvCookieInfo? = null,
)

@Serializable
data class TvCookieInfo(
    val cookies: List<TvCookieItem> = emptyList(),
)

@Serializable
data class TvCookieItem(
    val name: String = "",
    val value: String = "",
)
