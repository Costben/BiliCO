package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

/** 加载比这快就不弹转圈，缓存命中时不会闪一下。 */
private const val SPINNER_DELAY_MS = 160L

/**
 * 带加载指示的图片：没出图之前先铺占位底色，超过 [SPINNER_DELAY_MS] 还没出来再叠一个转圈，
 * 免得大图挂在纯黑背景上看着像坏掉。
 */
@Composable
fun LoadingImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    backgroundColor: Color = COUITheme.colorScheme.surfaceContainerHigh,
    /** 转圈颜色；纯黑看图页要传白色，浅色主题默认色会糊在黑底上。 */
    indicatorColor: Color? = null,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    var showSpinner by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (loaded) return@LaunchedEffect
        delay(SPINNER_DELAY_MS)
        if (!loaded) showSpinner = true
    }

    Box(modifier.background(backgroundColor), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onState = { state ->
                    loaded = state is AsyncImagePainter.State.Success ||
                        state is AsyncImagePainter.State.Error
                },
            )
        }
        if (!loaded && showSpinner) {
            CircularProgressIndicator(
                size = 22.dp,
                colors = if (indicatorColor != null) {
                    ProgressIndicatorDefaults.circularProgressIndicatorColors(
                        foregroundColor = indicatorColor,
                    )
                } else {
                    ProgressIndicatorDefaults.circularProgressIndicatorColors()
                },
            )
        }
    }
}
