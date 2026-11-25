package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.game.model.GameState
import com.example.cpen321application.ui.components.FormSubmitButton
import com.example.cpen321application.ui.components.OutlinedActionButton
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
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
    game can still change. The organizer invites friends and cancels a game that's on,
    and marks it completed once it has started. A player can leave at any time, even
    mid-game, and the organizer once it's cancelled or completed. Anyone who showed up
    can rate players once it's completed
 */
@Composable
internal fun ActionButtons(
    details: GameDetails,
    busyAction: GameAction?,
    onOpenChat: () -> Unit,
    onInviteFriends: () -> Unit,
    onGameAction: (GameAction) -> Unit,
    onRatePlayers: () -> Unit,
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
            // the server only accepts invites before the game starts and while it has spots left
            if (details.role == GameRole.Organizer && details.state == GameState.Upcoming && details.spotsLeft > 0) {
                OutlinedActionButton(text = "Invite Friends", onClick = onInviteFriends, color = SportsPrimary)
            }
            val action = when {
                details.role == GameRole.Player -> GameAction.Leave
                details.state == GameState.Upcoming -> GameAction.Cancel
                details.state == GameState.InProgress -> GameAction.Complete
                else -> GameAction.Leave
            }
            OutlinedActionButton(
                text = action.buttonLabel,
                onClick = { onGameAction(action) },
                isBusy = busyAction == action,
                color = if (action == GameAction.Complete) SportsPrimary else MaterialTheme.colorScheme.error
            )
            if (details.canRatePlayers) {
                OutlinedActionButton(text = "Rate Players", onClick = onRatePlayers, color = SportsPrimary)
            }
        }
        GameRole.Viewer -> JoinButton(details = details, onRequestToJoin = onRequestToJoin)
    }
}

// the full-width filled button used for Game Details' main action
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    FormSubmitButton(text = text, isSending = false, onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled)
}
