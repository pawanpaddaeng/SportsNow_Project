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
import com.example.cpen321application.network.GameDetailsResponse
import com.example.cpen321application.network.GameDto
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// player avatars shown before the "+N" bubble
private const val MAX_AVATARS = 5

// the signed in user's relation to the game, decides which actions are shown
enum class GameRole { Organizer, Player, Viewer }

data class GamePlayer(val firstName: String, val lastName: String) {
    val fullName get() = "$firstName $lastName".trim()
    val initial get() = firstName.take(1).uppercase()
}

/*  what the Game Details screen shows, built from the server's reply with toGameDetails.
    pendingRequests is only used for the organizer, joinRequestStatus only for viewers
    ("pending", "approved", "declined", "cancelled" or null if they never asked)
 */
data class GameDetails(
    val gameId: Int,
    val role: GameRole,
    val sport: String,
    val title: String,
    val organizer: GamePlayer,
    val whenText: String,
    val skillLevel: String,
    val location: GameLocation,
    val description: String?,
    val players: List<GamePlayer>,
    val playerCount: Int,
    val maxPlayers: Int,
    val pendingRequests: Int,
    val joinRequestStatus: String?,
    val isCancelled: Boolean,
    val hasStarted: Boolean
) {
    val spotsLeft get() = (maxPlayers - playerCount).coerceAtLeast(0)
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val hourFormat = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
private val hourAmPmFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val amPmFormat = DateTimeFormatter.ofPattern("a", Locale.getDefault())

/*  "Fri, Oct 17 · 7:00 – 9:00 PM", "Sun, Oct 19 · 10:00 AM – 12:00 PM" or just the start
    when there is no end time, in the phone's time zone
 */
internal fun formatGameTime(start: ZonedDateTime, end: ZonedDateTime?): String {
    val startText = "${start.format(dayFormat)} · "
    return when {
        end == null -> startText + start.format(hourAmPmFormat)
        end.toLocalDate() != start.toLocalDate() ->
            "$startText${start.format(hourAmPmFormat)} – ${end.format(dayFormat)} · ${end.format(hourAmPmFormat)}"
        // AM/PM is only repeated when it changes
        start.format(amPmFormat) == end.format(amPmFormat) ->
            "$startText${start.format(hourFormat)} – ${end.format(hourAmPmFormat)}"
        else -> "$startText${start.format(hourAmPmFormat)} – ${end.format(hourAmPmFormat)}"
    }
}

private fun String.capitalized() = replaceFirstChar { it.titlecase(Locale.getDefault()) }

// turns the server's reply into what the screen shows
fun GameDetailsResponse.toGameDetails(game: GameDto): GameDetails {
    val zone = ZoneId.systemDefault()
    val start = try {
        Instant.parse(game.startsAt).atZone(zone)
    } catch (e: DateTimeParseException) {
        null
    }
    val end = try {
        game.endsAt?.let { Instant.parse(it).atZone(zone) }
    } catch (e: DateTimeParseException) {
        null
    }
    val players = players.orEmpty().map { GamePlayer(it.firstName, it.lastName) }

    return GameDetails(
        gameId = game.gameId,
        role = when (viewerRole) {
            "organizer" -> GameRole.Organizer
            "player" -> GameRole.Player
            else -> GameRole.Viewer
        },
        sport = game.sport.capitalized(),
        title = game.title,
        organizer = organizer?.let { GamePlayer(it.firstName, it.lastName) } ?: GamePlayer("Unknown", ""),
        whenText = start?.let { formatGameTime(it, end) } ?: game.startsAt,
        skillLevel = game.skillLevel.capitalized(),
        location = GameLocation(game.locationName, game.address, game.latitude, game.longitude),
        description = game.description?.takeIf { it.isNotBlank() },
        players = players,
        playerCount = game.playerCount,
        maxPlayers = game.maxPlayers,
        pendingRequests = pendingRequests ?: 0,
        joinRequestStatus = joinRequestStatus,
        isCancelled = game.status == "cancelled",
        hasStarted = start?.toInstant()?.isBefore(Instant.now()) ?: false
    )
}

/*
    Game Details screen from the Figma mockups, one screen for the three versions:
    the organizer sees Edit, pending join requests and Cancel Game, a joined player
    sees "You're in" and Leave Game, anyone else sees the spots left and Request to Join.
    details is null until the game loads; isLoading shows a spinner and errorMessage
    a problem (with Retry when onRetry is given). onCancelGame is only called after the
    organizer confirms, isCancelling shows progress on the button while it runs.
    The callbacks connect it to other screens through AppNavigation
 */
@Composable
fun GameDetailsScreen(
    details: GameDetails?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isCancelling: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onEdit: () -> Unit = {},
    onDirections: (GameLocation) -> Unit = {},
    onJoinRequests: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onCancelGame: () -> Unit = {},
    onLeaveGame: () -> Unit = {},
    onRequestToJoin: () -> Unit = {},
    // highlighted in the bottom bar: Discover when opened from there, otherwise Home
    selectedTab: BottomTab = BottomTab.Home,
    onTabSelected: (BottomTab) -> Unit = {}
) {
    var showPlayers by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(selectedTab = selectedTab, onTabSelected = onTabSelected)
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
            TopBar(
                // a cancelled or started game can't be changed any more
                showEdit = details?.role == GameRole.Organizer && !details.isCancelled && !details.hasStarted,
                onBack = onBack,
                onEdit = onEdit
            )

            if (details == null) {
                LoadingOrError(isLoading = isLoading, errorMessage = errorMessage, onRetry = onRetry)
            } else {
                // a refresh failed, the last loaded details stay visible
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Hero(details)
                InfoCard(details)
                LocationCard(location = details.location, onDirections = { onDirections(details.location) })

                if (details.role == GameRole.Organizer && details.pendingRequests > 0 && !details.isCancelled) {
                    JoinRequestsBanner(count = details.pendingRequests, onClick = onJoinRequests)
                }

                PlayersSection(details = details, onSeeAll = { showPlayers = true })

                if (details.description != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "About", color = SportsOnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = details.description, color = SportsOnSurfaceVariant, fontSize = 14.sp)
                    }
                }

                Actions(
                    details = details,
                    isCancelling = isCancelling,
                    onOpenChat = onOpenChat,
                    onCancelGame = { confirmCancel = true },
                    onLeaveGame = onLeaveGame,
                    onRequestToJoin = onRequestToJoin
                )
            }
        }
    }

    if (showPlayers && details != null) {
        PlayersDialog(details = details, onDismiss = { showPlayers = false })
    }

    if (confirmCancel && details != null) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this game?") },
            text = {
                Text("${details.title} will be cancelled for everyone. Players and anyone who asked to join will be notified. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onCancelGame()
                }) {
                    Text("Cancel Game", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep Game") }
            }
        )
    }
}

