package erl.bilico.feature.dynamic

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.di.AppContainer
import kotlinx.coroutines.launch

/** 动态卡片上的稍后再看：视频动态才有 aid，没有就退化成提示。 */
@Composable
internal fun rememberDynamicWatchLater(container: AppContainer): (DynamicVideoCard) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(container) {
        { video ->
            when {
                video.aid <= 0L ->
                    Toast.makeText(context, "该视频暂不支持稍后再看", Toast.LENGTH_SHORT).show()

                !container.session.isLoggedIn() ->
                    Toast.makeText(context, "登录后可用", Toast.LENGTH_SHORT).show()

                else -> scope.launch {
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

/** 分享动态：视频动态给视频链接，其余给 opus 链接。 */
internal fun shareDynamic(context: Context, item: DynamicItemUi) {
    val bvid = item.video?.bvid.orEmpty()
    val link = if (bvid.isNotBlank()) {
        "https://www.bilibili.com/video/$bvid"
    } else {
        "https://www.bilibili.com/opus/${item.dynamicId}"
    }
    val summary = item.content.lineSequence()
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .take(60)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, if (summary.isBlank()) link else "$summary\n$link")
    }
    context.startActivity(Intent.createChooser(intent, "分享动态"))
}
