package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.review.model.PlayerToRate
import com.example.cpen321application.review.model.PlayersToRate
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.SurfaceCard
import com.example.cpen321application.ui.components.cardBackground
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.example.cpen321application.ui.theme.SportsSurfaceContainer

// what the Rate Players screen shows. players is null until the game loads
data class RatePlayersUiState(
    val players: PlayersToRate?,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// what the Rate Players screen's controls do, onRetry is null when retrying won't help
data class RatePlayersActions(
    val onBack: () -> Unit,
    val onRetry: (() -> Unit)? = null,
    val onPlayerClick: (PlayerToRate) -> Unit = {},
    val onTabSelected: (BottomTab) -> Unit = {}
)

// the other players who showed up to a completed game, rating each of them is optional
@Composable
fun RatePlayersScreen(state: RatePlayersUiState, actions: RatePlayersActions, modifier: Modifier = Modifier) {
    val loaded = state.players
    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(selectedTab = BottomTab.Home, onTabSelected = actions.onTabSelected)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BackTitleBar(title = "Rate Players", onBack = actions.onBack)

            if (loaded == null) {
                LoadingOrError(isLoading = state.isLoading, errorMessage = state.errorMessage, onRetry = actions.onRetry)
            } else {
                // a refresh failed, the last loaded players stay visible
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(text = loaded.gameTitle, color = SportsOnSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                RatingOptionalNote()
                if (loaded.players.isEmpty()) {
                    Text(text = "No other players showed up to this game.", color = SportsOnSurfaceVariant, fontSize = 14.sp)
                }
                loaded.players.forEach { player ->
                    PlayerRow(player = player, onClick = { actions.onPlayerClick(player) })
                }
            }
        }
    }
}

@Composable
private fun RatingOptionalNote() {
    SurfaceCard {
        Text(text = "Rating is optional", color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            text = "You can rate as many or as few players as you like, and skip anyone you don't want to rate. " +
                "Players only see their overall score, never individual ratings.",
            color = SportsOnSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

// a player to tap and rate, a rating can't be changed so a rated player can't be tapped
@Composable
private fun PlayerRow(player: PlayerToRate, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .cardBackground(SportsSurfaceContainer)
            .clickable(enabled = !player.isRated, onClick = onClick)
            .padding(16.dp)
    ) {
        Avatar(text = player.initial, size = 32.dp, fontSize = 13)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = player.name, color = SportsOnSurface, fontSize = 16.sp)
            if (player.isOrganizer) {
                Text(text = "Organizer", color = SportsOnSurfaceVariant, fontSize = 12.sp)
            }
        }
        if (player.isRated) {
            Text(text = "Rated", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SportsOnSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
