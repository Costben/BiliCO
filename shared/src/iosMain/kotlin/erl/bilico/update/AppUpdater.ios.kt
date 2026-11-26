package erl.bilico.update

import erl.bilico.platform.PlatformContext
import erl.bilico.platform.openExternalUrl

actual fun updatePlatformUserAgent(): String = "BiliCO-iOS"

actual fun preferredReleaseAssetExtension(): String = ".ipa"

actual fun supportsInAppUpdate(): Boolean = false

actual fun openPlatformUpdateDownload(context: PlatformContext, info: UpdateInfo) {
    openExternalUrl(context, info.pageUrl)
}

actual suspend fun downloadPlatformUpdatePackage(
    context: PlatformContext,
    url: String,
    version: String,
    force: Boolean,
    onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
): DownloadedUpdatePackage = throw UnsupportedOperationException("iOS 不支持应用内下载安装包")

actual fun installPlatformUpdatePackage(
    context: PlatformContext,
    pkg: DownloadedUpdatePackage,
): Result<Unit> = Result.failure(UnsupportedOperationException("iOS 不支持应用内安装"))
