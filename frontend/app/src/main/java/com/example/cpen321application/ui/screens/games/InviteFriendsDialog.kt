package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.friend.model.FriendPerson
import com.example.cpen321application.ui.components.RetryMessage
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimary

/*  what the friend picker shows. friends is null until they load; isLoading shows a
    spinner, errorMessage a problem (with Retry if the friends didn't load) and
    isSending progress on Invite
 */
data class InviteFriendsUiState(
    val friends: List<FriendPerson>? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSending: Boolean = false
)

/*  the organizer's friend picker: each friend who isn't in the game yet (playerIds) with a
    checkbox. Invite sends the ticked friends' ids
 */
@Composable
internal fun InviteFriendsDialog(
    state: InviteFriendsUiState,
    playerIds: Set<Int>,
    onRetry: () -> Unit,
    onInvite: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(setOf<Int>()) }
    val friends = state.friends
    val invitable = friends.orEmpty().filter { it.userId !in playerIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Friends") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // sending failed, the friends stay listed below
                if (state.errorMessage != null && friends != null) {
                    Text(text = state.errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
                when {
                    friends == null && state.isLoading -> CircularProgressIndicator(color = SportsPrimary)
                    friends == null -> RetryMessage(
                        message = state.errorMessage ?: "Couldn't load your friends, try again",
                        onRetry = onRetry
                    )
                    friends.isEmpty() -> EmptyMessage("Add friends in the Friends tab to invite them to your games.")
                    invitable.isEmpty() -> EmptyMessage("All your friends are already in this game.")
                    else -> FriendChecklist(
                        friends = invitable,
                        selected = selected,
                        onToggle = { userId ->
                            selected = if (userId in selected) selected - userId else selected + userId
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInvite(selected.toList()) }, enabled = selected.isNotEmpty() && !state.isSending) {
                if (state.isSending) {
                    CircularProgressIndicator(color = SportsPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(if (selected.isEmpty()) "Invite" else "Invite (${selected.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Text(text = text, color = SportsOnSurfaceVariant, fontSize = 14.sp)
}

// one row per friend, tapping anywhere on the row ticks or unticks them
@Composable
private fun FriendChecklist(friends: List<FriendPerson>, selected: Set<Int>, onToggle: (Int) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        friends.forEach { friend ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = friend.userId in selected,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(friend.userId) }
                    )
            ) {
                Avatar(text = friend.initial, size = 32.dp, fontSize = 13)
                Text(text = friend.name, color = SportsOnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                // the row handles taps, so the checkbox only shows the state
                Checkbox(checked = friend.userId in selected, onCheckedChange = null)
            }
        }
    }
}