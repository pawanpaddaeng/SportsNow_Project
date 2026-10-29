package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.game.model.GameState
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary

// why the game can't be joined or changed any more, null while it's still on
private fun closedMessage(details: GameDetails): String? = when {
    details.isCancelled -> "This game was cancelled."
    details.isCompleted -> "This game was completed."
    !details.hasStarted -> null
    details.role == GameRole.Organizer -> "This game has started. Mark it completed once it's over."
    else -> "This game has already started."
}

/*  buttons at the bottom of Game Details, depending on the user's role and whether the
    game can still change. The organizer cancels a game that's on and marks it completed
    once it has started. A player can leave at any time, even mid-game, and the organizer
    once it's cancelled or completed
 */
@Composable
internal fun ActionButtons(
    details: GameDetails,
    busyAction: GameAction?,
    onOpenChat: () -> Unit,
    onGameAction: (GameAction) -> Unit,
    onRequestToJoin: () -> Unit
) {
    val message = closedMessage(details)
    if (message != null) {
        Text(
            text = message,
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (details.role == GameRole.Viewer) return
    }

    when (details.role) {
        GameRole.Organizer, GameRole.Player -> {
            PrimaryButton(text = "Open Chat", onClick = onOpenChat)
            val action = when {
                details.role == GameRole.Player -> GameAction.Leave
                details.state == GameState.Upcoming -> GameAction.Cancel
                details.state == GameState.InProgress -> GameAction.Complete
                else -> GameAction.Leave
            }
            OutlinedActionButton(
                text = when (action) {
                    GameAction.Cancel -> "Cancel Game"
                    GameAction.Complete -> "Mark as Completed"
                    GameAction.Leave -> "Leave Game"
                },
                onClick = { onGameAction(action) },
                isBusy = busyAction == action,
                color = if (action == GameAction.Complete) SportsPrimary else MaterialTheme.colorScheme.error
            )
        }
        GameRole.Viewer -> JoinButton(details = details, onRequestToJoin = onRequestToJoin)
    }
}

// Request to Join, or why the user can't ask, with a note about notifications
@Composable
private fun JoinButton(details: GameDetails, onRequestToJoin: () -> Unit) {
    val requestPending = details.joinRequestStatus == "pending"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PrimaryButton(
            text = when {
                requestPending -> "Request sent"
                details.spotsLeft == 0 -> "Game is full"
                else -> "Request to Join"
            },
            onClick = onRequestToJoin,
            enabled = !requestPending && details.spotsLeft > 0
        )
        Text(
            text = if (requestPending) {
                "Waiting for the organizer to respond. You will be notified when they do."
            } else {
                "The organizer reviews requests. You will be notified when they respond."
            },
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/*  outlined button for leaving, cancelling or completing, red text unless another
    color is given. isBusy shows a spinner instead
 */
@Composable
private fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    isBusy: Boolean = false,
    color: Color = MaterialTheme.colorScheme.error
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isBusy,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, SportsOutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                color = color,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
