package erl.bilico.di

import android.content.Context
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.TokenRefreshHelper
import erl.bilico.data.repository.CommentRepository
import erl.bilico.data.repository.DynamicRepository
import erl.bilico.data.repository.FeedRepository
import erl.bilico.data.repository.HeartbeatReporter
import erl.bilico.data.repository.HistoryRepository
import erl.bilico.data.repository.LiveRepository
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.session.SessionRepository
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

    val comments = CommentRepository(client.api, client.wbi, client.buvid)

    val heartbeat = HeartbeatReporter(client.api, session)

    val dynamic = DynamicRepository(client.api, client.buvid)

    val history = HistoryRepository(client.api, client.buvid)

    val live = LiveRepository(client.api, client.wbi, client.buvid)
}
