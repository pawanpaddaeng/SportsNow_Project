package com.example.cpen321application.game.model

import com.example.cpen321application.game.capitalized
import com.example.cpen321application.game.dto.GameDto
import com.example.cpen321application.game.dto.PendingJoinRequestDto
import com.example.cpen321application.game.formatDay
import com.example.cpen321application.game.formatRequestedAgo
import com.example.cpen321application.game.parseInstant
import com.example.cpen321application.game.spotsLeftLabel
import java.time.Instant
import java.util.Locale

// how the popup's "How often do you play?" answers read on a request card
private val FREQUENCY_TEXT = mapOf(
    "rarely" to "plays rarely",
    "monthly" to "plays monthly",
    "weekly" to "plays weekly",
    "several_per_week" to "plays several times a week"
)

// the game at the top of Join Requests: "Volleyball Night" and "Fri, Oct 17 · 8/12 players · 4 spots left"
data class JoinRequestsHeader(val title: String, val subtitle: String, val isFull: Boolean)

// a request card: who asked, "Intermediate · plays weekly · requested 2h ago" and their message
data class JoinRequestItem(
    val requestId: Int,
    val name: String,
    val initial: String,
    val details: String,
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
    val requested = parseInstant(createdAt)?.let { "requested ${formatRequestedAgo(it, now)}" }
    val details = listOfNotNull(
        skillLevel.capitalized(),
        playFrequency?.let { FREQUENCY_TEXT[it] },
        requested
    ).joinToString(" · ")
    return JoinRequestItem(
        requestId = requestId,
        name = "${user.firstName} ${user.lastName}".trim(),
        initial = user.firstName.take(1).uppercase(Locale.getDefault()),
        details = details,
        message = message?.takeIf { it.isNotBlank() }
    )
}
