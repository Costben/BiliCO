package erl.bilico.feature.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.platform.DecodedAvatarBitmap
import erl.bilico.platform.PickedPhotoData
import erl.bilico.platform.cropAvatarBitmapToJpeg
import erl.bilico.platform.decodeAvatarCropBitmap
import erl.bilico.platform.platformSupportsAvatarCrop
import erl.bilico.ui.components.ConfirmDialog
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.max

private fun clampCropOffset(
    offsetX: Float,
    offsetY: Float,
    bmpWidth: Int,
    bmpHeight: Int,
    cropWidthPx: Float,
    cropHeightPx: Float,
    scale: Float,
): Pair<Float, Float> {
    if (cropWidthPx <= 0f || cropHeightPx <= 0f || bmpWidth <= 0 || bmpHeight <= 0) return 0f to 0f
    val baseScale = max(cropWidthPx / bmpWidth.toFloat(), cropHeightPx / bmpHeight.toFloat())
    val maxDx = ((bmpWidth * baseScale * scale - cropWidthPx) / 2f).coerceAtLeast(0f)
    val maxDy = ((bmpHeight * baseScale * scale - cropHeightPx) / 2f).coerceAtLeast(0f)
    return offsetX.coerceIn(-maxDx, maxDx) to offsetY.coerceIn(-maxDy, maxDy)
}

/**
 * 头像裁剪弹窗：
 * - Android 端保留完整 1:1 拖拽/双指缩放裁剪框与 720px 正方形 JPEG 压缩输出及二次确认；
 * - iOS 端保留圆形预览确认流程直接上传选中照片。
 */
@Composable
internal fun AvatarCropDialog(
    sourcePhoto: PickedPhotoData,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    if (!platformSupportsAvatarCrop) {
        AvatarPreviewOnlyDialog(
            sourcePhoto = sourcePhoto,
            uploading = uploading,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
        return
    }

    var show by remember(sourcePhoto.id) { mutableStateOf(false) }
    var settled by remember(sourcePhoto.id) { mutableStateOf(false) }
    var decoded by remember(sourcePhoto.id) { mutableStateOf<DecodedAvatarBitmap?>(null) }
    var decodeError by remember(sourcePhoto.id) { mutableStateOf<String?>(null) }
    var scale by remember(sourcePhoto.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(sourcePhoto.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(sourcePhoto.id) { mutableFloatStateOf(0f) }
    var boxSize by remember(sourcePhoto.id) { mutableStateOf(IntSize.Zero) }
    var pendingUploadBytes by remember(sourcePhoto.id) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(sourcePhoto.id) {
        show = true
        decodeError = null
        runCatching { decodeAvatarCropBitmap(sourcePhoto.bytes) }
            .onSuccess { bmp ->
                if (bmp != null) decoded = bmp else decodeError = "图片加载失败"
            }
            .onFailure { decodeError = it.message ?: "图片加载失败" }
    }

    DisposableEffect(decoded) {
        val current = decoded
        onDispose { current?.recycle() }
    }

    OverlayDialog(
        show = show,
        title = "裁剪头像",
        summary = "拖动或双指缩放调整头像位置",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { if (!uploading) show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141414))
                    .clipToBounds()
                    .onSizeChanged { boxSize = it }
                    .pointerInput(decoded, uploading) {
                        val bmp = decoded ?: return@pointerInput
                        if (uploading) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 4f)
                            val (clampedX, clampedY) = clampCropOffset(
                                offsetX = offsetX + pan.x,
                                offsetY = offsetY + pan.y,
                                bmpWidth = bmp.width,
                                bmpHeight = bmp.height,
                                cropWidthPx = boxSize.width.toFloat(),
                                cropHeightPx = boxSize.height.toFloat(),
                                scale = nextScale,
                            )
                            scale = nextScale
                            offsetX = clampedX
                            offsetY = clampedY
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = decoded
                val imageBitmap = remember(bmp) { bmp?.toImageBitmap() }
                when {
                    imageBitmap != null -> {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "裁剪头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                },
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                        ) {
                            drawRect(Color.Black.copy(alpha = 0.48f))
                            val radius = size.minDimension / 2f
                            drawCircle(
                                color = Color.Transparent,
                                radius = radius,
                                blendMode = BlendMode.Clear,
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.85f),
                                radius = radius - 1.dp.toPx(),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }

                    decodeError != null -> {
                        Text(
                            text = decodeError.orEmpty(),
                            color = COUITheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }

                    else -> {
                        CircularProgressIndicator(size = 24.dp, strokeWidth = 2.4.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "取消",
                    onClick = { if (!uploading) show = false },
                ),
                positive = DialogButtonBarAction(
                    text = if (uploading) "上传中…" else "上传头像",
                    recommended = true,
                    onClick = {
                        val bmp = decoded ?: return@DialogButtonBarAction
                        if (uploading) return@DialogButtonBarAction
                        val boxW = boxSize.width.toFloat().coerceAtLeast(1f)
                        val boxH = boxSize.height.toFloat().coerceAtLeast(1f)
                        pendingUploadBytes = cropAvatarBitmapToJpeg(
                            source = bmp,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            cropWidthPx = boxW,
                            cropHeightPx = boxH,
                        )
                    },
                ),
            )
        }
    }

    pendingUploadBytes?.let { bytes ->
        ConfirmDialog(
            title = "确认更换头像",
            message = "确定将裁剪后的图片设为新的个人头像吗？",
            confirmText = "确认上传",
            onDismiss = { pendingUploadBytes = null },
            onConfirm = {
                pendingUploadBytes = null
                onConfirm(bytes)
            },
        )
    }
}

@Composable
private fun AvatarPreviewOnlyDialog(
    sourcePhoto: PickedPhotoData,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "预览头像",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { if (!uploading) show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 12.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(COUITheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = sourcePhoto.bytes,
                    contentDescription = "头像预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "取消",
                    onClick = { if (!uploading) show = false },
                ),
                positive = DialogButtonBarAction(
                    text = if (uploading) "上传中…" else "使用此头像",
                    recommended = true,
                    onClick = { if (!uploading) onConfirm(sourcePhoto.bytes) },
                ),
            )
        }
    }
}