@Composable
private fun TopBar(showEdit: Boolean, onBack: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SportsOnSurface
            )
        }
        Text(
            text = "Game Details",
            color = SportsOnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (showEdit) {
            TextButton(onClick = onEdit) {
                Text(text = "Edit", color = SportsPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun LoadingOrError(isLoading: Boolean, errorMessage: String?, onRetry: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = SportsPrimary)
            errorMessage != null -> {
                Text(
                    text = errorMessage,
                    color = SportsOnSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                if (onRetry != null) {
                    OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(50)) {
                        Text(text = "Retry", color = SportsPrimary)
                    }
                }
            }
        }
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
                    text = when (details.spotsLeft) {
                        0 -> "Full"
                        1 -> "1 spot left"
                        else -> "${details.spotsLeft} spots left"
                    },
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
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsSurfaceContainer)
            .padding(16.dp)
    ) {
        InfoRow(label = "When", value = details.whenText)
        InfoRow(label = "Skill level", value = details.skillLevel)
        // joined players are past the approval step
        if (details.role != GameRole.Player) {
            InfoRow(label = "Joining", value = "Organizer approves requests")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = label, color = SportsOnSurfaceVariant, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(
            text = value,
            color = SportsOnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
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
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsPrimaryContainer)
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
private fun Avatar(
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
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(shape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (showCheck) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
        }
        Text(text = text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// everyone in the game, opened from "See all"
@Composable
private fun PlayersDialog(details: GameDetails, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Players · ${details.playerCount}/${details.maxPlayers}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                details.players.forEach { player ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(text = player.initial, size = 32.dp, fontSize = 13)
                        Text(text = player.fullName, color = SportsOnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (player == details.organizer) {
                            Text(text = "Organizer", color = SportsOnSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    )
}
