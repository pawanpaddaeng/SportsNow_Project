package com.example.cpen321application.navigation.routes

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.game.location.hasLocationPermission
import com.example.cpen321application.game.model.GameLocation
import com.example.cpen321application.navigation.PICKED_LOCATION_KEY
import com.example.cpen321application.navigation.PICK_START_KEY
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.discover.DiscoverActions
import com.example.cpen321application.ui.screens.discover.DiscoverScreen
import com.example.cpen321application.ui.screens.discover.DiscoverUiState

@Composable
fun DiscoverRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val discover = app.discoverState
    // only ask once per visit, the system dialog pauses and resumes this screen
    var askedForPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            discover.load(refreshLocation = true)
        } else {
            discover.error = "SportsNow needs your location to find games near you. " +
                "Allow location access, then try again."
        }
    }

    fun askForPermission() {
        askedForPermission = true
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // a new location only when the user asks for it, otherwise the last one is reused
    fun reloadWithNewLocation() {
        if (discover.place != null || hasLocationPermission(context)) {
            discover.load(refreshLocation = true)
        } else {
            askForPermission()
        }
    }

    // Pick Location starts at the picked place, or at the user's location
    fun openPickLocation() {
        val start = discover.place ?: discover.location?.let {
            GameLocation(name = "Your location", address = "", latitude = it.latitude, longitude = it.longitude)
        }
        backStackEntry.savedStateHandle[PICK_START_KEY] = start
        app.navController.navigate(Routes.PICK_LOCATION)
    }

    /*  reloads whenever the screen is shown. Coming back from Pick Location with a new
        place searches there, going back without confirming keeps the current place
     */
    OnResume(backStackEntry) {
        val handle = backStackEntry.savedStateHandle
        val start = handle.remove<GameLocation>(PICK_START_KEY)
        val picked = handle.remove<GameLocation>(PICKED_LOCATION_KEY)
        if (picked != null && picked != start) discover.place = picked
        when {
            discover.place != null || hasLocationPermission(context) -> discover.load(refreshLocation = false)
            !askedForPermission -> askForPermission()
        }
    }

    DiscoverScreen(
        state = DiscoverUiState(
            games = discover.games,
            location = discover.location,
            place = discover.place,
            hasLoaded = discover.hasLoaded,
            isLoading = discover.isLoading,
            errorMessage = discover.error
        ),
        actions = DiscoverActions(
            onRetry = { reloadWithNewLocation() },
            onRefresh = { reloadWithNewLocation() },
            onChangeLocation = { openPickLocation() },
            onUseMyLocation = {
                discover.place = null
                reloadWithNewLocation()
            },
            onGameClick = { game -> app.navController.navigate(Routes.gameDetails(game.gameId)) },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}
