package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.GameDetails
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.game.model.GameRole
import com.example.cpen321application.game.spotsLeftLabel
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.components.LabelValueRow
import com.example.cpen321application.ui.components.RetryMessage
import com.example.cpen321application.ui.components.SurfaceCard
import com.example.cpen321application.ui.components.cardBackground
import com.example.cpen321application.ui.components.pillBackground
import com.example.cpen321application.ui.theme.*

// player avatars shown before the "+N" bubble
private const val MAX_AVATARS = 5

// width of the labels in the When / Skill level card, so the values line up
private val INFO_LABEL_WIDTH = 96.dp

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

/*  what the Game Details screen's controls do. onRetry is null when retrying won't help.
    onCancelGame is only called after the organizer confirms
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
    sees "You're in" and Leave Game, anyone else sees the spots left and Request to Join.
    The actions connect it to other screens through AppNavigation
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

    Actions(
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

// sport and status pills, the title and who organizes it
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(details: GameDetails) {
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
private fun InfoCard(details: GameDetails) {
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
private fun LocationCard(location: GameLocation, onDirections: () -> Unit) {
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
private fun JoinRequestsBanner(count: Int, onClick: () -> Unit) {
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
private fun PlayersSection(details: GameDetails, onSeeAll: () -> Unit) {
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

// buttons at the bottom, depending on the user's role and whether the game can still change
@Composable
private fun Actions(
    details: GameDetails,
    isCancelling: Boolean,
    onOpenChat: () -> Unit,
    onCancelGame: () -> Unit,
    onLeaveGame: () -> Unit,
    onRequestToJoin: () -> Unit
) {
    if (details.isCancelled || details.hasStarted) {
        Text(
            text = if (details.isCancelled) "This game was cancelled." else "This game has already started.",
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (details.role == GameRole.Viewer) return
    }

    when (details.role) {
        GameRole.Organizer, GameRole.Player -> {
            PrimaryButton(text = "Open Chat", onClick = onOpenChat)
            if (!details.isCancelled && !details.hasStarted) {
                if (details.role == GameRole.Organizer) {
                    DangerButton(text = "Cancel Game", onClick = onCancelGame, isBusy = isCancelling)
                } else {
                    DangerButton(text = "Leave Game", onClick = onLeaveGame)
                }
            }
        }
        GameRole.Viewer -> {
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
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// outlined button with red text for leaving or cancelling, isBusy shows a spinner instead
@Composable
private fun DangerButton(text: String, onClick: () -> Unit, isBusy: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isBusy,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, SportsOutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.error,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
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

