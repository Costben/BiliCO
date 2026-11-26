package erl.bilico.feature.dynamic

import erl.bilico.platform.PlatformContext
import erl.bilico.platform.shareText
import erl.bilico.platform.showToast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.di.AppContainer
import kotlinx.coroutines.launch

/** 动态卡片上的稍后再看：视频动态才有 aid，没有就退化成提示。 */
@Composable
internal fun rememberDynamicWatchLater(container: AppContainer): (DynamicVideoCard) -> Unit {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    return remember(container) {
        { video ->
            when {
                video.aid <= 0L ->
                    showToast(context, "该视频暂不支持稍后再看")

                !container.session.isLoggedIn() ->
                    showToast(context, "登录后可用")

                else -> scope.launch {
                    container.interactions.addToWatchLater(video.aid)
                        .onSuccess {
                            showToast(context, "已加入稍后再看")
                        }
                        .onFailure { error ->
                            showToast(context, error.message ?: "加入稍后再看失败")
                        }
                }
            }
        }
    }
}

/** 分享动态：视频动态给视频链接，其余给 opus 链接。 */
internal fun shareDynamic(context: PlatformContext, item: DynamicItemUi) {
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
    shareText(context, if (summary.isBlank()) link else "$summary\n$link", "分享动态")
}
