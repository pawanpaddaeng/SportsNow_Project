package com.example.cpen321application.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.location.DeviceLocation
import com.example.cpen321application.network.DiscoverGameDto
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.components.RetryMessage
import com.example.cpen321application.ui.components.pillBackground
import com.example.cpen321application.ui.components.spotsLeftLabel
import com.example.cpen321application.ui.screens.games.GameLocation
import com.example.cpen321application.ui.screens.games.SPORTS
import com.example.cpen321application.ui.screens.games.SelectableChip
import com.example.cpen321application.ui.theme.*
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// zoom that shows a few kilometres around the user
private const val MAP_ZOOM = 13f
private val MAP_HEIGHT = 160.dp

// average walking speed, used for "3 min walk"
private const val WALKING_METRES_PER_MINUTE = 80.0
// further than this a walking time isn't useful, only the distance is shown
private const val MAX_WALK_METRES = 3000

// games starting within this many minutes get a "Starts in 45 min" pill
private const val STARTS_SOON_MINUTES = 120L

// faded look for games that are already full
private const val FULL_GAME_ALPHA = 0.5f

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dayTimeFormat = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())

/*  what a Discover card shows, built from the server's reply with toDiscoverGame.
    sport is the server's value (e.g. "basketball"), used by the sport filter
 */
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

// "250 m" under a kilometre (to the nearest 50 m), otherwise "1.4 km"
internal fun formatDistance(metres: Int): String =
    if (metres < 1000) {
        "${((metres / 50.0).roundToInt() * 50).coerceAtLeast(50)} m"
    } else {
        String.format(Locale.getDefault(), "%.1f km", metres / 1000.0)
    }

// "Today · 5:30 PM", "Tomorrow · 9:00 AM" or "Sat, Oct 18 · 3:00 PM" in the phone's time zone
internal fun formatStartTime(start: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val startDay = start.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (startDay) {
        today -> "Today · ${start.atZone(zone).format(timeFormat)}"
        today.plusDays(1) -> "Tomorrow · ${start.atZone(zone).format(timeFormat)}"
        else -> start.atZone(zone).format(dayTimeFormat)
    }
}

// "Starts in 45 min" or "Starts in 1 h 30 min" for games starting soon, null otherwise
internal fun formatStartsIn(start: Instant, now: Instant): String? {
    val minutes = Duration.between(now, start).toMinutes()
    if (minutes < 0 || minutes > STARTS_SOON_MINUTES) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "Starts in $minutes min"
        rest == 0L -> "Starts in $hours h"
        else -> "Starts in $hours h $rest min"
    }
}

// turns a game from the server into what its Discover card shows
fun DiscoverGameDto.toDiscoverGame(now: Instant = Instant.now()): DiscoverGame {
    val start = try {
        Instant.parse(game.startsAt)
    } catch (e: DateTimeParseException) {
        null
    }
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

/*  what the Discover screen's controls do. Tapping the map opens the map picker
    (onChangeLocation) and onUseMyLocation goes back to searching near the user
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
    the user on a map and as a list, closest first, with a sport filter at the top.
    The actions connect it to other screens through AppNavigation
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

/*  where the games are searched around: the map with "Showing games near" and the
    place under it, like the location field when creating a game. Tapping it opens the
    map picker. With a picked place, "Use my location" goes back to the user's location
 */
@Composable
private fun SearchArea(
    location: DeviceLocation?,
    place: GameLocation?,
    games: List<DiscoverGame>,
    onChangeLocation: () -> Unit,
    onUseMyLocation: () -> Unit,
    onGameClick: (DiscoverGame) -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, SportsOutlineVariant, shape)
            .clickable(onClick = onChangeLocation)
    ) {
        if (location != null) {
            GamesMap(
                location = location,
                isPickedPlace = place != null,
                games = games,
                onMapClick = onChangeLocation,
                onGameClick = onGameClick
            )
        }
        CardRow {
            Icon(imageVector = Icons.Filled.Place, contentDescription = null, tint = Color(0xFFD93025))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Showing games near", color = SportsOnSurfaceVariant, fontSize = 12.sp)
                Text(
                    text = place?.name ?: if (location != null) "Your location" else "Choose a location",
                    color = SportsOnSurface,
                    fontSize = 16.sp
                )
                if (place != null && place.address.isNotBlank()) {
                    Text(text = place.address, color = SportsOnSurfaceVariant, fontSize = 12.sp)
                }
            }
            Text(text = "Change", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (place != null) {
            TextButton(onClick = onUseMyLocation, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = SportsPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Use my location", color = SportsPrimary)
            }
        }
    }
}

