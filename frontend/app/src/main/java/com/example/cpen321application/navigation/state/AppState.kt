package com.example.cpen321application.navigation.state

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.cpen321application.core.rememberSafeCoroutineScope
import com.example.cpen321application.core.session.AuthTokenStore
import com.example.cpen321application.core.session.jwtToken
import com.example.cpen321application.game.location.DeviceLocation
import com.example.cpen321application.game.location.currentDeviceLocation
import com.example.cpen321application.game.location.isLocationEnabled
import com.example.cpen321application.game.repository.DiscoverGamesResult
import com.example.cpen321application.game.repository.MyGamesResult
import com.example.cpen321application.game.repository.fetchDiscoverGames
import com.example.cpen321application.game.repository.fetchMyGames
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.SIGN_IN_MESSAGE_KEY
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.screens.discover.DiscoverGame
import com.example.cpen321application.ui.screens.discover.toDiscoverGame
import com.example.cpen321application.ui.screens.games.GameLocation
import com.example.cpen321application.ui.screens.home.GameSummary
import com.example.cpen321application.ui.screens.home.toGameSummary
import com.example.cpen321application.user.NotificationRegistration
import com.example.cpen321application.user.repository.ProfileForm
import com.example.cpen321application.user.repository.ProfileResult
import com.example.cpen321application.user.repository.UserProfile
import com.example.cpen321application.user.repository.fetchMyProfile
import com.example.cpen321application.user.repository.updateMyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
    State shared by the app's screens: the Home games, the Discover games and the user's
    profile, with what loads them and moves between screens. Kept for the whole app so
    the loaded data survives going to other screens and back, and stays visible while a
    refresh loads. scope shows a message instead of crashing on an unexpected error, so
    every load resets its loading flag in a finally block
 */
