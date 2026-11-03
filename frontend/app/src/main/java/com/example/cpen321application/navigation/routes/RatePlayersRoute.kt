package com.example.cpen321application.navigation.routes

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.review.model.PlayerToRate
import com.example.cpen321application.review.model.PlayersToRate
import com.example.cpen321application.review.model.RatingCategory
import com.example.cpen321application.review.repository.PlayersToRateResult
import com.example.cpen321application.review.repository.SubmitRatingResult
import com.example.cpen321application.review.repository.fetchPlayersToRate
import com.example.cpen321application.review.repository.submitRating
import com.example.cpen321application.ui.screens.games.RatePlayersActions
import com.example.cpen321application.ui.screens.games.RatePlayersScreen
import com.example.cpen321application.ui.screens.games.RatePlayersUiState
import com.example.cpen321application.ui.screens.games.RatingSheet
import kotlinx.coroutines.launch

// the players of a completed game who showed up and the form to rate one, opened from Game Details
@Composable
fun RatePlayersRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val gameId = backStackEntry.gameId()
    var players by remember { mutableStateOf<PlayersToRate?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // the game is gone or the user can't rate in it, retrying won't help
    var unavailable by remember { mutableStateOf(false) }
    var ratingPlayer by remember { mutableStateOf<PlayerToRate?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var ratingError by remember { mutableStateOf<String?>(null) }

    // on failure the last loaded players stay visible with the error above them
    fun loadPlayers() {
        if (isLoading) return
        isLoading = true
        app.scope.launch {
            try {
                when (val result = fetchPlayersToRate(context, gameId)) {
                    is PlayersToRateResult.Loaded -> {
                        players = result.players
                        error = null
                    }
                    is PlayersToRateResult.Unavailable -> {
                        players = null
                        error = result.message
                        unavailable = true
                    }
                    is PlayersToRateResult.Failed -> error = result.message
                }
            } finally {
                isLoading = false
            }
        }
    }

    // the server's problem is shown in the form. The list reloads either way, the player may already be rated
    fun sendRating(player: PlayerToRate, scores: Map<RatingCategory, Int>) {
        isSending = true
        ratingError = null
        app.scope.launch {
            val result = try {
                submitRating(context, gameId, player.userId, scores)
            } finally {
                isSending = false
            }
            when (result) {
                SubmitRatingResult.Submitted -> {
                    ratingPlayer = null
                    Toast.makeText(context, "Rating submitted", Toast.LENGTH_SHORT).show()
                }
                is SubmitRatingResult.Failed -> ratingError = result.message
            }
            loadPlayers()
        }
    }

    OnResume(backStackEntry) { loadPlayers() }

    RatePlayersScreen(
        state = RatePlayersUiState(players = players, isLoading = isLoading, errorMessage = error),
        actions = RatePlayersActions(
            onBack = { app.navController.popBackStack() },
            onRetry = if (unavailable) null else ({ loadPlayers() }),
            onPlayerClick = { player ->
                ratingError = null
                ratingPlayer = player
            },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )

    ratingPlayer?.let { player ->
        RatingSheet(
            player = player,
            isSending = isSending,
            errorMessage = ratingError,
            onDismiss = { ratingPlayer = null },
            onSubmit = { scores -> sendRating(player, scores) }
        )
    }
}
