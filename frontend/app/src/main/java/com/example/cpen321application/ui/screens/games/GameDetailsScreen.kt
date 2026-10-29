package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.RetryMessage
import com.example.cpen321application.ui.theme.*

/*  what the Game Details screen shows. details is null until the game loads; isLoading
    shows a spinner and errorMessage a problem. isCancelling shows progress on the Cancel
    Game button. selectedTab is highlighted in the bottom bar: Discover when the game was
    opened from there, otherwise Home
 */
data class GameDetailsUiState(
    val details: GameDetails?,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCancelling: Boolean = false,
    val selectedTab: BottomTab = BottomTab.Home
)

/*  onRetry is null when retrying won't help. onCancelGame is only called after the
    organizer confirms
 */
data class GameDetailsActions(
    val onBack: () -> Unit,
    val onRetry: (() -> Unit)? = null,
    val onEdit: () -> Unit = {},
    val onDirections: (GameLocation) -> Unit = {},
    val onJoinRequests: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onCancelGame: () -> Unit = {},
    val onLeaveGame: () -> Unit = {},
    val onRequestToJoin: () -> Unit = {},
    val onTabSelected: (BottomTab) -> Unit = {}
)

/*
    Game Details screen from the Figma mockups, one screen for the three versions:
    the organizer sees Edit, pending join requests and Cancel Game, a joined player
    sees "You're in" and Leave Game, anyone else sees the spots left and Request to Join
 */
@Composable
fun GameDetailsScreen(state: GameDetailsUiState, actions: GameDetailsActions, modifier: Modifier = Modifier) {
    val details = state.details
    var showPlayers by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(selectedTab = state.selectedTab, onTabSelected = actions.onTabSelected)
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
            // a cancelled or started game can't be changed any more
            val canEdit = details?.role == GameRole.Organizer && !details.isCancelled && !details.hasStarted
            BackTitleBar(title = "Game Details", onBack = actions.onBack) {
                if (canEdit) {
                    TextButton(onClick = actions.onEdit) {
                        Text(text = "Edit", color = SportsPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (details == null) {
                LoadingOrError(isLoading = state.isLoading, errorMessage = state.errorMessage, onRetry = actions.onRetry)
            } else {
                LoadedDetails(
                    details = details,
                    state = state,
                    actions = actions,
                    onSeeAllPlayers = { showPlayers = true },
                    onCancelGame = { confirmCancel = true }
                )
            }
        }
    }

    if (showPlayers && details != null) {
        PlayersDialog(details = details, onDismiss = { showPlayers = false })
    }

    if (confirmCancel && details != null) {
        ConfirmCancelDialog(
            title = details.title,
            onConfirm = {
                confirmCancel = false
                actions.onCancelGame()
            },
            onDismiss = { confirmCancel = false }
        )
    }
}

/*  the loaded game's sections, from the hero to the action buttons. onCancelGame asks
    the organizer to confirm first, onSeeAllPlayers opens the players dialog
 */
@Composable
private fun LoadedDetails(
    details: GameDetails,
    state: GameDetailsUiState,
    actions: GameDetailsActions,
    onSeeAllPlayers: () -> Unit,
    onCancelGame: () -> Unit
) {
    // a refresh failed, the last loaded details stay visible
    if (state.errorMessage != null) {
        Text(
            text = state.errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Hero(details)
    InfoCard(details)
    LocationCard(location = details.location, onDirections = { actions.onDirections(details.location) })

    if (details.role == GameRole.Organizer && details.pendingRequests > 0 && !details.isCancelled) {
        JoinRequestsBanner(count = details.pendingRequests, onClick = actions.onJoinRequests)
    }

    PlayersSection(details = details, onSeeAll = onSeeAllPlayers)

    if (details.description != null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "About", color = SportsOnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(text = details.description, color = SportsOnSurfaceVariant, fontSize = 14.sp)
        }
    }

    ActionButtons(
        details = details,
        isCancelling = state.isCancelling,
        onOpenChat = actions.onOpenChat,
        onCancelGame = onCancelGame,
        onLeaveGame = actions.onLeaveGame,
        onRequestToJoin = actions.onRequestToJoin
    )
}

// a spinner while the game loads, or the problem with Retry when onRetry is given
@Composable
private fun LoadingOrError(isLoading: Boolean, errorMessage: String?, onRetry: (() -> Unit)?) {
    when {
        isLoading -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp)
        ) {
            CircularProgressIndicator(color = SportsPrimary)
        }
        errorMessage != null -> RetryMessage(message = errorMessage, onRetry = onRetry, modifier = Modifier.padding(top = 48.dp))
    }
}
