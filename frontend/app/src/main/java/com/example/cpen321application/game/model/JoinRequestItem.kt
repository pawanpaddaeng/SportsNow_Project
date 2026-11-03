package com.example.cpen321application.game.model

import com.example.cpen321application.game.capitalized
import com.example.cpen321application.game.dto.GameDto
import com.example.cpen321application.game.dto.PendingJoinRequestDto
import com.example.cpen321application.game.formatDay
import com.example.cpen321application.game.formatRequestedAgo
import com.example.cpen321application.game.parseInstant
import com.example.cpen321application.game.spotsLeftLabel
import com.example.cpen321application.review.model.formatReputationScore
import java.time.Instant
import java.util.Locale

// how the popup's "How often do you play?" answers read on a request card
private val FREQUENCY_TEXT = mapOf(
    "rarely" to "Plays rarely",
    "monthly" to "Plays monthly",
    "weekly" to "Plays weekly",
    "several_per_week" to "Plays several times a week"
)

// the game at the top of Join Requests: "Volleyball Night" and "Fri, Oct 17 · 8/12 players · 4 spots left"
data class JoinRequestsHeader(val title: String, val subtitle: String, val isFull: Boolean)

// a request card: who asked, "Rating 4.7/5 · Intermediate · Plays weekly", "Requested 2h ago" and their message
data class JoinRequestItem(
    val requestId: Int,
    val userId: Int,
    val name: String,
    val initial: String,
    val details: String,
    val requested: String?,
    val message: String?
)

fun GameDto.toJoinRequestsHeader(): JoinRequestsHeader {
    val spotsLeft = (maxPlayers - playerCount).coerceAtLeast(0)
    return JoinRequestsHeader(
        title = title,
        subtitle = "${formatDay(startsAt)} · $playerCount/$maxPlayers players · ${spotsLeftLabel(spotsLeft).lowercase()}",
        isFull = spotsLeft == 0
    )
}

fun PendingJoinRequestDto.toJoinRequestItem(now: Instant = Instant.now()): JoinRequestItem {
    val details = listOfNotNull(
        "Rating ${formatReputationScore(reputationScore)}",
        skillLevel.capitalized(),
        playFrequency?.let { FREQUENCY_TEXT[it] }
    ).joinToString(" · ")
    return JoinRequestItem(
        requestId = requestId,
        userId = user.userId,
        name = "${user.firstName} ${user.lastName}".trim(),
        initial = user.firstName.take(1).uppercase(Locale.getDefault()),
        details = details,
        requested = parseInstant(createdAt)?.let { "Requested ${formatRequestedAgo(it, now)}" },
        message = message?.takeIf { it.isNotBlank() }
    )
}
