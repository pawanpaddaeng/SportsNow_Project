package com.example.cpen321application.navigation.routes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.core.notifications.GameEvents
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.game.model.GamePlayer
import com.example.cpen321application.game.model.toGameDetails
import com.example.cpen321application.game.repository.GameChangeResult
import com.example.cpen321application.game.repository.GameDetailsResult
import com.example.cpen321application.game.repository.JoinRequestResult
import com.example.cpen321application.game.repository.cancelGame
import com.example.cpen321application.game.repository.completeGame
import com.example.cpen321application.game.repository.fetchGameDetails
import com.example.cpen321application.game.repository.leaveGame
import com.example.cpen321application.game.repository.removePlayer
import com.example.cpen321application.game.repository.sendJoinRequest
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.screens.games.GameAction
import com.example.cpen321application.ui.screens.games.GameDetailsActions
import com.example.cpen321application.ui.screens.games.GameDetailsScreen
import com.example.cpen321application.ui.screens.games.GameDetailsUiState
import com.example.cpen321application.ui.screens.games.JoinRequestSheet
import kotlinx.coroutines.launch

// the game id of a route like game_details/{gameId}
internal fun NavBackStackEntry.gameId(): Int = arguments?.getInt(Routes.GAME_ID_ARG) ?: 0

@Composable
fun GameDetailsRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val navController = app.navController
    val gameId = backStackEntry.gameId()
    var details by remember { mutableStateOf<GameDetails?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var busyAction by remember { mutableStateOf<GameAction?>(null) }
    var removingPlayerId by remember { mutableStateOf<Int?>(null) }
    var showJoinSheet by remember { mutableStateOf(false) }
    // opened from a Discover card: highlight Discover and go back there after a join request
    val openedFromDiscover = remember {
        navController.previousBackStackEntry?.destination?.route == Routes.DISCOVER
    }

    // on failure the last loaded details stay visible with the error above them
    fun loadDetails() {
        if (isLoading) return
        isLoading = true
        app.scope.launch {
            try {
                when (val result = fetchGameDetails(context, gameId)) {
                    is GameDetailsResult.Loaded -> {
                        details = result.details.toGameDetails(result.game)
                        error = null
                        notFound = false
                    }
                    GameDetailsResult.NotFound -> {
                        details = null
                        error = "This game no longer exists."
                        notFound = true
                    }
                    is GameDetailsResult.Failed -> error = result.message
                }
            } finally {
                isLoading = false
            }
        }
    }

    /*  sends an action the user confirmed on the screen, with progress on its button, then
        says it worked or shows the server's problem. After cancelling or completing the
        game reloads; after leaving the user is no longer in it, so go back (Home and
        Discover reload when shown)
     */
    fun runAction(action: GameAction) {
        busyAction = action
        app.scope.launch {
            val result = try {
                when (action) {
                    GameAction.Cancel -> cancelGame(context, gameId)
                    GameAction.Complete -> completeGame(context, gameId)
                    GameAction.Leave -> leaveGame(context, gameId)
                }
            } finally {
                busyAction = null
            }
            when (result) {
                is GameChangeResult.Done -> {
                    val doneMessage = when (action) {
                        GameAction.Cancel -> "Game cancelled"
                        GameAction.Complete -> "Game completed"
                        GameAction.Leave -> "You left the game"
                    }
                    Toast.makeText(context, doneMessage, Toast.LENGTH_SHORT).show()
                    if (action == GameAction.Leave) navController.popBackStack() else loadDetails()
                }
                is GameChangeResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // the organizer confirmed removing this player, the game reloads to show who's left
    fun removeFromGame(player: GamePlayer) {
        removingPlayerId = player.userId
        app.scope.launch {
            val result = try {
                removePlayer(context, gameId, player.userId)
            } finally {
                removingPlayerId = null
            }
            when (result) {
                is GameChangeResult.Done -> {
                    Toast.makeText(context, "${player.fullName} was removed", Toast.LENGTH_SHORT).show()
                    loadDetails()
                }
                is GameChangeResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // reloads whenever the screen is shown, e.g. coming back after editing the game
    OnResume(backStackEntry) { loadDetails() }

    // a notification about this game arrived (new request, approval, edit, ...)
    LaunchedEffect(gameId) {
        GameEvents.events.collect { event -> if (event.gameId == gameId) loadDetails() }
    }

    GameDetailsScreen(
        state = GameDetailsUiState(
            details = details,
            isLoading = isLoading,
            errorMessage = error,
            busyAction = busyAction,
            removingPlayerId = removingPlayerId,
            selectedTab = if (openedFromDiscover) BottomTab.Discover else BottomTab.Home
        ),
        actions = GameDetailsActions(
            onBack = { navController.popBackStack() },
            onRetry = if (notFound) null else ({ loadDetails() }),
            onEdit = { navController.navigate(Routes.editGame(gameId)) },
            onDirections = { location -> openDirections(context, location) },
            onJoinRequests = { navController.navigate(Routes.joinRequests(gameId)) },
            onGameAction = { action -> runAction(action) },
            onRemovePlayer = { player -> removeFromGame(player) },
            onRequestToJoin = { showJoinSheet = true },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )

    val shownDetails = details
    if (showJoinSheet && shownDetails != null) {
        JoinRequestPopup(
            app = app,
            details = shownDetails,
            onDismiss = { showJoinSheet = false },
            onSent = {
                showJoinSheet = false
                // Discover reloads when shown, so the card says "Request pending"
                if (openedFromDiscover) navController.popBackStack() else loadDetails()
            }
        )
    }
}

// sends the answers to the organizer, the server's problem is shown in the popup
@Composable
private fun JoinRequestPopup(app: AppState, details: GameDetails, onDismiss: () -> Unit, onSent: () -> Unit) {
    val context = LocalContext.current
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    JoinRequestSheet(
        details = details,
        isSending = isSending,
        errorMessage = error,
        onDismiss = onDismiss,
        onSend = { answers ->
            isSending = true
            error = null
            app.scope.launch {
                val result = try {
                    sendJoinRequest(
                        context = context,
                        gameId = details.gameId,
                        skillLevel = answers.skillLevel,
                        playFrequency = answers.playFrequency,
                        message = answers.message
                    )
                } finally {
                    isSending = false
                }
                when (result) {
                    is JoinRequestResult.Sent -> {
                        Toast.makeText(context, "Request sent", Toast.LENGTH_SHORT).show()
                        onSent()
                    }
                    is JoinRequestResult.Failed -> error = result.message
                }
            }
        }
    )
}

// opens directions in Google Maps, or the browser if Maps isn't installed
private fun openDirections(context: Context, location: GameLocation) {
    val uri =
        "https://www.google.com/maps/dir/?api=1&destination=${location.latitude},${location.longitude}".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to show directions", Toast.LENGTH_SHORT).show()
    }
}
