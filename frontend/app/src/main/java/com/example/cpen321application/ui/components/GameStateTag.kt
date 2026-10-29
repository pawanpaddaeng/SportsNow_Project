package com.example.cpen321application.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameState
import com.example.cpen321application.ui.theme.SportsOnPrimaryContainer
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsPrimaryContainer

// small rounded label, with a check before the text when showCheck
@Composable
fun Pill(
    text: String,
    background: Color,
    textColor: Color,
    borderColor: Color? = null,
    showCheck: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.pillBackground(background, borderColor)
    ) {
        if (showCheck) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
        }
        Text(text = text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/*  the game's state as a tag, the same on every screen. Cancelled is outlined in red so
    it doesn't look like the organizer's solid red requests badge
 */
@Composable
fun GameStateTag(state: GameState) {
    when (state) {
        GameState.Upcoming ->
            Pill(text = "Upcoming", background = SportsPrimaryContainer, textColor = SportsOnPrimaryContainer)
        GameState.InProgress ->
            Pill(text = "In progress", background = SportsPrimary, textColor = Color.White)
        GameState.Completed ->
            Pill(text = "Completed", background = SportsOutlineVariant, textColor = SportsOnSurface, showCheck = true)
        GameState.Cancelled -> Pill(
            text = "Cancelled",
            background = Color.White,
            textColor = MaterialTheme.colorScheme.error,
            borderColor = MaterialTheme.colorScheme.error
        )
    }
}
