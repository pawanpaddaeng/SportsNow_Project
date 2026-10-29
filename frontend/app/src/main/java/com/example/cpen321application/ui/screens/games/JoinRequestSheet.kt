package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.ui.components.ChipGroup
import com.example.cpen321application.ui.components.FieldLabel
import com.example.cpen321application.ui.components.sportsFieldColors
import com.example.cpen321application.ui.theme.*
import java.util.Locale

// longest message to the organizer, matches join_requests.message in the database
private const val MAX_MESSAGE_LENGTH = 200

// label shown on the chip and the value sent to the server
private val REQUEST_SKILL_LEVELS = listOf(
    "Beginner" to "beginner",
    "Intermediate" to "intermediate",
    "Advanced" to "advanced",
    "Competitive" to "competitive"
)
private val PLAY_FREQUENCIES = listOf(
    "Rarely" to "rarely",
    "Monthly" to "monthly",
    "Weekly" to "weekly",
    "Several times a week" to "several_per_week"
)

// the popup's answers, skillLevel and playFrequency use the server's values
data class JoinRequestAnswers(
    val skillLevel: String,
    val playFrequency: String?,
    val message: String
)

/*
    "Request to join" popup from the Figma mockup, opened from Game Details.
    Skill level is required, how often they play and the message are optional.
    isSending shows progress on Send Request while the request is sent, errorMessage
    shows why it failed (the answers stay so the user can try again)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRequestSheet(
    details: GameDetails,
    onDismiss: () -> Unit,
    onSend: (JoinRequestAnswers) -> Unit,
    isSending: Boolean = false,
    errorMessage: String? = null
) {
    var skillLevel by rememberSaveable { mutableStateOf<String?>(null) }
    var playFrequency by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSending) onDismiss() },
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            SheetHeader(details = details, onClose = onDismiss, closeEnabled = !isSending)

            ChipGroup(
                label = "What is your skill level in ${details.sport.lowercase(Locale.getDefault())}? *",
                options = REQUEST_SKILL_LEVELS,
                selected = skillLevel,
                onSelect = { skillLevel = it }
            )

            // optional, tapping the selected chip again clears it
            ChipGroup(
                label = "How often do you play?",
                options = PLAY_FREQUENCIES,
                selected = playFrequency,
                onSelect = { playFrequency = if (it == playFrequency) null else it }
            )

            MessageField(value = message, onValueChange = { message = it.take(MAX_MESSAGE_LENGTH) })

            Text(
                text = "${details.organizer.firstName} reviews requests. You will get a notification when they respond.",
                color = SportsOnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SportsSurfaceContainer)
                    .padding(12.dp)
            )

            if (errorMessage != null) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Buttons(
                canSend = skillLevel != null && !isSending,
                isSending = isSending,
                onCancel = onDismiss,
                onSend = {
                    skillLevel?.let { skill ->
                        onSend(JoinRequestAnswers(skillLevel = skill, playFrequency = playFrequency, message = message))
                    }
                }
            )
        }
    }
}

// "Request to join", the game's title and time, and a close button
@Composable
private fun SheetHeader(details: GameDetails, onClose: () -> Unit, closeEnabled: Boolean) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Request to join", color = SportsOnSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${details.title} · ${details.whenText}",
                color = SportsOnSurfaceVariant,
                fontSize = 14.sp
            )
        }
        IconButton(onClick = onClose, enabled = closeEnabled) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = SportsOnSurface)
        }
    }
}

// optional message with a "27 / 200" counter under it
@Composable
private fun MessageField(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel("Message to organizer (optional)")
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = 3,
            placeholder = { Text("e.g. Happy to play any position!") },
            shape = RoundedCornerShape(8.dp),
            colors = sportsFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${value.length} / $MAX_MESSAGE_LENGTH",
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Buttons(canSend: Boolean, isSending: Boolean, onCancel: () -> Unit, onSend: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onCancel,
            enabled = !isSending,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, SportsOutline),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text(text = "Cancel", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Button(
            onClick = onSend,
            enabled = canSend,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            if (isSending) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(text = "Send Request", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
