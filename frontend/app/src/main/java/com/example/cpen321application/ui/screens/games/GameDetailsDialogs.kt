package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GamePlayer
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.game.model.GameState
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimary

// the Game Details screen's dialogs

// what a confirmation dialog says: its question, what happens, and its two buttons
private data class ConfirmText(
    val title: String,
    val body: String,
    val confirm: String,
    val dismiss: String,
    // red confirm button for actions that take something away
    val destructive: Boolean = true
)

/*  what confirming the action does to this game. A player leaving before the game starts
    gives up their spot, or leaves mid-game once it started. Anyone leaving a cancelled or
    completed game just stops seeing it on Home
 */
private fun confirmText(action: GameAction, details: GameDetails): ConfirmText = when (action) {
    GameAction.Cancel -> ConfirmText(
        title = "Cancel this game?",
        body = "${details.title} will be cancelled for everyone. Players and anyone who asked to join " +
            "will be notified. This can't be undone.",
        confirm = "Cancel Game",
        dismiss = "Keep Game"
    )
    GameAction.Complete -> ConfirmText(
        title = "Mark this game completed?",
        body = "${details.title} will be marked completed for everyone and its players will be notified. " +
            "This can't be undone.",
        confirm = "Mark Completed",
        dismiss = "Not Yet",
        destructive = false
    )
    GameAction.Leave -> ConfirmText(
        title = "Leave this game?",
        body = when (details.state) {
            GameState.Cancelled, GameState.Completed -> "${details.title} will be removed from your games."
            GameState.InProgress ->
                "You'll leave ${details.title} while it's in progress and the organizer will be notified."
            GameState.Upcoming -> "You'll give up your spot in ${details.title} and the organizer will be notified. " +
                "To play again you'll need to send a new join request."
        },
        confirm = "Leave Game",
        dismiss = "Stay"
    )
}

// asks the user to confirm a Game Details action before it's sent
@Composable
internal fun ConfirmActionDialog(
    action: GameAction,
    details: GameDetails,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val text = confirmText(action, details)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.title) },
        text = { Text(text.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                if (text.destructive) {
                    Text(text.confirm, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(text.confirm)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text.dismiss) }
        }
    )
}

/*  marks the game completed: every player starts ticked as having shown up and the
    organizer unticks anyone who didn't. The organizer isn't listed, they're the one marking it
 */
@Composable
internal fun CompleteGameDialog(
    details: GameDetails,
    onConfirm: (noShowUserIds: List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val text = confirmText(GameAction.Complete, details)
    val players = details.players.filter { it.userId != details.organizer.userId }
    var noShowUserIds by remember { mutableStateOf(emptySet<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(text.body)
                if (players.isNotEmpty()) {
                    Text(text = "Untick anyone who didn't show up.", color = SportsOnSurface, fontSize = 14.sp)
                }
                players.forEach { player ->
                    AttendanceRow(
                        player = player,
                        showedUp = player.userId !in noShowUserIds,
                        onChange = { showedUp ->
                            noShowUserIds = if (showedUp) noShowUserIds - player.userId else noShowUserIds + player.userId
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(noShowUserIds.toList()) }) { Text(text.confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text.dismiss) }
        }
    )
}

// a player with a tick for having shown up, the whole row toggles it
@Composable
private fun AttendanceRow(player: GamePlayer, showedUp: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = showedUp, role = Role.Checkbox, onValueChange = onChange)
    ) {
        Avatar(text = player.initial, size = 32.dp, fontSize = 13)
        Text(text = player.fullName, color = SportsOnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Checkbox(
            checked = showedUp,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = SportsPrimary)
        )
    }
}

// everyone in the game, opened from "See all". Tapping a player opens their profile
@Composable
internal fun PlayersDialog(
    details: GameDetails,
    removingPlayerId: Int?,
    onRemove: (GamePlayer) -> Unit,
    onOpenProfile: (GamePlayer) -> Unit,
    onDismiss: () -> Unit
) {
    // the organizer can remove players until the game starts
    val canRemove = details.role == GameRole.Organizer && details.state == GameState.Upcoming
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Players · ${details.playerCount}/${details.maxPlayers}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                details.players.forEach { player ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerName(player = player, onClick = { onOpenProfile(player) })
                        if (player.userId == details.organizer.userId) {
                            Text(text = "Organizer", color = SportsOnSurfaceVariant, fontSize = 12.sp)
                        } else if (canRemove) {
                            RemoveButton(removingPlayerId = removingPlayerId, player = player, onRemove = onRemove)
                        }
                    }
                }
            }
        }
    )
}

// the player's initial and name, tapping them opens their profile
@Composable
private fun RowScope.PlayerName(player: GamePlayer, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "View profile", onClick = onClick)
    ) {
        Avatar(text = player.initial, size = 32.dp, fontSize = 13)
        Text(text = player.fullName, color = SportsOnSurface, fontSize = 16.sp)
    }
}

// Remove next to a player, a spinner while they're being removed, disabled while another one is
@Composable
private fun RemoveButton(removingPlayerId: Int?, player: GamePlayer, onRemove: (GamePlayer) -> Unit) {
    if (removingPlayerId == player.userId) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.error, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    } else {
        TextButton(onClick = { onRemove(player) }, enabled = removingPlayerId == null) {
            Text("Remove", color = MaterialTheme.colorScheme.error)
        }
    }
}

// "Remove this player?" with Remove and Keep, they can still ask to join again later
@Composable
internal fun ConfirmRemovePlayerDialog(
    player: GamePlayer,
    gameTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${player.fullName}?") },
        text = {
            Text(
                "${player.fullName} will be removed from $gameTitle and notified. " +
                    "They can send a new request to join later."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep") }
        }
    )
}
