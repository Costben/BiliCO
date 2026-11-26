package erl.bilico.platform

import androidx.compose.ui.graphics.ImageBitmap

internal expect val platformSupportsAvatarCrop: Boolean

internal expect class DecodedAvatarBitmap {
    val width: Int
    val height: Int
    fun toImageBitmap(): ImageBitmap
    fun recycle()
}

internal expect suspend fun decodeAvatarCropBitmap(
    bytes: ByteArray,
    maxDim: Int = 2048,
): DecodedAvatarBitmap?

internal expect fun cropAvatarBitmapToJpeg(
    source: DecodedAvatarBitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    cropWidthPx: Float,
    cropHeightPx: Float,
): ByteArray
