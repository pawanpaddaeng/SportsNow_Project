package com.example.cpen321application.ui.screens.games

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.game.spotsLeftLabel
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.components.LabelValueRow
import com.example.cpen321application.ui.components.LocationPreviewMap
import com.example.cpen321application.ui.components.SurfaceCard
import com.example.cpen321application.ui.components.cardBackground
import com.example.cpen321application.ui.components.pillBackground
import com.example.cpen321application.ui.theme.*

// the Game Details screen's sections, from the hero to the players

// player avatars shown before the "+N" bubble
private const val MAX_AVATARS = 5

// width of the labels in the When / Skill level card, so the values line up
private val INFO_LABEL_WIDTH = 96.dp

// sport and status pills, the title and who organizes it
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Hero(details: GameDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(text = details.sport, background = SportsPrimaryContainer, textColor = SportsOnPrimaryContainer)
            when {
                details.isCancelled ->
                    Pill(text = "Cancelled", background = MaterialTheme.colorScheme.error, textColor = Color.White)
                details.role == GameRole.Player ->
                    Pill(text = "You're in", background = SportsPrimary, textColor = Color.White, showCheck = true)
                details.role == GameRole.Viewer && !details.hasStarted -> Pill(
                    text = spotsLeftLabel(details.spotsLeft),
                    background = SportsSurfaceContainer,
                    textColor = SportsOnSurfaceVariant,
                    borderColor = SportsOutlineVariant
                )
            }
        }

        Text(text = details.title, color = SportsOnSurface, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)

        if (details.role == GameRole.Organizer) {
            Text(text = "Organized by you", color = SportsOnSurfaceVariant, fontSize = 14.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(text = details.organizer.initial, size = 24.dp, fontSize = 10)
                Text(
                    text = "Organized by ${details.organizer.fullName}",
                    color = SportsOnSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
internal fun InfoCard(details: GameDetails) {
    SurfaceCard {
        LabelValueRow(label = "When", value = details.whenText, labelWidth = INFO_LABEL_WIDTH)
        LabelValueRow(label = "Skill level", value = details.skillLevel, labelWidth = INFO_LABEL_WIDTH)
        // joined players are past the approval step
        if (details.role != GameRole.Player) {
            LabelValueRow(label = "Joining", value = "Organizer approves requests", labelWidth = INFO_LABEL_WIDTH)
        }
    }
}

// map preview with the place name, address and a Directions link
@Composable
internal fun LocationCard(location: GameLocation, onDirections: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, SportsOutlineVariant, shape)
    ) {
        LocationPreviewMap(location = location, onClick = onDirections, height = 140.dp)
        CardRow {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = location.name, color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = location.address, color = SportsOnSurfaceVariant, fontSize = 12.sp)
            }
            Text(
                text = "Directions",
                color = SportsPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDirections)
                    .padding(4.dp)
            )
        }
    }
}

// "3 pending join requests" card shown to the organizer
@Composable
internal fun JoinRequestsBanner(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .cardBackground(SportsPrimaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Avatar(
            text = count.toString(),
            size = 32.dp,
            fontSize = 13,
            background = MaterialTheme.colorScheme.error,
            textColor = Color.White,
            borderColor = null
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (count == 1) "1 pending join request" else "$count pending join requests",
                color = SportsOnPrimaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(text = "Review who wants to play", color = SportsOnPrimaryContainer, fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SportsOnPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

// "Players · 8/12" with overlapping initials and a "+N" bubble for the rest
@Composable
internal fun PlayersSection(details: GameDetails, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Players · ${details.playerCount}/${details.maxPlayers}",
                color = SportsOnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (details.players.isNotEmpty()) {
                Text(
                    text = "See all",
                    color = SportsPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSeeAll)
                        .padding(4.dp)
                )
            }
        }

        val shown = details.players.take(MAX_AVATARS)
        val hidden = details.players.size - shown.size
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            shown.forEach { player -> Avatar(text = player.initial, size = 40.dp, fontSize = 16) }
            if (hidden > 0) {
                Avatar(
                    text = "+$hidden",
                    size = 40.dp,
                    fontSize = 16,
                    background = SportsSurfaceContainer,
                    textColor = SportsOnSurfaceVariant
                )
            }
        }
    }
}

// round initial (or count) bubble, white ring so overlapping avatars stay apart
@Composable
internal fun Avatar(
    text: String,
    size: Dp,
    fontSize: Int,
    background: Color = SportsPrimaryContainer,
    textColor: Color = SportsOnPrimaryContainer,
    borderColor: Color? = if (size >= 40.dp) Color.White else null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(2.dp, borderColor, CircleShape) else Modifier)
    ) {
        Text(text = text, color = textColor, fontSize = fontSize.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Pill(
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
