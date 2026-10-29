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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary

// buttons at the bottom of Game Details, depending on the user's role and whether the game can still change
@Composable
internal fun ActionButtons(
    details: GameDetails,
    isCancelling: Boolean,
    onOpenChat: () -> Unit,
    onCancelGame: () -> Unit,
    onLeaveGame: () -> Unit,
    onRequestToJoin: () -> Unit
) {
    if (details.isCancelled || details.hasStarted) {
        Text(
            text = if (details.isCancelled) "This game was cancelled." else "This game has already started.",
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
            if (!details.isCancelled && !details.hasStarted) {
                if (details.role == GameRole.Organizer) {
                    DangerButton(text = "Cancel Game", onClick = onCancelGame, isBusy = isCancelling)
                } else {
                    DangerButton(text = "Leave Game", onClick = onLeaveGame)
                }
            }
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

// outlined button with red text for leaving or cancelling, isBusy shows a spinner instead
@Composable
private fun DangerButton(text: String, onClick: () -> Unit, isBusy: Boolean = false) {
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
                color = MaterialTheme.colorScheme.error,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
