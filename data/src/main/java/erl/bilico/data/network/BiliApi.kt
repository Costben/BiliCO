package erl.bilico.data.network

import erl.bilico.data.model.AddReplyResponse
import erl.bilico.data.model.ArticleViewResponse
import erl.bilico.data.model.BangumiFollowResponse
import erl.bilico.data.model.BaseResponse
import erl.bilico.data.model.DynamicDetailResponse
import erl.bilico.data.model.DynamicFeedResponse
import erl.bilico.data.model.MessageAtResponse
import erl.bilico.data.model.MessageLikeResponse
import erl.bilico.data.model.MessageReplyResponse
import erl.bilico.data.model.PgcRankResponse
import erl.bilico.data.model.PgcSeasonResponse
import erl.bilico.data.model.RankingResponse
import erl.bilico.data.model.SystemNoticeResponse
import erl.bilico.data.model.UserRelationResponse
import erl.bilico.data.model.VideoRelationResponse
import erl.bilico.data.model.FavFolderResponse
import erl.bilico.data.model.FavResourceResponse
import erl.bilico.data.model.FollowingsResponse
import erl.bilico.data.model.HistoryResponse
import erl.bilico.data.model.LiveDanmakuInfoResponse
import erl.bilico.data.model.LiveListResponse
import erl.bilico.data.model.LivePlayUrlResponse
import erl.bilico.data.model.LiveRoomDetailResponse
import erl.bilico.data.model.LiveRoomInfoResponse
import erl.bilico.data.model.NavResponse
import erl.bilico.data.model.PlayUrlResponse
import erl.bilico.data.model.PlayerInfoResponse
import erl.bilico.data.model.QrCodeResponse
import erl.bilico.data.model.QrPollResponse
import erl.bilico.data.model.RelationStatResponse
import erl.bilico.data.model.RecommendResponse
import erl.bilico.data.model.ReplyResponse
import erl.bilico.data.model.SearchSquareResponse
import erl.bilico.data.model.SearchTypeResponse
import erl.bilico.data.model.SpaceInfoResponse
import erl.bilico.data.model.SpaceVideoResponse
import erl.bilico.data.model.UpStatResponse
import erl.bilico.data.model.VideoInfoResponse
import erl.bilico.data.model.WatchLaterResponse
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface BiliApi {
    @GET("x/web-interface/nav")
    suspend fun nav(): NavResponse

    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun recommend(@QueryMap params: Map<String, String>): RecommendResponse

    @GET("x/web-interface/view")
    suspend fun videoInfo(@Query("bvid") bvid: String): VideoInfoResponse

    @GET("x/web-interface/view")
    suspend fun videoInfoByAid(@Query("aid") aid: Long): VideoInfoResponse

    @GET("x/player/wbi/playurl")
    suspend fun playUrl(@QueryMap params: Map<String, String>): PlayUrlResponse

    @GET("x/player/playurl")
    suspend fun playUrlLegacy(@QueryMap params: Map<String, String>): PlayUrlResponse

    @GET("x/player/wbi/v2")
    suspend fun playerInfo(@QueryMap params: Map<String, String>): PlayerInfoResponse

    @GET("x/player/v2")
    suspend fun playerInfoLegacy(@QueryMap params: Map<String, String>): PlayerInfoResponse

    @GET("x/v2/reply/wbi/main")
    suspend fun mainComments(@QueryMap params: Map<String, String>): ReplyResponse

    @GET("x/v2/reply")
    suspend fun legacyComments(@QueryMap params: Map<String, String>): ReplyResponse

    @GET("x/v2/reply/reply")
    suspend fun subComments(@QueryMap params: Map<String, String>): ReplyResponse

    @FormUrlEncoded
    @POST("x/v2/reply/add")
    suspend fun addComment(@FieldMap fields: Map<String, String>): AddReplyResponse

    @FormUrlEncoded
    @POST("x/v2/reply/action")
    suspend fun likeComment(@FieldMap fields: Map<String, String>): BaseResponse

    @FormUrlEncoded
    @POST("x/click-interface/web/heartbeat")
    suspend fun heartbeat(@FieldMap fields: Map<String, String>): BaseResponse

    @FormUrlEncoded
    @POST("x/v2/dm/post")
    suspend fun sendDanmaku(@FieldMap fields: Map<String, String>): BaseResponse

    @POST("x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activateBuvid(@retrofit2.http.Body payload: Map<String, String>): BaseResponse

    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun dynamicFeed(@QueryMap params: Map<String, String>): DynamicFeedResponse

    // ---------- 专栏 / 图文 ----------

    @GET("x/article/view")
    suspend fun articleView(@QueryMap params: Map<String, String>): ArticleViewResponse

    @GET("x/polymer/web-dynamic/v1/detail")
    suspend fun dynamicDetail(@QueryMap params: Map<String, String>): DynamicDetailResponse

    @GET("x/polymer/web-dynamic/v1/opus/detail")
    suspend fun opusDetail(@QueryMap params: Map<String, String>): DynamicDetailResponse

    @GET("x/web-interface/history/cursor")
    suspend fun history(@QueryMap params: Map<String, String>): HistoryResponse

    @GET("https://api.live.bilibili.com/room/v3/area/getRoomList")
    suspend fun liveRooms(@QueryMap params: Map<String, String>): LiveListResponse

    @GET("https://api.live.bilibili.com/room/v1/Room/get_info")
    suspend fun liveRoomInfo(@Query("room_id") roomId: Long): LiveRoomInfoResponse

    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom")
    suspend fun liveRoomDetail(@Query("room_id") roomId: Long): LiveRoomDetailResponse

    @GET("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun livePlayUrl(@QueryMap params: Map<String, String>): LivePlayUrlResponse

    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo")
    suspend fun liveDanmakuInfo(@QueryMap params: Map<String, String>): LiveDanmakuInfoResponse

    @GET("x/web-interface/wbi/search/square")
    suspend fun searchSquare(@QueryMap params: Map<String, String>): SearchSquareResponse

    @GET("x/web-interface/search/square")
    suspend fun searchSquareLegacy(@Query("limit") limit: Int = 10): SearchSquareResponse

    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/",
    )
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchType(@QueryMap params: Map<String, String>): SearchTypeResponse

    @GET("x/space/wbi/acc/info")
    suspend fun spaceInfo(@QueryMap params: Map<String, String>): SpaceInfoResponse

    @GET("x/space/wbi/arc/search")
    suspend fun spaceVideos(@QueryMap params: Map<String, String>): SpaceVideoResponse

    @GET("x/relation/stat")
    suspend fun relationStat(@Query("vmid") mid: Long): RelationStatResponse

    @GET("x/space/upstat")
    suspend fun upStat(@Query("mid") mid: Long): UpStatResponse

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun favFolders(
        @Query("up_mid") mid: Long,
        @Query("web_location") webLocation: String = "333.1387",
    ): FavFolderResponse

    @GET("x/v3/fav/resource/list")
    suspend fun favResources(@QueryMap params: Map<String, String>): FavResourceResponse

    @GET("x/v2/history/toview/web")
    suspend fun watchLater(@QueryMap params: Map<String, String>): WatchLaterResponse

    @GET("x/relation/followings")
    suspend fun followings(@QueryMap params: Map<String, String>): FollowingsResponse

    // ---------- 弹幕 ----------

    @GET("https://api.bilibili.com/x/v2/dm/web/seg.so")
    suspend fun danmakuSegment(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("segment_index") segmentIndex: Int,
    ): okhttp3.ResponseBody

    // ---------- 番剧 ----------

    @GET("pgc/view/web/season")
    suspend fun pgcSeason(@QueryMap params: Map<String, String>): PgcSeasonResponse

    @GET("pgc/player/web/v2/playurl")
    suspend fun pgcPlayUrl(@QueryMap params: Map<String, String>): okhttp3.ResponseBody

    @GET("pgc/player/web/playurl")
    suspend fun pgcPlayUrlLegacy(@QueryMap params: Map<String, String>): okhttp3.ResponseBody

    @GET("pgc/web/rank/list")
    suspend fun pgcRank(@QueryMap params: Map<String, String>): PgcRankResponse

    @GET("pgc/season/rank/web/list")
    suspend fun pgcSeasonRank(@QueryMap params: Map<String, String>): PgcRankResponse

    @GET("x/space/bangumi/follow/list")
    suspend fun bangumiFollow(@QueryMap params: Map<String, String>): BangumiFollowResponse

    @FormUrlEncoded
    @POST("pgc/web/follow/add")
    suspend fun followBangumi(@FieldMap fields: Map<String, String>): BaseResponse

    @FormUrlEncoded
    @POST("pgc/web/follow/del")
    suspend fun unfollowBangumi(@FieldMap fields: Map<String, String>): BaseResponse

    // ---------- 排行榜 ----------

    @GET("x/web-interface/ranking/v2")
    suspend fun ranking(@QueryMap params: Map<String, String>): RankingResponse

    // ---------- 视频互动 ----------

    @GET("x/web-interface/archive/relation")
    suspend fun videoRelation(@Query("aid") aid: Long): VideoRelationResponse

    @GET("x/relation")
    suspend fun userRelation(@Query("fid") fid: Long): UserRelationResponse

    @FormUrlEncoded
    @POST("x/web-interface/archive/like")
    suspend fun likeVideo(@FieldMap fields: Map<String, String>): BaseResponse

    @FormUrlEncoded
    @POST("x/web-interface/coin/add")
    suspend fun coinVideo(@FieldMap fields: Map<String, String>): BaseResponse

    @FormUrlEncoded
    @POST("x/v3/fav/resource/deal")
    suspend fun favoriteResource(@FieldMap fields: Map<String, String>): BaseResponse

    @FormUrlEncoded
    @POST("x/v2/history/toview/add")
    suspend fun addWatchLater(@FieldMap fields: Map<String, String>): BaseResponse

    @FormUrlEncoded
    @POST("x/relation/modify")
    suspend fun modifyRelation(@FieldMap fields: Map<String, String>): BaseResponse

    // ---------- 消息 ----------

    @GET("https://api.bilibili.com/x/msgfeed/reply")
    suspend fun messageReply(@QueryMap params: Map<String, String>): MessageReplyResponse

    @GET("https://api.bilibili.com/x/msgfeed/at")
    suspend fun messageAt(@QueryMap params: Map<String, String>): MessageAtResponse

    @GET("https://api.bilibili.com/x/msgfeed/like")
    suspend fun messageLike(@QueryMap params: Map<String, String>): MessageLikeResponse

    @GET("https://message.bilibili.com/x/sys-msg/query_notify_list")
    suspend fun systemNotices(@QueryMap params: Map<String, String>): SystemNoticeResponse
}

interface PassportApi {
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(): QrCodeResponse

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQrCode(@Query("qrcode_key") key: String): QrPollResponse

    @FormUrlEncoded
    @POST("x/passport-tv-login/h5/refresh")
    suspend fun refreshToken(@FieldMap params: Map<String, String>): TvRefreshResponse
}

@kotlinx.serialization.Serializable
data class TvRefreshResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TvRefreshData? = null,
)

@kotlinx.serialization.Serializable
data class TvRefreshData(
    val mid: Long = 0,
    @kotlinx.serialization.SerialName("access_token") val accessToken: String = "",
    @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String = "",
    @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long = 0,
)
