package erl.bilico.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.AppealTag
import erl.bilico.di.LocalAppContainer
import erl.bilico.feature.video.PlayerModalHost
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

sealed interface ReportTarget {
    data class Video(val aid: Long, val bvid: String = "", val title: String = "") : ReportTarget
    data class Member(val mid: Long, val name: String = "") : ReportTarget
    data class Dynamic(
        val dynamicId: String,
        val uid: Long,
        val authorName: String = "",
        val authorFace: String = "",
    ) : ReportTarget

    data class Comment(
        val oid: Long,
        val rpid: Long,
        val mid: Long,
        val userName: String = "",
        val avatar: String = "",
        val type: Int = 1,
    ) : ReportTarget
}

private data class ReportGroup(
    val title: String,
    val options: List<Pair<Int, String>>,
)

private val MEMBER_PRIMARY_REASONS = listOf(
    1 to "头像违规",
    2 to "昵称违规",
    3 to "签名违规",
)

private val MEMBER_V2_REASONS = listOf(
    1 to "色情低俗",
    2 to "不实信息",
    3 to "违禁",
    4 to "人身攻击",
    5 to "赌博诈骗",
    6 to "违规引流外链",
)

private val DYNAMIC_REASONS = listOf(
    4 to "垃圾广告",
    8 to "引战",
    1 to "色情",
    5 to "人身攻击",
    3 to "违法信息",
    9 to "涉政谣言",
    10 to "涉社会事件谣言",
    12 to "虚假不实信息",
    13 to "违法信息外链",
    0 to "其他",
)

