package com.example.cpen321application.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.network.GameDto
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*  short summary of a game used by the cards on the home screen
    pendingRequests is only used for games the user organizes
 */
data class GameSummary(
    val id: String,
    val sport: String,
    val title: String,
    val dateTime: String,
    val location: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val pendingRequests: Int = 0
)

// e.g. "Sat, Oct 18 · 3:00 PM" in the phone's time zone
private val cardDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())

// turns a game from the server into what the home screen card shows
fun GameDto.toGameSummary(): GameSummary {
    val dateTime = try {
        Instant.parse(startsAt).atZone(ZoneId.systemDefault()).format(cardDateFormat)
    } catch (e: Exception) {
        startsAt
    }
    return GameSummary(
        id = gameId.toString(),
        sport = sport.replaceFirstChar { it.titlecase(Locale.getDefault()) },
        title = title,
        dateTime = dateTime,
        location = locationName,
        playerCount = playerCount,
        maxPlayers = maxPlayers
    )
}

/*
    Home screen for SportsNow:
    Shows the games the user joined and organized with the bottom navigation bar.
    isRefreshing shows a loading bar while the list updates, errorMessage is shown
    above the games when an update failed (the last loaded games stay visible).
    The callbacks connect it to other screens through AppNavigation
 */
@Composable
fun HomeScreen(
    joinedGames: List<GameSummary>,
    organizedGames: List<GameSummary>,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    errorMessage: String? = null,
    onCreateGame: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onGameClick: (GameSummary) -> Unit = {},
    onTabSelected: (BottomTab) -> Unit = {}
) {
    var joinedExpanded by rememberSaveable { mutableStateOf(true) }
    var organizedExpanded by rememberSaveable { mutableStateOf(true) }

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
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SportsNow",
                        color = SportsOnSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isRefreshing) SportsOutlineVariant else SportsOnSurface
                        )
                    }
                }
            }

            // thin loading bar while the games update, the current list stays visible
            if (isRefreshing) {
                item(key = "refreshing") {
                    LinearProgressIndicator(
                        color = SportsPrimary,
                        trackColor = SportsPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (errorMessage != null) {
                item(key = "error") {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            gameSection(
                title = "Joined Games",
                games = joinedGames,
                expanded = joinedExpanded,
                onToggle = { joinedExpanded = !joinedExpanded },
                emptyText = "You haven't joined any games yet. Check Discover for games near you.",
                onGameClick = onGameClick
            )

            item { Spacer(modifier = Modifier.height(4.dp)) }

            gameSection(
                title = "Organized Games",
                games = organizedGames,
                expanded = organizedExpanded,
                onToggle = { organizedExpanded = !organizedExpanded },
                emptyText = "You haven't organized any games yet.",
                onGameClick = onGameClick
            )

            item { CreateGameButton(onClick = onCreateGame) }
        }
    }
}

// collapsible section with a header, its game cards, or a message when empty
private fun LazyListScope.gameSection(
    title: String,
    games: List<GameSummary>,
    expanded: Boolean,
    onToggle: () -> Unit,
    emptyText: String,
    onGameClick: (GameSummary) -> Unit
) {
    item(key = "header_$title") {
        SectionHeader(title = title, count = games.size, expanded = expanded, onToggle = onToggle)
    }
    if (!expanded) return

    if (games.isEmpty()) {
        item(key = "empty_$title") {
            Text(text = emptyText, color = SportsOnSurfaceVariant, fontSize = 14.sp)
        }
    } else {
        items(games, key = { "${title}_${it.id}" }) { game ->
            GameCard(game = game, onClick = { onGameClick(game) })
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        Text(text = title, color = SportsOnSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Pill(text = count.toString(), background = SportsSurfaceContainer, textColor = SportsOnSurfaceVariant)
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = SportsOnSurface,
            modifier = Modifier.rotate(if (expanded) 0f else -90f)
        )
    }
}

@Composable
private fun GameCard(game: GameSummary, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SportsSurfaceContainer)
            .border(1.dp, SportsOutlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // sport initial as the avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SportsPrimaryContainer)
        ) {
            Text(
                text = game.sport.take(1),
                color = SportsOnPrimaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = game.title, color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = game.dateTime, color = SportsOnSurfaceVariant, fontSize = 14.sp)
            Text(text = game.location, color = SportsOnSurfaceVariant, fontSize = 12.sp)
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                text = "${game.playerCount}/${game.maxPlayers}",
                background = Color.White,
                textColor = SportsOnSurfaceVariant,
                borderColor = SportsOutlineVariant
            )
            if (game.pendingRequests > 0) {
                val label = if (game.pendingRequests == 1) "1 request" else "${game.pendingRequests} requests"
                Pill(text = label, background = MaterialTheme.colorScheme.error, textColor = Color.White)
            }
        }
    }
}

@Composable
private fun Pill(
    text: String,
    background: Color,
    textColor: Color,
    borderColor: Color? = null
) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// dashed outline button under the organized games
@Composable
private fun CreateGameButton(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRoundRect(
                    color = SportsOutlineVariant,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = SportsPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Organize a new game",
            color = SportsPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
