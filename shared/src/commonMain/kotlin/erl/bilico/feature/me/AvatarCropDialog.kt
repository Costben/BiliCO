package erl.bilico.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import erl.bilico.platform.PickedPhotoData
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 头像预览与确认弹窗（iOS KMP 版直接上传选中的照片字节）。 */
@Composable
internal fun AvatarCropDialog(
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