private val COMMENT_GROUPS = listOf(
    ReportGroup(
        "违反法律法规",
        listOf(9 to "违法违规", 2 to "色情", 10 to "低俗", 12 to "赌博诈骗", 23 to "违法信息外链"),
    ),
    ReportGroup(
        "谣言类不实信息",
        listOf(19 to "涉政谣言", 22 to "虚假不实信息", 20 to "涉社会事件谣言"),
    ),
    ReportGroup(
        "侵犯个人权益",
        listOf(7 to "人身攻击", 15 to "侵犯隐私"),
    ),
    ReportGroup(
        "有害社区环境",
        listOf(
            1 to "垃圾广告",
            4 to "引战",
            5 to "剧透",
            3 to "刷屏",
            8 to "抢楼",
            18 to "青少年不良信息",
            17 to "其他",
        ),
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDialog(
    target: ReportTarget,
    inlineInStage: Boolean = false,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = COUITheme.colorScheme

    var videoTags by remember { mutableStateOf<List<AppealTag>>(emptyList()) }
    var selectedVideoTid by remember { mutableIntStateOf(-1) }
    val selectedMemberPrimary = remember { mutableStateListOf<Int>() }
    var selectedMemberV2 by remember { mutableStateOf<Int?>(null) }
    var selectedDynamicReason by remember { mutableIntStateOf(DYNAMIC_REASONS.first().first) }
    var selectedCommentReason by remember { mutableIntStateOf(1) }
    var desc by remember { mutableStateOf("") }
    var addBlacklist by remember { mutableStateOf(false) }
    var blockLocally by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        if (target is ReportTarget.Video) {
            val loaded = container.reports.appealTags().getOrDefault(emptyList())
            videoTags = loaded
            if (selectedVideoTid < 0 && loaded.isNotEmpty()) {
                selectedVideoTid = loaded.first().tid
            }
        }
    }

    val title = when (target) {
        is ReportTarget.Video -> "举报视频"
        is ReportTarget.Member -> "举报用户"
        is ReportTarget.Dynamic -> "举报动态"
        is ReportTarget.Comment -> "举报评论"
    }

    val subtitle = when (target) {
        is ReportTarget.Video -> target.title
        is ReportTarget.Member -> target.name
        is ReportTarget.Dynamic -> target.authorName
        is ReportTarget.Comment -> target.userName
    }

    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = { if (!submitting) onDismiss() },
        maxWidth = 420.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 20.dp),
                style = COUITheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (inlineInStage) 210.dp else 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (target) {
                    is ReportTarget.Video -> {
                        if (videoTags.isEmpty()) {
                            Text(
                                text = "正在加载举报原因...",
                                style = COUITheme.textStyles.body2,
                                color = scheme.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                videoTags.forEach { tag ->
                                    ReasonChip(
                                        text = tag.name,
                                        selected = selectedVideoTid == tag.tid,
                                        onClick = { selectedVideoTid = tag.tid },
                                    )
                                }
                            }
                        }
                        TextField(
                            value = desc,
                            onValueChange = { desc = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "补充说明（可选）",
                            backgroundMode = TextFieldMode.Rectangle,
                            minLines = 2,
                            maxLines = 4,
                        )
                    }

                    is ReportTarget.Member -> {
                        Text(
                            text = "基础信息违规（至少选一项）",
                            style = COUITheme.textStyles.footnote1,
                            color = scheme.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MEMBER_PRIMARY_REASONS.forEach { (code, label) ->
                                val picked = code in selectedMemberPrimary
                                ReasonChip(
                                    text = label,
                                    selected = picked,
                                    onClick = {
                                        if (picked) selectedMemberPrimary.remove(code)
                                        else selectedMemberPrimary.add(code)
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "其他违规类型（可选）",
                            style = COUITheme.textStyles.footnote1,
                            color = scheme.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MEMBER_V2_REASONS.forEach { (code, label) ->
                                val picked = selectedMemberV2 == code
                                ReasonChip(
                                    text = label,
                                    selected = picked,
                                    onClick = {
                                        selectedMemberV2 = if (picked) null else code
                                    },
                                )
                            }
                        }
                    }

                    is ReportTarget.Dynamic -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DYNAMIC_REASONS.forEach { (code, label) ->
                                ReasonChip(
                                    text = label,
                                    selected = selectedDynamicReason == code,
                                    onClick = { selectedDynamicReason = code },
                                )
                            }
                        }
                        TextField(
                            value = desc,
                            onValueChange = { desc = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = if (selectedDynamicReason == 0) "请填写具体原因" else "补充说明（可选）",
                            backgroundMode = TextFieldMode.Rectangle,
                            minLines = 2,
                            maxLines = 4,
                        )
                        ReportCheckRow(
                            label = "同时加入账号黑名单",
                            checked = addBlacklist,
                            onToggle = { addBlacklist = !addBlacklist },
                        )
                        ReportCheckRow(
                            label = "同时本地屏蔽该 UP 主",
                            checked = blockLocally,
                            onToggle = { blockLocally = !blockLocally },
                        )
                    }

                    is ReportTarget.Comment -> {
                        COMMENT_GROUPS.forEach { group ->
                            Text(
                                text = group.title,
                                style = COUITheme.textStyles.footnote1,
                                color = scheme.onSurfaceSecondary,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                group.options.forEach { (code, label) ->
                                    ReasonChip(
                                        text = label,
                                        selected = selectedCommentReason == code,
                                        onClick = { selectedCommentReason = code },
                                    )
                                }
                            }
                        }
                        val requireContent = selectedCommentReason == 22 || selectedCommentReason == 17
                        TextField(
                            value = desc,
                            onValueChange = { desc = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = if (requireContent) "请填写具体说明" else "补充说明（可选）",
                            backgroundMode = TextFieldMode.Rectangle,
                            minLines = 2,
                            maxLines = 4,
                        )
                        if (target.mid > 0L) {
                            ReportCheckRow(
                                label = "同时拉黑该用户",
                                checked = addBlacklist,
                                onToggle = { addBlacklist = !addBlacklist },
                            )
                            ReportCheckRow(
                                label = "同时本地屏蔽该用户",
                                checked = blockLocally,
                                onToggle = { blockLocally = !blockLocally },
                            )
                        }
                    }
                }
            }

            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "取消",
                    onClick = { if (!submitting) onDismiss() },
                ),
                positive = DialogButtonBarAction(
                    text = if (submitting) "提交中..." else "提交举报",
                    onClick = {
                        if (submitting) return@DialogButtonBarAction
                        if (!container.session.isLoggedIn()) {
                            Toast.makeText(context, "登录后可用", Toast.LENGTH_SHORT).show()
                            return@DialogButtonBarAction
                        }
                        when (target) {
                            is ReportTarget.Video -> if (selectedVideoTid < 0) {
                                Toast.makeText(context, "请选择举报原因", Toast.LENGTH_SHORT).show()
                                return@DialogButtonBarAction
                            }

                            is ReportTarget.Member -> if (selectedMemberPrimary.isEmpty()) {
                                Toast.makeText(context, "请至少选择一项违规信息", Toast.LENGTH_SHORT).show()
                                return@DialogButtonBarAction
                            }

                            is ReportTarget.Dynamic -> if (selectedDynamicReason == 0 && desc.isBlank()) {
                                Toast.makeText(context, "请填写具体原因", Toast.LENGTH_SHORT).show()
                                return@DialogButtonBarAction
                            }

                            is ReportTarget.Comment -> if ((selectedCommentReason == 22 || selectedCommentReason == 17) && desc.isBlank()) {
                                Toast.makeText(context, "请填写具体说明", Toast.LENGTH_SHORT).show()
                                return@DialogButtonBarAction
                            }
                        }

                        submitting = true
                        scope.launch {
                            val result = when (target) {
                                is ReportTarget.Video -> container.reports.reportVideo(
                                    aid = target.aid,
                                    tid = selectedVideoTid,
                                    desc = desc.trim(),
                                )

                                is ReportTarget.Member -> container.reports.reportMember(
                                    mid = target.mid,
                                    reasonCodes = selectedMemberPrimary.toList(),
                                    reasonV2 = selectedMemberV2,
                                )

                                is ReportTarget.Dynamic -> container.reports.reportDynamic(
                                    accusedUid = target.uid,
                                    dynamicId = target.dynamicId,
                                    reasonType = selectedDynamicReason,
                                    reasonDesc = desc.trim(),
                                ).onSuccess {
                                    if (addBlacklist) {
                                        container.blocks.toggleServerBlacklist(
                                            mid = target.uid,
                                            add = true,
                                            name = target.authorName,
                                            face = target.authorFace,
                                        )
                                    }
                                    if (blockLocally) {
                                        container.blocks.blockUserLocally(
                                            mid = target.uid,
                                            name = target.authorName,
                                            face = target.authorFace,
                                        )
                                    }
                                }

                                is ReportTarget.Comment -> container.reports.reportComment(
                                    oid = target.oid,
                                    rpid = target.rpid,
                                    reason = selectedCommentReason,
                                    content = desc.trim(),
                                    addBlacklist = addBlacklist,
                                    type = target.type,
                                ).onSuccess {
                                    if (addBlacklist) {
                                        container.blocks.syncServerBlacklist()
                                    }
                                    if (blockLocally && target.mid > 0L) {
                                        container.blocks.blockUserLocally(
                                            mid = target.mid,
                                            name = target.userName,
                                            face = target.avatar,
                                        )
                                    }
                                }
                            }
                            submitting = false
                            result.onSuccess {
                                Toast.makeText(context, "举报成功", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.message ?: "举报失败",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ),
            )
        }
    }
}

@Composable
private fun ReasonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReportCheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = COUITheme.textStyles.body2,
            color = scheme.onSurface,
        )
    }
}
