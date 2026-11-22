package erl.bilico.feature.me

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.Account
import erl.bilico.data.model.EditableAccountProfile
import erl.bilico.data.repository.MemberProfileRepository
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.ConfirmDialog
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Undo
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

private val GENDER_OPTIONS = listOf("保密", "男", "女")
private const val MAX_NICKNAME_LENGTH = 30
private const val MAX_SIGN_LENGTH = 70

/** 挂在主页容器里的编辑资料宿主：响应顶栏菜单开关，未登录时不打开弹窗。 */
@Composable
internal fun ProfileEditHost(
    open: Boolean,
    container: AppContainer,
    nav: NavState,
    onDismiss: () -> Unit,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val current = account
    if (!open || current == null) return
    ProfileEditDialog(
        account = current,
        profileRepo = container.memberProfile,
        onOpenWeb = {
            onDismiss()
            nav.push(Route.WebProfileEdit)
        },
        onDismiss = onDismiss,
    )
}

/** 「编辑资料」弹窗：支持更换头像（含裁剪）、修改昵称（ID）、个人简介、性别及网页端兜底。 */
@Composable
internal fun ProfileEditDialog(
    account: Account,
    profileRepo: MemberProfileRepository,
    onOpenWeb: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = COUITheme.colorScheme

    var show by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var loadedProfile by remember(account.mid) { mutableStateOf<EditableAccountProfile?>(null) }
    var loadingProfile by remember(account.mid) { mutableStateOf(true) }
    var loadError by remember(account.mid) { mutableStateOf<String?>(null) }

    var initialName by remember(account.mid) { mutableStateOf(account.name) }
    var initialSign by remember(account.mid) { mutableStateOf(account.sign) }
    var initialSex by remember(account.mid) { mutableStateOf("保密") }

    var nameDraft by remember(account.mid) { mutableStateOf(account.name) }
    var signDraft by remember(account.mid) { mutableStateOf(account.sign) }
    var sexDraft by remember(account.mid) { mutableStateOf("保密") }
    var avatarUrl by remember(account.mid) { mutableStateOf(account.face) }

    var saving by remember { mutableStateOf(false) }
    var uploadingAvatar by remember { mutableStateOf(false) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSave by remember { mutableStateOf<PendingProfileSave?>(null) }
    var webFallbackMessage by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) cropUri = uri
    }

    LaunchedEffect(account.mid) {
        show = true
        loadingProfile = true
        loadError = null
        profileRepo.fetchEditableProfile()
            .onSuccess { profile ->
                loadedProfile = profile
                if (profile.uname.isNotBlank()) {
                    if (nameDraft == initialName) nameDraft = profile.uname
                    initialName = profile.uname
                }
                if (signDraft == initialSign) signDraft = profile.sign
                initialSign = profile.sign
                val normalizedSex = profile.sex.takeIf { it in GENDER_OPTIONS } ?: "保密"
                if (sexDraft == initialSex) sexDraft = normalizedSex
                initialSex = normalizedSex
                if (profile.face.isNotBlank()) avatarUrl = profile.face
            }
            .onFailure { err ->
                loadError = err.message ?: "读取账号资料失败"
            }
        loadingProfile = false
    }

    OverlayDialog(
        show = show,
        title = "编辑资料",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { if (!saving && !uploadingAvatar) show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceContainer)
                        .clickable(enabled = !uploadingAvatar && !saving) {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("edit_profile_avatar"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sizedAvatar = remember(avatarUrl) {
                        Formatters.sizedImageUrl(avatarUrl, 132, 132)
                    }
                    Avatar(sizedAvatar, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("头像", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (uploadingAvatar) "正在处理上传…" else "点击从相册选择并裁剪",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceSecondary,
                        )
                    }
                    Text("更换", fontSize = 13.sp, color = scheme.primary)
                }

                TextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(MAX_NICKNAME_LENGTH) },
                    label = "昵称（ID）",
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = { nameDraft = initialName },
                            enabled = !saving && nameDraft != initialName,
                            modifier = Modifier.testTag("edit_profile_name_restore"),
                        ) {
                            Icon(
                                imageVector = COUIIcons.Undo,
                                contentDescription = "恢复昵称",
                                modifier = Modifier.size(18.dp),
                                tint = if (!saving && nameDraft != initialName) {
                                    scheme.primary
                                } else {
                                    scheme.onSurfaceSecondary.copy(alpha = 0.4f)
                                },
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_name"),
                )

                val idHintText = when {
                    loadingProfile -> "UID ${account.mid} · 正在同步账号资料…"
                    loadError != null -> "UID ${account.mid} · $loadError"
                    loadedProfile?.nickFree == true -> "UID ${account.mid} · 当前可免费修改一次昵称"
                    else -> "UID ${account.mid} · 修改昵称需受重名校验，部分账号需消耗 6 硬币"
                }
                Text(
                    text = idHintText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (loadError != null) scheme.error else scheme.onSurfaceSecondary,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .testTag("edit_profile_name_hint"),
                )

                TextField(
                    value = signDraft,
                    onValueChange = { signDraft = it.take(MAX_SIGN_LENGTH) },
                    label = "个人简介",
                    singleLine = false,
                    maxLines = 4,
                    trailingIcon = if (signDraft != initialSign) {
                        {
                            IconButton(
                                onClick = { signDraft = initialSign },
                                enabled = !saving,
                                modifier = Modifier.testTag("edit_profile_sign_restore"),
                            ) {
                                Icon(
                                    imageVector = COUIIcons.Undo,
                                    contentDescription = "恢复简介",
                                    modifier = Modifier.size(18.dp),
                                    tint = scheme.primary,
                                )
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_sign"),
                )

                GenderSelectorRow(
                    selected = sexDraft,
                    enabled = !saving && !loadingProfile,
                    onSelect = { sexDraft = it },
                )

                Text(
                    text = "需安全验证或修改更多资料时，点此打开网页版 →",
                    fontSize = 12.sp,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            show = false
                            onOpenWeb()
                        }
                        .padding(vertical = 4.dp)
                        .testTag("edit_profile_web_fallback"),
                )
            }

            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "取消",
                    onClick = { if (!saving && !uploadingAvatar) show = false },
                ),
                positive = DialogButtonBarAction(
                    text = if (saving) "保存中…" else "保存",
                    recommended = true,
                    onClick = {
                        if (saving || uploadingAvatar) return@DialogButtonBarAction
                        val nextName = nameDraft.trim()
                        val nextSign = signDraft.trim()
                        val nextSex = sexDraft
                        if (nextName.isEmpty()) {
                            Toast.makeText(context, "昵称不能为空", Toast.LENGTH_SHORT).show()
                            return@DialogButtonBarAction
                        }
                        val unameArg = nextName.takeIf { it != initialName }
                        val signArg = nextSign.takeIf { it != initialSign }
                        val sexArg = nextSex.takeIf { it != initialSex }
                        if (unameArg == null && signArg == null && sexArg == null) {
                            show = false
                            return@DialogButtonBarAction
                        }
                        pendingSave = PendingProfileSave(
                            uname = unameArg,
                            sign = signArg,
                            sex = sexArg,
                        )
                    },
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    pendingSave?.let { save ->
        val summaryLines = buildList {
            if (save.uname != null) add("昵称（ID）改为「${save.uname}」")
            if (save.sign != null) add("个人简介更新为「${save.sign.ifBlank { "（清空）" }}」")
            if (save.sex != null) add("性别改为「${save.sex}」")
        }.joinToString("，")
        ConfirmDialog(
            title = "确认保存资料",
            message = "确定将$summaryLines 并同步到 B 站账号吗？",
            confirmText = "确认保存",
            onDismiss = { pendingSave = null },
            onConfirm = {
                pendingSave = null
                if (!saving) {
                    saving = true
                    scope.launch {
                        profileRepo.updateProfile(
                            uname = save.uname,
                            sign = save.sign,
                            sex = save.sex,
                            current = loadedProfile,
                        ).onSuccess {
                            Toast.makeText(context, "资料已同步", Toast.LENGTH_SHORT).show()
                            show = false
                        }.onFailure { err ->
                            if (err is MemberProfileRepository.WebFallbackException) {
                                webFallbackMessage = err.message
                            } else {
                                Toast.makeText(context, err.message ?: "修改资料失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        saving = false
                    }
                }
            },
        )
    }

    cropUri?.let { uri ->
        AvatarCropDialog(
            sourceUri = uri,
            uploading = uploadingAvatar,
            onDismiss = { if (!uploadingAvatar) cropUri = null },
            onConfirm = { bytes ->
                if (!uploadingAvatar) {
                    uploadingAvatar = true
                    scope.launch {
                        profileRepo.updateAvatar(bytes = bytes)
                            .onSuccess { newUrl ->
                                cropUri = null
                                if (newUrl.isNotBlank()) avatarUrl = newUrl
                                Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { err ->
                                if (err is MemberProfileRepository.WebFallbackException) {
                                    cropUri = null
                                    webFallbackMessage = err.message
                                } else {
                                    Toast.makeText(context, err.message ?: "更换头像失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        uploadingAvatar = false
                    }
                }
            },
        )
    }

    webFallbackMessage?.let { msg ->
        ConfirmDialog(
            title = "未能直接修改",
            message = "$msg。是否打开 B 站网页版继续编辑？",
            confirmText = "打开网页版",
            onDismiss = { webFallbackMessage = null },
            onConfirm = {
                webFallbackMessage = null
                show = false
                onOpenWeb()
            },
        )
    }
}

private data class PendingProfileSave(
    val uname: String?,
    val sign: String?,
    val sex: String?,
)

@Composable
private fun GenderSelectorRow(
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "性别",
            fontSize = 13.sp,
            color = scheme.onSurfaceSecondary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )
        GENDER_OPTIONS.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .background(
                        if (isSelected) scheme.primary.copy(alpha = 0.14f) else scheme.surfaceContainer,
                    )
                    .clickable(enabled = enabled) { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) scheme.primary else scheme.onSurface,
                )
            }
        }
    }
}
