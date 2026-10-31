package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GamePlayer
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.game.model.GameState
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant

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

// everyone in the game, opened from "See all"
@Composable
internal fun PlayersDialog(
    details: GameDetails,
    removingPlayerId: Int?,
    onRemove: (GamePlayer) -> Unit,
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
                        Avatar(text = player.initial, size = 32.dp, fontSize = 13)
                        Text(text = player.fullName, color = SportsOnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
