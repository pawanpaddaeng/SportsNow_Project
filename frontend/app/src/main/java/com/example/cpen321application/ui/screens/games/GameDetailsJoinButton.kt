package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant

// Request to Join, or why the user can't ask, with a note about notifications
@Composable
internal fun JoinButton(details: GameDetails, onRequestToJoin: () -> Unit) {
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
