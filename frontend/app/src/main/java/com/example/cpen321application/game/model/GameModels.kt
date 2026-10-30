package com.example.cpen321application.game.model

import com.example.cpen321application.game.capitalized
import com.example.cpen321application.game.dto.GameDetailsResponse
import com.example.cpen321application.game.dto.GameDto
import com.example.cpen321application.game.formatDayTime
import com.example.cpen321application.game.formatGameTime
import com.example.cpen321application.game.parseZoned
import java.io.Serializable
import java.time.Instant

// the signed in user's relation to the game, decides which actions are shown
enum class GameRole { Organizer, Player, Viewer }

/*  where a game is at, shown as one tag by Home and Game Details. InProgress has started
    but the organizer hasn't marked it completed yet
 */
enum class GameState { Upcoming, InProgress, Completed, Cancelled }

/*  the game's state from its server status ("scheduled", "cancelled" or "completed") and
    the server's has_started. The app doesn't compare times itself, so it always agrees
    with what the server allows
 */
fun gameState(status: String, hasStarted: Boolean): GameState = when {
    status == "cancelled" -> GameState.Cancelled
    status == "completed" -> GameState.Completed
    hasStarted -> GameState.InProgress
    else -> GameState.Upcoming
}

// userId lets the organizer remove the player
data class GamePlayer(val userId: Int, val firstName: String, val lastName: String) {
    val fullName get() = "$firstName $lastName".trim()
    val initial get() = firstName.take(1).uppercase()
}

// Serializable so it can be passed between screens through the navigation SavedStateHandle
data class GameLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
) : Serializable

/*  pendingRequests is only used for the organizer, joinRequestStatus only for viewers
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
    val state: GameState
) {
    val spotsLeft get() = (maxPlayers - playerCount).coerceAtLeast(0)
    val isCancelled get() = state == GameState.Cancelled
    val isCompleted get() = state == GameState.Completed
    // a cancelled game counts as not started, it can't be played any more
    val hasStarted get() = state == GameState.InProgress || state == GameState.Completed
}

/*  the Input Game Info form. sport and skillLevel use the server's values
    (e.g. "basketball", "any"), startsAt is the start time in UTC.
    Serializable so the edit route can keep the loaded game with rememberSaveable
 */
data class GameInfoForm(
    val sport: String,
    val title: String,
    val startsAt: Instant,
    val location: GameLocation,
    val maxPlayers: Int,
    val skillLevel: String,
    val description: String?
) : Serializable

// a game card on Home, pendingRequests is only used for games the user organizes, every card shows its state
data class GameSummary(
    val id: String,
    val sport: String,
    val title: String,
    val dateTime: String,
    val location: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val pendingRequests: Int = 0,
    val state: GameState = GameState.Upcoming
)

fun GameDto.toLocation() = GameLocation(locationName, address, latitude, longitude)

fun GameDetailsResponse.toGameDetails(game: GameDto): GameDetails {
    val start = parseZoned(game.startsAt)
    val end = game.endsAt?.let { parseZoned(it) }

    return GameDetails(
        gameId = game.gameId,
        role = when (viewerRole) {
            "organizer" -> GameRole.Organizer
            "player" -> GameRole.Player
            else -> GameRole.Viewer
        },
        sport = game.sport.capitalized(),
        title = game.title,
        organizer = organizer?.let { GamePlayer(it.userId, it.firstName, it.lastName) }
            ?: GamePlayer(game.organizerId, "Unknown", ""),
        whenText = start?.let { formatGameTime(it, end) } ?: game.startsAt,
        skillLevel = game.skillLevel.capitalized(),
        location = game.toLocation(),
        description = game.description?.takeIf { it.isNotBlank() },
        players = players.orEmpty().map { GamePlayer(it.userId, it.firstName, it.lastName) },
        playerCount = game.playerCount,
        maxPlayers = game.maxPlayers,
        pendingRequests = pendingRequests ?: 0,
        joinRequestStatus = joinRequestStatus,
        state = gameState(game.status, game.hasStarted)
    )
}

fun GameDto.toGameInfoForm() = GameInfoForm(
    sport = sport,
    title = title,
    startsAt = Instant.parse(startsAt),
    location = toLocation(),
    maxPlayers = maxPlayers,
    skillLevel = skillLevel,
    description = description?.takeIf { it.isNotBlank() }
)

fun GameDto.toGameSummary() = GameSummary(
    id = gameId.toString(),
    sport = sport.capitalized(),
    title = title,
    dateTime = formatDayTime(startsAt),
    location = locationName,
    playerCount = playerCount,
    maxPlayers = maxPlayers,
    pendingRequests = pendingRequests ?: 0,
    state = gameState(status, hasStarted)
)