/*  map around the search point with a pin per game (faded when full) and a pin for
    the search point: azure for the user, red for a picked place. Lite mode draws it as
    a still image, cheaper inside a scrolling list. Tapping a game's pin opens it,
    tapping elsewhere calls onMapClick
 */
@Composable
private fun GamesMap(
    location: DeviceLocation,
    isPickedPlace: Boolean,
    games: List<DiscoverGame>,
    onMapClick: () -> Unit,
    onGameClick: (DiscoverGame) -> Unit
) {
    val userPoint = LatLng(location.latitude, location.longitude)
    val key = "${location.latitude},${location.longitude}"
    val cameraState = rememberCameraPositionState(key = key) {
        position = CameraPosition.fromLatLngZoom(userPoint, MAP_ZOOM)
    }

    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT),
        cameraPositionState = cameraState,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false
        ),
        // also stops lite mode from opening the Google Maps app on a tap
        onMapClick = { onMapClick() }
    ) {
        Marker(
            state = rememberMarkerState(key = "you_$key", position = userPoint),
            title = if (isPickedPlace) "Search area" else "You",
            icon = BitmapDescriptorFactory.defaultMarker(
                if (isPickedPlace) BitmapDescriptorFactory.HUE_RED else BitmapDescriptorFactory.HUE_AZURE
            )
        )
        games.forEach { game ->
            Marker(
                state = rememberMarkerState(
                    key = "game_${game.gameId}",
                    position = LatLng(game.latitude, game.longitude)
                ),
                title = game.title,
                alpha = if (game.isFull) FULL_GAME_ALPHA else 1f,
                onClick = {
                    onGameClick(game)
                    true
                }
            )
        }
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

// "1. Volleyball Drop-in" card with time, place, distance and status pills
@Composable
private fun GameCard(rank: Int, game: DiscoverGame, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (game.isFull) FULL_GAME_ALPHA else 1f)
            .clip(shape)
            .background(SportsSurfaceContainer)
            .border(1.dp, SportsOutlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // sport initial as the avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SportsPrimaryContainer)
            ) {
                Text(
                    text = game.sport.take(1).uppercase(Locale.getDefault()),
                    color = SportsOnPrimaryContainer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "$rank. ${game.title}", color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = game.whenText, color = SportsOnSurfaceVariant, fontSize = 14.sp)
                Text(text = game.locationName, color = SportsOnSurfaceVariant, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = game.distanceText, color = SportsPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (game.walkText != null) {
                    Text(text = game.walkText, color = SportsOnSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        StatusPills(game)
    }
}

// "3 spots left", "Request pending" and "Starts in 45 min" under a game card
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusPills(game: DiscoverGame) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(text = spotsLeftLabel(game.spotsLeft))
        if (game.requestPending) {
            Pill(text = "Request pending", textColor = SportsPrimary, borderColor = SportsPrimary)
        }
        if (game.startsInText != null && !game.isFull) {
            Pill(
                text = game.startsInText,
                background = SportsPrimaryContainer,
                textColor = SportsOnPrimaryContainer,
                borderColor = null
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    background: Color = Color.White,
    textColor: Color = SportsOnSurfaceVariant,
    borderColor: Color? = SportsOutlineVariant
) {
    Text(
        text = text,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.pillBackground(background, borderColor, horizontalPadding = 10.dp)
    )
}
