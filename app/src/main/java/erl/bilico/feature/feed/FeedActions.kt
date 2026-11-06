package erl.bilico.feature.feed

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import erl.bilico.data.model.FeedVideo
import erl.bilico.di.AppContainer
import kotlinx.coroutines.launch

/**
 * 卡片三个点里的「稍后再看」。
 *
 * 未登录先拦下，和播放页一样不把空 csrf 发给接口；成功失败都给一句提示。
 */
@Composable
internal fun rememberWatchLaterAction(
    container: AppContainer,
    video: FeedVideo,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(video.aid, container) {
        {
            if (!container.session.isLoggedIn()) {
                Toast.makeText(context, "登录后可用", Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    container.interactions.addToWatchLater(video.aid)
                        .onSuccess {
                            Toast.makeText(context, "已加入稍后再看", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: "加入稍后再看失败",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                }
            }
        }
    }
}
