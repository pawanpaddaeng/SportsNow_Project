package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.network.GameDto
import com.example.cpen321application.network.PendingJoinRequestDto
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.theme.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerDayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

// how the popup's "How often do you play?" answers read on a request card
private val FREQUENCY_TEXT = mapOf(
    "rarely" to "plays rarely",
    "monthly" to "plays monthly",
    "weekly" to "plays weekly",
    "several_per_week" to "plays several times a week"
)

// the game at the top of the screen: "Volleyball Night" and "Fri, Oct 17 · 8/12 players · 4 spots left"
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
    val day = try {
        Instant.parse(startsAt).atZone(ZoneId.systemDefault()).format(headerDayFormat)
    } catch (e: Exception) {
        startsAt
    }
    val spotsLeft = (maxPlayers - playerCount).coerceAtLeast(0)
    val spotsText = when (spotsLeft) {
        0 -> "full"
        1 -> "1 spot left"
        else -> "$spotsLeft spots left"
    }
    return JoinRequestsHeader(
        title = title,
        subtitle = "$day · $playerCount/$maxPlayers players · $spotsText",
        isFull = spotsLeft == 0
    )
}

// "just now", "5 min ago", "2h ago", "yesterday" or "3 days ago"
internal fun formatRequestedAgo(createdAt: Instant, now: Instant): String {
    val minutes = Duration.between(createdAt, now).toMinutes().coerceAtLeast(0)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        hours < 1 -> "$minutes min ago"
        days < 1 -> "${hours}h ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}

fun PendingJoinRequestDto.toJoinRequestItem(now: Instant = Instant.now()): JoinRequestItem {
    val requested = try {
        "requested ${formatRequestedAgo(Instant.parse(createdAt), now)}"
    } catch (e: Exception) {
        null
    }
    val details = listOfNotNull(
        skillLevel.replaceFirstChar { it.titlecase(Locale.getDefault()) },
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

/*
    Join Requests screen from the Figma mockup (Accept players to join): the organizer's
    pending requests for one game, each with Decline and Approve. header is null until
    the game loads. isLoading shows a loading bar, errorMessage a problem with Retry when
    onRetry is given. busyRequestIds are requests whose answer is being sent, their
    buttons are disabled. Approve is disabled once the game is full.
    The callbacks connect it to other screens through AppNavigation
 */
@Composable
fun JoinRequestsScreen(
    header: JoinRequestsHeader?,
    requests: List<JoinRequestItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    busyRequestIds: Set<Int> = emptySet(),
    onRetry: (() -> Unit)? = null,
    onApprove: (JoinRequestItem) -> Unit = {},
    onDecline: (JoinRequestItem) -> Unit = {},
    onTabSelected: (BottomTab) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(selectedTab = BottomTab.Home, onTabSelected = onTabSelected)
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "top") { TopBar(onBack = onBack) }

            if (header != null) {
                item(key = "game") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = header.title, color = SportsOnSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(text = header.subtitle, color = SportsOnSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }

            // thin loading bar while the list updates, the current requests stay visible
            if (isLoading) {
                item(key = "loading") {
                    LinearProgressIndicator(
                        color = SportsPrimary,
                        trackColor = SportsPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (errorMessage != null) {
                item(key = "error") { ErrorMessage(message = errorMessage, onRetry = onRetry) }
            }

            if (header?.isFull == true && requests.isNotEmpty()) {
                item(key = "full") {
                    Note("The game is full. Edit the game to add spots before approving more players.")
                }
            }

            items(requests, key = { it.requestId }) { request ->
                RequestCard(
                    request = request,
                    isBusy = request.requestId in busyRequestIds,
                    canApprove = header?.isFull == false,
                    onApprove = { onApprove(request) },
                    onDecline = { onDecline(request) }
                )
            }

            if (header != null) {
                item(key = "footer") {
                    Note(
                        if (requests.isEmpty()) {
                            "No join requests right now. You'll get a notification when someone asks to join."
                        } else {
                            "Approved players are added to the game and its chat."
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SportsOnSurface
            )
        }
        Text(text = "Join Requests", color = SportsOnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorMessage(message: String, onRetry: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(text = message, color = SportsOnSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(50)) {
                Text(text = "Retry", color = SportsPrimary)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = SportsOnSurfaceVariant,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

// one request: initial, name, details, the quoted message and the two buttons
@Composable
private fun RequestCard(
    request: JoinRequestItem,
    isBusy: Boolean,
    canApprove: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, SportsOutlineVariant, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SportsPrimaryContainer)
            ) {
                Text(text = request.initial, color = SportsOnPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = request.name, color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = request.details, color = SportsOnSurfaceVariant, fontSize = 12.sp)
            }
        }

        if (request.message != null) {
            Text(
                text = "“${request.message}”",
                color = SportsOnSurface,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SportsSurfaceContainer)
                    .padding(12.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onDecline,
                enabled = !isBusy,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, SportsOutline),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = SportsPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Decline", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onApprove,
                enabled = !isBusy && canApprove,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Approve", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
