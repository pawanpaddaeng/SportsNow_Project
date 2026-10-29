package com.example.cpen321application.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.DiscoverGame
import com.example.cpen321application.game.spotsLeftLabel
import com.example.cpen321application.ui.components.pillBackground
import com.example.cpen321application.ui.theme.SportsOnPrimaryContainer
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsPrimaryContainer
import com.example.cpen321application.ui.theme.SportsSurfaceContainer
import java.util.Locale

// faded look for games that are already full, on the cards and the map pins
internal const val FULL_GAME_ALPHA = 0.5f

// "1. Volleyball Drop-in" card with time, place, distance and status pills
@Composable
internal fun GameCard(rank: Int, game: DiscoverGame, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (game.isFull) FULL_GAME_ALPHA else 1f)
            .clip(shape)
            .background(SportsSurfaceContainer)
            .border(1.dp, SportsOutlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // sport initial as the avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SportsPrimaryContainer)
            ) {
                Text(
                    text = game.sport.take(1).uppercase(Locale.getDefault()),
                    color = SportsOnPrimaryContainer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "$rank. ${game.title}", color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = game.whenText, color = SportsOnSurfaceVariant, fontSize = 14.sp)
                Text(text = game.locationName, color = SportsOnSurfaceVariant, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = game.distanceText, color = SportsPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (game.walkText != null) {
                    Text(text = game.walkText, color = SportsOnSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        StatusPills(game)
    }
}

// "3 spots left", "Request pending" and "Starts in 45 min" under a game card
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusPills(game: DiscoverGame) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(text = spotsLeftLabel(game.spotsLeft))
        if (game.requestPending) {
            Pill(text = "Request pending", textColor = SportsPrimary, borderColor = SportsPrimary)
        }
        if (game.startsInText != null && !game.isFull) {
            Pill(
                text = game.startsInText,
                background = SportsPrimaryContainer,
                textColor = SportsOnPrimaryContainer,
                borderColor = null
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    background: Color = Color.White,
    textColor: Color = SportsOnSurfaceVariant,
    borderColor: Color? = SportsOutlineVariant
) {
    Text(
        text = text,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.pillBackground(background, borderColor, horizontalPadding = 10.dp)
    )
}
