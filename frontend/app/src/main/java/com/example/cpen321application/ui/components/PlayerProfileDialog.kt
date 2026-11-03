package com.example.cpen321application.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.friend.model.FriendshipStatus
import com.example.cpen321application.user.model.PlayerProfile
import com.example.cpen321application.review.model.formatReputationScore
import com.example.cpen321application.ui.theme.SportsOnPrimaryContainer
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsPrimaryContainer
import com.example.cpen321application.ui.theme.SportsSurfaceContainer

/*  what the profile popup shows. profile is null until it loads; isLoading shows a spinner and
    errorMessage a problem, with Retry when canRetry. Once the profile is shown, errorMessage is
    a friend request that couldn't be sent. isAddingFriend shows progress on Add Friend
 */
data class PlayerProfileUiState(
    val profile: PlayerProfile? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = true,
    val isAddingFriend: Boolean = false
)

/*  another player's public profile in a popup: name, age range, games played, reputation and
    badges. Add Friend shows for anyone who isn't a friend yet, never on the user's own profile.
    Close returns to the screen underneath
 */
@Composable
fun PlayerProfileDialog(
    state: PlayerProfileUiState,
    onRetry: () -> Unit,
    onAddFriend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            val profile = state.profile
            when {
                profile != null -> ProfileDetails(profile, state, onAddFriend)
                state.isLoading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    CircularProgressIndicator(color = SportsPrimary)
                }
                else -> RetryMessage(
                    message = state.errorMessage ?: "Couldn't load this profile, try again",
                    onRetry = onRetry.takeIf { state.canRetry },
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    )
}

@Composable
private fun ProfileDetails(profile: PlayerProfile, state: PlayerProfileUiState, onAddFriend: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Initial(profile.initial)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = profile.name,
                color = SportsOnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (profile.isSelf) "This is you · Age ${profile.ageRange}" else "Age ${profile.ageRange}",
                color = SportsOnSurfaceVariant,
                fontSize = 14.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat(value = profile.gamesPlayed.toString(), label = "Games played", modifier = Modifier.weight(1f))
            Stat(
                value = profile.reputationScore?.let(::formatReputationScore) ?: "–",
                label = "Rating",
                modifier = Modifier.weight(1f)
            )
        }
        Badges(profile.badges)
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        FriendStatus(profile, isAdding = state.isAddingFriend, onAddFriend = onAddFriend)
    }
}

@Composable
private fun Initial(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(SportsPrimaryContainer)
    ) {
        Text(text = text, color = SportsOnPrimaryContainer, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}

// a number with what it counts underneath, in a grey card
@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .cardBackground(SportsSurfaceContainer)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text(text = value, color = SportsOnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = SportsOnSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

// the badges the player earned, as pills
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(badges: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = "Badges", color = SportsOnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (badges.isEmpty()) {
            Text(text = "No badges yet", color = SportsOnSurfaceVariant, fontSize = 14.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                badges.forEach { badge ->
                    Text(
                        text = badge,
                        color = SportsOnPrimaryContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.pillBackground(SportsPrimaryContainer, borderColor = null)
                    )
                }
            }
        }
    }
}

// Add Friend for someone who isn't a friend yet, otherwise where things stand. Nothing on the user's own profile
@Composable
private fun FriendStatus(profile: PlayerProfile, isAdding: Boolean, onAddFriend: () -> Unit) {
    if (profile.isSelf) return
    when (profile.friendship) {
        FriendshipStatus.None -> Button(
            onClick = onAddFriend,
            enabled = !isAdding,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            if (isAdding) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text(text = "Add Friend", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        FriendshipStatus.Friends -> StatusNote("You're friends")
        FriendshipStatus.RequestSent -> StatusNote("Friend request sent")
        FriendshipStatus.RequestReceived -> StatusNote("Sent you a friend request. Answer it in the Friends tab.")
    }
}

@Composable
private fun StatusNote(text: String) {
    Text(
        text = text,
        color = SportsPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}