package com.example.cpen321application.navigation.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cpen321application.game.location.DeviceLocation
import com.example.cpen321application.game.location.currentDeviceLocation
import com.example.cpen321application.game.location.isLocationEnabled
import com.example.cpen321application.game.model.DiscoverGame
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.game.model.toDiscoverGame
import com.example.cpen321application.game.repository.DiscoverGamesResult
import com.example.cpen321application.game.repository.fetchDiscoverGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// the games shown on Discover and the location they were loaded for
class DiscoverState(private val context: Context, private val scope: CoroutineScope) {
    val games = mutableStateListOf<DiscoverGame>()
    var location by mutableStateOf<DeviceLocation?>(null)
        private set
    var hasLoaded by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    // place the user picked on the map to search around, null searches around the phone
    var place by mutableStateOf<GameLocation?>(null)

    /*  loads the games near the picked place, or near the user when none is picked.
        The phone's location is only looked up again when refreshLocation is set (or
        none is known yet), to save battery. On failure the last loaded games stay
     */
    fun load(refreshLocation: Boolean) {
        if (isLoading) return
        isLoading = true
        scope.launch {
            try {
                val picked = place
                val point = when {
                    picked != null -> DeviceLocation(picked.latitude, picked.longitude)
                    else -> location.takeUnless { refreshLocation } ?: currentDeviceLocation(context)
                }
                if (point == null) {
                    error = if (isLocationEnabled(context)) {
                        "Couldn't find where you are yet. Wait a moment, then try again."
                    } else {
                        "Location is turned off. Turn it on in your phone's settings, then try again."
                    }
                } else {
                    location = point
                    showGames(point)
                }
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun showGames(point: DeviceLocation) {
        when (val result = fetchDiscoverGames(context, point.latitude, point.longitude)) {
            is DiscoverGamesResult.Loaded -> {
                games.clear()
                games.addAll(result.games.map { it.toDiscoverGame() })
                hasLoaded = true
                error = null
            }
            is DiscoverGamesResult.Failed -> error = result.message
        }
    }

    fun clear() {
        games.clear()
        location = null
        place = null
        hasLoaded = false
        error = null
    }
}
