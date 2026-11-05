package erl.bilico.data.repository

import erl.bilico.data.network.BiliApi
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放心跳：让 B 站历史记录能记上进度。
 * web 心跳必须带 csrf，未登录时直接跳过。
 */
class HeartbeatReporter(
    private val api: BiliApi,
    private val session: SessionRepository,
) {
    suspend fun report(
        bvid: String,
        aid: Long,
        cid: Long,
        playedTimeSec: Long,
        realPlayedTimeSec: Long,
        startTsSec: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val csrf = session.csrf()
        if (csrf.isBlank()) return@withContext false

        val played = playedTimeSec.coerceAtLeast(0L)
        val real = realPlayedTimeSec.coerceAtLeast(0L)
        val fields = buildMap {
            if (aid > 0) put("aid", aid.toString())
            if (bvid.isNotBlank()) put("bvid", bvid)
            if (cid > 0) put("cid", cid.toString())
            session.mid().takeIf { it > 0 }?.let { put("mid", it.toString()) }
            put("played_time", played.toString())
            put("real_played_time", real.toString())
            put("realtime", real.toString())
            put("start_ts", startTsSec.coerceAtLeast(0L).toString())
            put("type", "3")
            put("dt", "2")
            put("outer", "0")
            put("play_type", if (played <= 0L && real <= 0L) "1" else "0")
            put("spmid", "333.788.0.0")
            put("from_spmid", "")
            put("refer_url", "")
            put("csrf", csrf)
        }
        runCatching { api.heartbeat(fields).code == 0 }.getOrDefault(false)
    }
}
