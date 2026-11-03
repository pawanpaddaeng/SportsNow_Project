package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.game.model.GamePlayer
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.RetryMessage
import com.example.cpen321application.ui.components.ScrollingTabScreen
import com.example.cpen321application.ui.theme.*

// the Game Details actions that wait for the server, each with its own button and what's shown once it's done
enum class GameAction(val doneMessage: String) {
    Cancel("Game cancelled"),
    Complete("Game completed"),
    Leave("You left the game")
}

/*  what the Game Details screen shows. details is null until the game loads; isLoading
    shows a spinner and errorMessage a problem. busyAction shows progress on that
    action's button, removingPlayerId on that player's Remove button. selectedTab is
    highlighted in the bottom bar: Discover when the game was opened from there, otherwise Home
 */
data class GameDetailsUiState(
    val details: GameDetails?,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val busyAction: GameAction? = null,
    val removingPlayerId: Int? = null,
    val selectedTab: BottomTab = BottomTab.Home
)

/*  onRetry is null when retrying won't help. onGameAction (cancel or leave),
    onCompleteGame and onRemovePlayer are only called after the user confirms
 */
data class GameDetailsActions(
    val onBack: () -> Unit,
    val onRetry: (() -> Unit)? = null,
    val onEdit: () -> Unit = {},
    val onDirections: (GameLocation) -> Unit = {},
    val onJoinRequests: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onInviteFriends: () -> Unit = {},
    val onGameAction: (GameAction) -> Unit = {},
    val onCompleteGame: (noShowUserIds: List<Int>) -> Unit = {},
    val onRatePlayers: () -> Unit = {},
    val onRemovePlayer: (GamePlayer) -> Unit = {},
    val onOpenProfile: (Int) -> Unit = {},
    val onRequestToJoin: () -> Unit = {},
    val onTabSelected: (BottomTab) -> Unit = {}
)

/*
    Game Details screen from the Figma mockups, one screen for the three versions:
    the organizer sees Edit, pending join requests, Remove next to each player and Invite
    Friends until the game starts, and Cancel Game (Mark as Completed once it started,
    Leave Game once it's cancelled or completed), a joined player sees
    "You're in" and Leave Game, anyone else sees the spots left and Request to Join
 */
@Composable
fun GameDetailsScreen(state: GameDetailsUiState, actions: GameDetailsActions, modifier: Modifier = Modifier) {
    val details = state.details
    var showPlayers by remember { mutableStateOf(false) }
    // the action whose confirmation dialog is open
    var confirmAction by remember { mutableStateOf<GameAction?>(null) }
    // the player the organizer is asked to confirm removing
    var confirmRemove by remember { mutableStateOf<GamePlayer?>(null) }

    ScrollingTabScreen(selectedTab = state.selectedTab, onTabSelected = actions.onTabSelected, modifier = modifier) {
        // a cancelled, completed or started game can't be changed any more
        val canEdit = details?.role == GameRole.Organizer &&
            !details.isCancelled && !details.isCompleted && !details.hasStarted
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
                onGameAction = { action -> confirmAction = action }
            )
        }
    }

    if (showPlayers && details != null) {
        PlayersDialog(
            details = details,
            removingPlayerId = state.removingPlayerId,
            onRemove = { player -> confirmRemove = player },
            onOpenProfile = { player -> actions.onOpenProfile(player.userId) },
            onDismiss = { showPlayers = false }
        )
    }

    val playerToRemove = confirmRemove
    if (playerToRemove != null && details != null) {
        ConfirmRemovePlayerDialog(
            player = playerToRemove,
            gameTitle = details.title,
            onConfirm = {
                confirmRemove = null
                actions.onRemovePlayer(playerToRemove)
            },
            onDismiss = { confirmRemove = null }
        )
    }

    val pendingAction = confirmAction
    if (pendingAction == GameAction.Complete && details != null) {
        CompleteGameDialog(
            details = details,
            onConfirm = { noShowUserIds ->
                confirmAction = null
                actions.onCompleteGame(noShowUserIds)
            },
            onDismiss = { confirmAction = null }
        )
    } else if (pendingAction != null && details != null) {
        ConfirmActionDialog(
            action = pendingAction,
            details = details,
            onConfirm = {
                confirmAction = null
                actions.onGameAction(pendingAction)
            },
            onDismiss = { confirmAction = null }
        )
    }
}

/*  the loaded game's sections, from the hero to the action buttons. onGameAction asks
    the user to confirm the action first, onSeeAllPlayers opens the players dialog
 */
@Composable
private fun LoadedDetails(
    details: GameDetails,
    state: GameDetailsUiState,
    actions: GameDetailsActions,
    onSeeAllPlayers: () -> Unit,
    onGameAction: (GameAction) -> Unit
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

    // only the organizer answers requests, and only while the game is still open
    val isOrganizer = details.role == GameRole.Organizer
    val hasPendingRequests = details.pendingRequests > 0
    val isGameOpen = !details.isCancelled && !details.isCompleted
    val showJoinRequestsBanner = isOrganizer && hasPendingRequests && isGameOpen
    if (showJoinRequestsBanner) {
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
        busyAction = state.busyAction,
        onOpenChat = actions.onOpenChat,
        onInviteFriends = actions.onInviteFriends,
        onGameAction = onGameAction,
        onRatePlayers = actions.onRatePlayers,
        onRequestToJoin = actions.onRequestToJoin
    )
}

// a spinner while the game loads, or the problem with Retry when onRetry is given
@Composable
internal fun LoadingOrError(isLoading: Boolean, errorMessage: String?, onRetry: (() -> Unit)?) {
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
