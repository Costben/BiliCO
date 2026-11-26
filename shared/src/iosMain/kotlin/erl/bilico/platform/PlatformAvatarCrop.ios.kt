package erl.bilico.platform

import androidx.compose.ui.graphics.ImageBitmap

internal actual val platformSupportsAvatarCrop: Boolean = false

internal actual class DecodedAvatarBitmap {
    actual val width: Int get() = 0
    actual val height: Int get() = 0
    actual fun toImageBitmap(): ImageBitmap = error("iOS 不使用本地位图裁剪")
    actual fun recycle() = Unit
}

internal actual suspend fun decodeAvatarCropBitmap(
    bytes: ByteArray,
    maxDim: Int,
): DecodedAvatarBitmap? = null

internal actual fun cropAvatarBitmapToJpeg(
    source: DecodedAvatarBitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    cropWidthPx: Float,
    cropHeightPx: Float,
): ByteArray = ByteArray(0)
