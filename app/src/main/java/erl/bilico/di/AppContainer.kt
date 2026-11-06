package erl.bilico.di

import android.content.Context
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.TokenRefreshHelper
import erl.bilico.data.repository.ArticleRepository
import erl.bilico.data.repository.CommentRepository
import erl.bilico.data.repository.DanmakuRepository
import erl.bilico.data.repository.DynamicRepository
import erl.bilico.data.repository.FavoriteRepository
import erl.bilico.data.repository.FeedRepository
import erl.bilico.data.repository.HeartbeatReporter
import erl.bilico.data.repository.HistoryRepository
import erl.bilico.data.repository.InteractionRepository
import erl.bilico.data.repository.LiveRepository
import erl.bilico.data.repository.MessageRepository
import erl.bilico.data.repository.PgcRepository
import erl.bilico.data.repository.RankingRepository
import erl.bilico.data.repository.RelationRepository
import erl.bilico.data.repository.SearchRepository
import erl.bilico.data.repository.SpaceRepository
import erl.bilico.data.repository.SubtitleRepository
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.repository.WatchLaterRepository
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手写依赖容器：一个进程一套网络栈，仓库共享 Cookie 与 WBI 密钥。
 * 没有反射、没有注解处理器，构造顺序一眼可见。
 */
class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val client = BiliClient(appContext, scope)

    val session = SessionRepository(
        context = appContext,
        cookies = client.cookieStore,
        api = client.api,
        passportApi = client.passportApi,
        buvid = client.buvid,
        scope = scope,
    )

    val tokenRefresh = TokenRefreshHelper(client.passportApi, session)

    val feed = FeedRepository(client.api, client.wbi, client.buvid)

    val video = VideoRepository(client.api, client.wbi, client.buvid, session)

    val comments = CommentRepository(client.api, client.wbi, client.buvid, session)

    val heartbeat = HeartbeatReporter(client.api, session)

    val dynamic = DynamicRepository(client.api, client.buvid)

    val articles = ArticleRepository(client.api, client.wbi, client.buvid)

    val history = HistoryRepository(client.api, client.buvid)

    val live = LiveRepository(client.api, client.wbi, client.buvid)

    val settings = SettingsRepository(appContext, scope)

    val search = SearchRepository(client.api, client.wbi, client.buvid)

    val space = SpaceRepository(client.api, client.wbi, client.buvid)

    val favorites = FavoriteRepository(client.api, client.buvid)

    val watchLater = WatchLaterRepository(client.api, client.buvid)

    val relations = RelationRepository(client.api, client.buvid)

    val danmaku = DanmakuRepository(client.api, client.buvid)

    val pgc = PgcRepository(client.api, client.wbi, client.buvid, session)

    val ranking = RankingRepository(client.api, client.wbi, client.buvid)

    val interactions = InteractionRepository(client.api, client.buvid, session)

    val messages = MessageRepository(client.api, client.buvid)

    val subtitles = SubtitleRepository(client.api, client.wbi, client.buvid, client.okHttp)
}
