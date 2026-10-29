package com.example.cpen321application.game.model

import com.example.cpen321application.game.dto.DiscoverGameDto
import com.example.cpen321application.game.formatDistance
import com.example.cpen321application.game.formatStartTime
import com.example.cpen321application.game.formatStartsIn
import com.example.cpen321application.game.parseInstant
import java.time.Instant
import kotlin.math.ceil

// average walking speed, used for "3 min walk"
private const val WALKING_METRES_PER_MINUTE = 80.0
// further than this a walking time isn't useful, only the distance is shown
private const val MAX_WALK_METRES = 3000

// a Discover card. sport is the server's value (e.g. "basketball"), used by the sport filter
data class DiscoverGame(
    val gameId: Int,
    val sport: String,
    val title: String,
    val whenText: String,
    val locationName: String,
    val distanceText: String,
    val walkText: String?,
    val spotsLeft: Int,
    val startsInText: String?,
    val requestPending: Boolean,
    val latitude: Double,
    val longitude: Double
) {
    val isFull get() = spotsLeft == 0
}

fun DiscoverGameDto.toDiscoverGame(now: Instant = Instant.now()): DiscoverGame {
    val start = parseInstant(game.startsAt)
    return DiscoverGame(
        gameId = game.gameId,
        sport = game.sport,
        title = game.title,
        whenText = start?.let { formatStartTime(it, now) } ?: game.startsAt,
        locationName = game.locationName,
        distanceText = formatDistance(distanceMeters),
        walkText = if (distanceMeters <= MAX_WALK_METRES) {
            "${ceil(distanceMeters / WALKING_METRES_PER_MINUTE).toInt().coerceAtLeast(1)} min walk"
        } else {
            null
        },
        spotsLeft = (game.maxPlayers - game.playerCount).coerceAtLeast(0),
        startsInText = start?.let { formatStartsIn(it, now) },
        requestPending = joinRequestStatus == "pending",
        latitude = game.latitude,
        longitude = game.longitude
    )
}
