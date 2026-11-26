package erl.bilico.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max

private const val AVATAR_OUTPUT_SIZE = 720
private const val AVATAR_MAX_BYTES = 1_800_000

internal actual val platformSupportsAvatarCrop: Boolean = true

internal actual class DecodedAvatarBitmap(
    internal val bitmap: Bitmap,
) {
    actual val width: Int get() = bitmap.width
    actual val height: Int get() = bitmap.height
    actual fun toImageBitmap(): ImageBitmap = bitmap.asImageBitmap()
    actual fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

internal actual suspend fun decodeAvatarCropBitmap(
    bytes: ByteArray,
    maxDim: Int,
): DecodedAvatarBitmap? = withContext(ioDispatcher) {
    if (bytes.isEmpty()) return@withContext null
    val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxDim && longest > 0) {
                val ratio = maxDim.toFloat() / longest
                decoder.setTargetSize(
                    (w * ratio).toInt().coerceAtLeast(1),
                    (h * ratio).toInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalStateException("无法解码图片")
    }
    val software = if (raw.config == Bitmap.Config.HARDWARE) {
        raw.copy(Bitmap.Config.ARGB_8888, false)
            ?: throw IllegalStateException("无法转换图片格式")
    } else {
        raw
    }
    DecodedAvatarBitmap(software)
}

internal actual fun cropAvatarBitmapToJpeg(
    source: DecodedAvatarBitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    cropWidthPx: Float,
    cropHeightPx: Float,
): ByteArray {
    val bmp = source.bitmap
    val safeW = cropWidthPx.coerceAtLeast(1f)
    val safeH = cropHeightPx.coerceAtLeast(1f)
    val bmpW = bmp.width.toFloat().coerceAtLeast(1f)
    val bmpH = bmp.height.toFloat().coerceAtLeast(1f)
    val baseScale = max(safeW / bmpW, safeH / bmpH)
    val totalScale = baseScale * scale
    val outRatio = max(AVATAR_OUTPUT_SIZE / safeW, AVATAR_OUTPUT_SIZE / safeH)

    val out = Bitmap.createBitmap(AVATAR_OUTPUT_SIZE, AVATAR_OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val matrix = Matrix().apply {
        postTranslate(-bmpW / 2f, -bmpH / 2f)
        postScale(totalScale * outRatio, totalScale * outRatio)
        postTranslate(
            AVATAR_OUTPUT_SIZE / 2f + offsetX * outRatio,
            AVATAR_OUTPUT_SIZE / 2f + offsetY * outRatio,
        )
    }
    canvas.drawColor(AndroidColor.BLACK)
    canvas.drawBitmap(bmp, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

    val stream = ByteArrayOutputStream()
    var quality = 92
    out.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    while (stream.size() > AVATAR_MAX_BYTES && quality > 60) {
        stream.reset()
        quality -= 10
        out.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    }
    out.recycle()
    return stream.toByteArray()
}