class AppState(
    val navController: NavHostController,
    private val context: Context,
    val scope: CoroutineScope
) {
    // games shown on Home, loaded from the server
    val organizedGames = mutableStateListOf<GameSummary>()
    val joinedGames = mutableStateListOf<GameSummary>()
    var isRefreshingGames by mutableStateOf(false)
        private set
    var gamesError by mutableStateOf<String?>(null)
        private set

    // games shown on Discover and the location they were loaded for
    val discoverGames = mutableStateListOf<DiscoverGame>()
    var discoverLocation by mutableStateOf<DeviceLocation?>(null)
        private set
    var hasLoadedDiscover by mutableStateOf(false)
        private set
    var isLoadingDiscover by mutableStateOf(false)
        private set
    var discoverError by mutableStateOf<String?>(null)
    // place the user picked on the map to search around, null searches around the phone
    var discoverPlace by mutableStateOf<GameLocation?>(null)

    // the signed in user's profile, for the Profile and Edit Profile screens
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var isLoadingProfile by mutableStateOf(false)
        private set
    var profileError by mutableStateOf<String?>(null)
        private set
    var isSavingProfile by mutableStateOf(false)
        private set
    var saveProfileError by mutableStateOf<String?>(null)

    // sign in automatically with a token from a previous session, if it expired
    // the notification registration renews it
    fun startDestination(): String {
        val savedToken = AuthTokenStore.load(context) ?: return Routes.SIGN_IN
        jwtToken = savedToken
        return Routes.HOME
    }

    // loads the user's games from the server, on failure the last loaded games stay
    fun refreshGames() {
        if (isRefreshingGames) return
        isRefreshingGames = true
        scope.launch {
            try {
                when (val result = fetchMyGames(context)) {
                    is MyGamesResult.Loaded -> {
                        organizedGames.clear()
                        organizedGames.addAll(result.organized.map { it.toGameSummary() })
                        joinedGames.clear()
                        joinedGames.addAll(result.joined.map { it.toGameSummary() })
                        gamesError = null
                    }
                    is MyGamesResult.Failed -> gamesError = result.message
                }
            } finally {
                isRefreshingGames = false
            }
        }
    }

    /*  loads the games near the picked place, or near the user when none is picked.
        The phone's location is only looked up again when refreshLocation is set (or
        none is known yet), to save battery. On failure the last loaded games stay
     */
    fun loadDiscover(refreshLocation: Boolean) {
        if (isLoadingDiscover) return
        isLoadingDiscover = true
        scope.launch {
            try {
                val place = discoverPlace
                val location = when {
                    place != null -> DeviceLocation(place.latitude, place.longitude)
                    else -> discoverLocation.takeUnless { refreshLocation } ?: currentDeviceLocation(context)
                }
                if (location == null) {
                    discoverError = if (isLocationEnabled(context)) {
                        "Couldn't find where you are yet. Wait a moment, then try again."
                    } else {
                        "Location is turned off. Turn it on in your phone's settings, then try again."
                    }
                } else {
                    discoverLocation = location
                    showDiscoverGames(location)
                }
            } finally {
                isLoadingDiscover = false
            }
        }
    }

    private suspend fun showDiscoverGames(location: DeviceLocation) {
        when (val result = fetchDiscoverGames(context, location.latitude, location.longitude)) {
            is DiscoverGamesResult.Loaded -> {
                discoverGames.clear()
                discoverGames.addAll(result.games.map { it.toDiscoverGame() })
                hasLoadedDiscover = true
                discoverError = null
            }
            is DiscoverGamesResult.Failed -> discoverError = result.message
        }
    }

    fun refreshProfile() {
        if (isLoadingProfile) return
        isLoadingProfile = true
        scope.launch {
            try {
                when (val result = fetchMyProfile(context)) {
                    is ProfileResult.Loaded -> {
                        profile = result.profile
                        profileError = null
                    }
                    is ProfileResult.Failed -> profileError = result.message
                }
            } finally {
                isLoadingProfile = false
            }
        }
    }

    fun saveProfile(form: ProfileForm) {
        if (isSavingProfile) return
        isSavingProfile = true
        saveProfileError = null
        scope.launch {
            val result = try {
                updateMyProfile(context, form)
            } finally {
                isSavingProfile = false
            }
            when (result) {
                is ProfileResult.Loaded -> {
                    profile = result.profile
                    profileError = null
                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                    // the user may have left Edit Profile while it was saving
                    if (navController.currentDestination?.route == Routes.EDIT_PROFILE) {
                        navController.popBackStack()
                    }
                }
                is ProfileResult.Failed -> saveProfileError = result.message
            }
        }
    }

    // bottom bar: Home, Discover and Profile are built, the other tabs aren't yet
    fun openTab(tab: BottomTab) {
        when (tab) {
            BottomTab.Home -> navController.popBackStack(Routes.HOME, inclusive = false)
            // Home stays underneath so back from Discover or Profile returns to it
            BottomTab.Discover -> navController.navigate(Routes.DISCOVER) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
            BottomTab.Profile -> navController.navigate(Routes.PROFILE) {
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
            else -> Toast.makeText(context, "${tab.label} isn't available yet", Toast.LENGTH_SHORT).show()
        }
    }

    // forget the session and show sign in with a message, clearing every other screen
    fun returnToSignIn() {
        jwtToken = null
        AuthTokenStore.clear(context)
        // the next user to sign in shouldn't see these games
        organizedGames.clear()
        joinedGames.clear()
        gamesError = null
        discoverGames.clear()
        discoverLocation = null
        discoverPlace = null
        hasLoadedDiscover = false
        discoverError = null
        profile = null
        profileError = null
        saveProfileError = null
        navController.navigate(Routes.SIGN_IN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
        navController.currentBackStackEntry?.savedStateHandle?.set(
            SIGN_IN_MESSAGE_KEY,
            "Your session ended. Please sign in again."
        )
    }

    // send this device's Firebase token to the server, renewing an expired JWT
    // silently if needed. Goes back to sign in if the session can't be used
    fun registerNotifications() {
        scope.launch {
            NotificationRegistration.register(context)
        }
    }

    // keep the token for this session and future launches, then go home
    // and remove sign in/up so back doesn't return to them
    fun completeSignIn(token: String) {
        jwtToken = token
        AuthTokenStore.save(context, token)
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.SIGN_IN) { inclusive = true }
        }
        registerNotifications()
    }
}

// the app's state, created once and kept while AppNavigation is shown
@Composable
fun rememberAppState(): AppState {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberSafeCoroutineScope()
    return remember(navController, context, scope) { AppState(navController, context, scope) }
}
