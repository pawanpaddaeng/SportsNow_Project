package com.example.cpen321application.ui.screens.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.SPORTS
import com.example.cpen321application.game.location.DeviceLocation
import com.example.cpen321application.game.model.DiscoverGame
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.RetryMessage
import com.example.cpen321application.ui.components.SelectableChip
import com.example.cpen321application.ui.theme.*
import java.util.Locale

/*  what the Discover screen shows. location is where the games were searched around,
    null until it is known. place is the place the user picked to search around instead
    of their own location, null when searching near them. isLoading shows a loading bar,
    errorMessage a problem with a Try again button: no location (2a) or the games couldn't
    load. games are the last loaded games, they stay visible while refreshing
 */
data class DiscoverUiState(
    val games: List<DiscoverGame>,
    val location: DeviceLocation?,
    val place: GameLocation? = null,
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/*  tapping the map opens the map picker (onChangeLocation) and onUseMyLocation
    goes back to searching near the user
 */
data class DiscoverActions(
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onChangeLocation: () -> Unit = {},
    val onUseMyLocation: () -> Unit = {},
    val onGameClick: (DiscoverGame) -> Unit = {},
    val onTabSelected: (BottomTab) -> Unit = {}
)

/*
    Discover screen from the Figma mockup (Use Case 3, Discover Game): the games near
    the user on a map and as a list, closest first, with a sport filter at the top
 */
@Composable
fun DiscoverScreen(state: DiscoverUiState, actions: DiscoverActions, modifier: Modifier = Modifier) {
    // null shows every sport
    var selectedSport by rememberSaveable { mutableStateOf<String?>(null) }
    val shownGames = state.games.filter { selectedSport == null || it.sport == selectedSport }
    val sportLabel = SPORTS.firstOrNull { it.second == selectedSport }?.first

    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(selectedTab = BottomTab.Discover, onTabSelected = actions.onTabSelected)
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "header") {
                Header(place = state.place, isLoading = state.isLoading, onRefresh = actions.onRefresh)
            }

            item(key = "search_area") {
                SearchArea(
                    location = state.location,
                    place = state.place,
                    games = shownGames,
                    onChangeLocation = actions.onChangeLocation,
                    onUseMyLocation = actions.onUseMyLocation,
                    onGameClick = actions.onGameClick
                )
            }

            item(key = "sports") {
                SportFilter(selectedSport = selectedSport, onSelect = { selectedSport = it })
            }

            // thin loading bar while the games update, the current list stays visible
            if (state.isLoading) {
                item(key = "loading") {
                    LinearProgressIndicator(
                        color = SportsPrimary,
                        trackColor = SportsPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.errorMessage != null) {
                item(key = "error") { RetryMessage(state.errorMessage, actions.onRetry, Modifier.padding(vertical = 16.dp), "Try again") }
            }

            if (state.hasLoaded) {
                gameList(games = shownGames, allGames = state.games, sportLabel = sportLabel, onGameClick = actions.onGameClick)
            }
        }
    }
}

@Composable
private fun Header(place: GameLocation?, isLoading: Boolean, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Discover",
                color = SportsOnSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = if (isLoading) SportsOutlineVariant else SportsOnSurface
                )
            }
        }
        Text(
            text = "Open games near ${place?.name ?: "you"}, closest first",
            color = SportsOnSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

// "All", "Basketball", "Soccer", ... scrolling row, one selected at a time
@Composable
private fun SportFilter(selectedSport: String?, onSelect: (String?) -> Unit) {
    val options = listOf("All" to null) + SPORTS
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.first }) { (label, sport) ->
            SelectableChip(text = label, selected = sport == selectedSport, onClick = { onSelect(sport) })
        }
    }
}

/*  "4 games nearby" with the cards, or a message when there are none nearby (3a)
    or none of the selected sport (5a)
 */
private fun LazyListScope.gameList(
    games: List<DiscoverGame>,
    allGames: List<DiscoverGame>,
    sportLabel: String?,
    onGameClick: (DiscoverGame) -> Unit
) {
    if (games.isEmpty()) {
        item(key = "empty") {
            EmptyMessage(
                text = when {
                    allGames.isEmpty() -> "No games nearby right now. Check back later or organize your own game."
                    else -> "No ${sportLabel?.lowercase(Locale.getDefault()) ?: ""} games nearby. Try another sport."
                }
            )
        }
        return
    }

    item(key = "count") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val sportWord = sportLabel?.let { "${it.lowercase(Locale.getDefault())} " } ?: ""
            Text(
                text = if (games.size == 1) "1 ${sportWord}game nearby" else "${games.size} ${sportWord}games nearby",
                color = SportsOnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(text = "Sorted by distance", color = SportsOnSurfaceVariant, fontSize = 12.sp)
        }
    }

    itemsIndexed(games, key = { _, game -> game.gameId }) { index, game ->
        GameCard(rank = index + 1, game = game, onClick = { onGameClick(game) })
    }

    item(key = "end") {
        EmptyMessage(text = "That's everything near you. Check back later or organize your own game.")
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        color = SportsOnSurfaceVariant,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}
