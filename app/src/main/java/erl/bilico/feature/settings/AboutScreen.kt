package erl.bilico.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.BuildConfig
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.PreferenceDivider
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.AppInfoPreference
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

private data class UpstreamRef(
    val title: String,
    val summary: String,
    val status: String,
    val url: String,
)

private val UI_UPSTREAMS = listOf(
    UpstreamRef(
        title = "COUI 17",
        summary = "自维护 ColorOS 17 Compose 组件库 · Costben/coui17",
        status = "Apache-2.0",
        url = "https://github.com/Costben/coui17",
    ),
    UpstreamRef(
        title = "COUI Kit",
        summary = "io.github.itoseo:coui · Jetpack Compose 组件与视觉效果库",
        status = "ItosEO",
        url = "https://github.com/ItosEO/COUI-Kit",
    ),
    UpstreamRef(
        title = "COUI",
        summary = "suqi8/coui · ColorOS 16 跨平台 Compose UI 组件库上游",
        status = "Apache-2.0",
        url = "https://github.com/suqi8/coui",
    ),
    UpstreamRef(
        title = "Miuix",
        summary = "compose-miuix-ui/miuix · Compose Multiplatform 基础组件上游",
        status = "Apache-2.0",
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
)

private val FEATURE_REFERENCES = listOf(
    UpstreamRef(
        title = "PiliPlus",
        summary = "bggRGjQaUbCoE/PiliPlus · 播放器、弹幕、功能逻辑与图标字体参考",
        status = "GitHub",
        url = "https://github.com/bggRGjQaUbCoE/PiliPlus",
    ),
    UpstreamRef(
        title = "BiliPai",
        summary = "jay3-yy/BiliPai · 原生 Compose 客户端功能实现与接口协议参考",
        status = "GitHub",
        url = "https://github.com/jay3-yy/BiliPai",
    ),
)

private val CORE_LIBRARIES = listOf(
    UpstreamRef(
        title = "AndroidX Media3",
        summary = "ExoPlayer DASH / HLS 音视频流与画中画播放引擎",
        status = "Apache-2.0",
        url = "https://github.com/androidx/media",
    ),
    UpstreamRef(
        title = "Coil 3",
        summary = "Kotlin Multiplatform 封面、头像与动图加载",
        status = "Apache-2.0",
        url = "https://github.com/coil-kt/coil",
    ),
    UpstreamRef(
        title = "OkHttp & Retrofit",
        summary = "HTTP/2 网络连接池、接口请求与序列化框架",
        status = "Apache-2.0",
        url = "https://github.com/square/okhttp",
    ),
    UpstreamRef(
        title = "ZXing & Brotli",
        summary = "扫码登录二维码生成与 Brotli 弹幕二进制解压",
        status = "Apache-2.0",
        url = "https://github.com/zxing/zxing",
    ),
)

@Composable
fun AboutScreen(
    @Suppress("UNUSED_PARAMETER") container: AppContainer,
    nav: NavState,
) {
    val context = LocalContext.current
    val appIcon = rememberAppIconPainter(context)
    val versionText = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    SecondaryPage(
        title = "关于",
        onBack = nav::pop,
    ) {
        item(key = "app-header") {
            AppInfoPreference(
                appName = "BiliCO",
                appVersion = versionText,
                modifier = Modifier.testTag("about_header"),
                icon = appIcon,
                onVersionLongClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("BiliCO", "BiliCO $versionText"))
                    Toast.makeText(context, "已复制版本信息", Toast.LENGTH_SHORT).show()
                },
            )
        }

        item(key = "project-title") { SectionTitle("项目", topPadding = 8.dp) }

        item(key = "project") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                ArrowPreference(
                    title = "开源仓库",
                    summary = "https://github.com/Costben/BiliCO",
                    statusText = "GitHub",
                    modifier = Modifier.testTag("about_repo"),
                    cardListPosition = CardListPosition.Head,
                    onClick = { openExternalUrl(context, "https://github.com/Costben/BiliCO") },
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "检查更新与更新日志",
                    summary = "查看历史版本与发布说明",
                    modifier = Modifier.testTag("about_updates"),
                    cardListPosition = CardListPosition.Tail,
                    onClick = { nav.push(Route.Update) },
                )
            }
        }

        item(key = "ui-upstreams-title") { SectionTitle("前端界面库") }

        item(key = "ui-upstreams") {
            UpstreamGroupCard(
                items = UI_UPSTREAMS,
                tagPrefix = "about_ui",
                onOpen = { url -> openExternalUrl(context, url) },
            )
        }

        item(key = "feature-refs-title") { SectionTitle("功能实现参考") }

        item(key = "feature-refs") {
            UpstreamGroupCard(
                items = FEATURE_REFERENCES,
                tagPrefix = "about_ref",
                onOpen = { url -> openExternalUrl(context, url) },
            )
        }

        item(key = "core-libs-title") { SectionTitle("核心开源依赖") }

        item(key = "core-libs") {
            UpstreamGroupCard(
                items = CORE_LIBRARIES,
                tagPrefix = "about_lib",
                onOpen = { url -> openExternalUrl(context, url) },
            )
        }

        item(key = "disclaimer") {
            Text(
                text = "第三方哔哩哔哩客户端，仅用于个人学习与研究。所有内容、商标与版权归原平台及对应开源项目作者所有。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun UpstreamGroupCard(
    items: List<UpstreamRef>,
    tagPrefix: String,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        items.forEachIndexed { index, item ->
            val position = when {
                items.size == 1 -> CardListPosition.None
                index == 0 -> CardListPosition.Head
                index == items.lastIndex -> CardListPosition.Tail
                else -> CardListPosition.Middle
            }
            if (index > 0) PreferenceDivider()
            ArrowPreference(
                title = item.title,
                summary = item.summary,
                statusText = item.status,
                modifier = Modifier.testTag("${tagPrefix}_$index"),
                cardListPosition = position,
                onClick = { onOpen(item.url) },
            )
        }
    }
}

@Composable
private fun rememberAppIconPainter(context: Context): Painter? = remember(context) {
    runCatching {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val sizePx = 192
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        BitmapPainter(bitmap.asImageBitmap())
    }.getOrNull()
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}
