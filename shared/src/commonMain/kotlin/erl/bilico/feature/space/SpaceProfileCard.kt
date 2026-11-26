package erl.bilico.feature.space

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.SpaceProfile
import erl.bilico.data.util.Formatters
import erl.bilico.feature.comment.LikeFilledIcon
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.FollowCapsuleButton
import erl.bilico.ui.components.OfficialVerifyLabel
import erl.bilico.ui.nestedBlockColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun ProfileCard(
    profile: SpaceProfile,
    showFollow: Boolean,
    following: Boolean,
    followBusy: Boolean,
    onFollow: () -> Unit,
    onOpenLiveRoom: (() -> Unit)? = null,
) {
    val avatarUrl = remember(profile.face) { Formatters.sizedImageUrl(profile.face, 192, 192) }
    val hasVerify = profile.officialType == 0 || profile.officialType == 1 || profile.officialDesc.isNotBlank()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(url = avatarUrl, size = 64.dp, officialType = profile.officialType)
                Spacer(Modifier.width(14.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileStatColumn(label = "粉丝", value = profile.followersText, modifier = Modifier.weight(1f))
                    ProfileStatColumn(label = "关注", value = profile.followingText, modifier = Modifier.weight(1f))
                    if (profile.likesText.isNotBlank()) {
                        ProfileLikeColumn(value = profile.likesText, modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = profile.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showFollow) {
                    Spacer(Modifier.width(10.dp))
                    FollowCapsuleButton(
                        following = following,
                        onClick = onFollow,
                        enabled = !followBusy,
                        followedBackground = nestedBlockColor(),
                    )
                }
            }

            if (hasVerify) {
                Spacer(Modifier.height(8.dp))
                OfficialVerifyLabel(type = profile.officialType, desc = profile.officialDesc)
            }

            if (profile.sign.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = profile.sign,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        lineHeight = 17.sp,
                    )
                }
            }

            if (onOpenLiveRoom != null) {
                Spacer(Modifier.height(10.dp))
                LiveRoomShortcutRow(
                    isLive = profile.isLive,
                    liveTitle = profile.liveTitle,
                    onClick = onOpenLiveRoom,
                )
            }
        }
    }
}

@Composable
private fun ProfileStatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = COUITheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileLikeColumn(
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = COUITheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "获赞",
                fontSize = 11.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = LikeFilledIcon,
                contentDescription = "获赞",
                modifier = Modifier.size(11.dp),
                tint = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}
