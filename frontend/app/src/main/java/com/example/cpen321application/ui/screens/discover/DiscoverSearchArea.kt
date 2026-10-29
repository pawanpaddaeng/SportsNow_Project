package com.example.cpen321application.ui.screens.discover

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.location.DeviceLocation
import com.example.cpen321application.game.model.DiscoverGame
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOnSurfaceVariant
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

// zoom that shows a few kilometres around the user
private const val MAP_ZOOM = 13f
private val MAP_HEIGHT = 160.dp

/*  where the games are searched around: the map with "Showing games near" and the
    place under it, like the location field when creating a game. Tapping it opens the
    map picker. With a picked place, "Use my location" goes back to the user's location
 */
@Composable
internal fun SearchArea(
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
