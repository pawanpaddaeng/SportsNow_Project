package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant

// the Game Details screen's dialogs

// "Cancel this game?" with Cancel Game and Keep Game
@Composable
internal fun ConfirmCancelDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this game?") },
        text = {
            Text("$title will be cancelled for everyone. Players and anyone who asked to join will be notified. This can't be undone.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Cancel Game", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep Game") }
        }
    )
}

// everyone in the game, opened from "See all"
@Composable
internal fun PlayersDialog(details: GameDetails, onDismiss: () -> Unit) {
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
                        if (player == details.organizer) {
                            Text(text = "Organizer", color = SportsOnSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    )
}
