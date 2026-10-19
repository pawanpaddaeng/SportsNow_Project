package com.example.cpen321application.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cpen321application.auth.AuthTokenStore
import com.example.cpen321application.auth.Session
import com.example.cpen321application.auth.firstName
import com.example.cpen321application.auth.jwtToken
import com.example.cpen321application.auth.lastName
import com.example.cpen321application.games.GameChangeResult
import com.example.cpen321application.games.GameDetailsResult
import com.example.cpen321application.games.MyGamesResult
import com.example.cpen321application.games.cancelGame
import com.example.cpen321application.games.createGame
import com.example.cpen321application.games.updateGame
import com.example.cpen321application.games.fetchGameDetails
import com.example.cpen321application.games.fetchMyGames
import com.example.cpen321application.notifications.NotificationRegistration
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.screens.auth.SignInScreen
import com.example.cpen321application.ui.screens.auth.SignUpScreen
import com.example.cpen321application.ui.screens.games.GameDetails
import com.example.cpen321application.ui.screens.games.GameDetailsScreen
import com.example.cpen321application.ui.screens.games.GameInfoForm
import com.example.cpen321application.ui.screens.games.InputGameInfoScreen
import com.example.cpen321application.ui.screens.games.GameLocation
import com.example.cpen321application.ui.screens.games.toGameDetails
import com.example.cpen321application.ui.screens.games.toGameInfoForm
import com.example.cpen321application.ui.screens.games.PickLocationScreen
import com.example.cpen321application.ui.screens.home.GameSummary
import com.example.cpen321application.ui.screens.home.HomeScreen
import com.example.cpen321application.ui.screens.home.toGameSummary
import kotlinx.coroutines.launch

/*
    App navigation composable:
    Deals with the screens in ui/screens and handles
    their interactions with each other, such as switching
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // sign in automatically with a token from a previous session, if it expired
    // the notification registration below renews it
    val startDestination = remember {
        val savedToken = AuthTokenStore.load(context)
        if (savedToken != null) {
            jwtToken = savedToken
            Routes.HOME
        } else {
            Routes.SIGN_IN
        }
    }

    // games shown on Home, loaded from the server. Kept here so they survive going to
    // other screens and back, and stay visible while a refresh is loading
    val organizedGames = remember { mutableStateListOf<GameSummary>() }
    val joinedGames = remember { mutableStateListOf<GameSummary>() }
    var isRefreshingGames by remember { mutableStateOf(false) }
    var gamesError by remember { mutableStateOf<String?>(null) }

    // loads the user's games from the server, on failure the last loaded games stay
    fun refreshGames() {
        if (isRefreshingGames) return
        isRefreshingGames = true
        scope.launch {
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
            isRefreshingGames = false
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

    // any API call whose session can't be used or renewed asks for sign in through Session
    LaunchedEffect(Unit) {
        Session.signInRequired.collect { returnToSignIn() }
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

    // signed in from a previous session: register notifications once at start
    LaunchedEffect(Unit) {
        if (startDestination == Routes.HOME) {
            registerNotifications()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SIGN_IN) { backStackEntry ->
            // message left by another screen, e.g. sign up when the account already exists
            val infoMessage by backStackEntry.savedStateHandle
                .getStateFlow<String?>(SIGN_IN_MESSAGE_KEY, null)
                .collectAsState()

            SignInScreen(
                onSignedIn = { token -> completeSignIn(token) },
                onNavigateToSignUp = {
                    backStackEntry.savedStateHandle[SIGN_IN_MESSAGE_KEY] = null
                    navController.navigate(Routes.SIGN_UP)
                },
                modifier = modifier,
                infoMessage = infoMessage
            )
        }

        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onSignedUp = { details, token ->
                    firstName = details.firstName
                    lastName = details.lastName
                    completeSignIn(token)
                },
                onAlreadyExists = {
                    // go back to sign in and tell the user to sign in instead
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        SIGN_IN_MESSAGE_KEY,
                        "An account already exists for this Google account. Please sign in."
                    )
                    navController.popBackStack()
                },
                onNavigateToSignIn = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                modifier = modifier
            )
        }

        composable(Routes.HOME) { backStackEntry ->
            // refresh every time Home is shown: when it first opens, when the user
            // comes back from another screen, and when the app returns to the foreground
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) refreshGames()
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }

            HomeScreen(
                joinedGames = joinedGames,
                organizedGames = organizedGames,
                modifier = modifier,
                isRefreshing = isRefreshingGames,
                errorMessage = gamesError,
                onRefresh = { refreshGames() },
                onCreateGame = { navController.navigate(Routes.CREATE_GAME) },
                onGameClick = { game ->
                    game.id.toIntOrNull()?.let { navController.navigate(Routes.gameDetails(it)) }
                }
            )
        }

        composable(
            route = Routes.GAME_DETAILS,
            arguments = listOf(navArgument(Routes.GAME_ID_ARG) { type = NavType.IntType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getInt(Routes.GAME_ID_ARG) ?: 0
            var details by remember { mutableStateOf<GameDetails?>(null) }
            var isLoading by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var notFound by remember { mutableStateOf(false) }
            var isCancelling by remember { mutableStateOf(false) }

            // on failure the last loaded details stay visible with the error above them
            fun loadDetails() {
                if (isLoading) return
                isLoading = true
                scope.launch {
                    when (val result = fetchGameDetails(context, gameId)) {
                        is GameDetailsResult.Loaded -> {
                            details = result.details.toGameDetails(result.game)
                            error = null
                            notFound = false
                        }
                        GameDetailsResult.NotFound -> {
                            details = null
                            error = "This game no longer exists."
                            notFound = true
                        }
                        is GameDetailsResult.Failed -> error = result.message
                    }
                    isLoading = false
                }
            }

            // reload whenever the screen is shown, e.g. coming back after editing the game
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) loadDetails()
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }

            // placeholder for actions whose screens and APIs aren't built yet
            fun notAvailableYet(action: String) {
                Toast.makeText(context, "$action isn't available yet", Toast.LENGTH_SHORT).show()
            }

            GameDetailsScreen(
                details = details,
                isLoading = isLoading,
                errorMessage = error,
                isCancelling = isCancelling,
                onRetry = if (notFound) null else ({ loadDetails() }),
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.editGame(gameId)) },
                onDirections = { location -> openDirections(context, location) },
                onJoinRequests = { notAvailableYet("Reviewing join requests") },
                // the screen asks the organizer to confirm before calling this
                onCancelGame = {
                    isCancelling = true
                    scope.launch {
                        val result = cancelGame(context, gameId)
                        isCancelling = false
                        when (result) {
                            is GameChangeResult.Done -> {
                                Toast.makeText(context, "Game cancelled", Toast.LENGTH_SHORT).show()
                                loadDetails()
                            }
                            is GameChangeResult.Failed ->
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onLeaveGame = { notAvailableYet("Leaving a game") },
                onRequestToJoin = { notAvailableYet("Requesting to join") },
                onTabSelected = { tab ->
                    if (tab == BottomTab.Home) navController.popBackStack(Routes.HOME, inclusive = false)
                },
                modifier = modifier
            )
        }

        composable(Routes.CREATE_GAME) { backStackEntry ->
            var isSubmitting by remember { mutableStateOf(false) }
            // set by the Pick Location screen when the user confirms a place
            val pickedLocation by backStackEntry.savedStateHandle
                .getStateFlow<GameLocation?>(PICKED_LOCATION_KEY, null)
                .collectAsState()

            InputGameInfoScreen(
                isSubmitting = isSubmitting,
                location = pickedLocation,
                onBack = { navController.popBackStack() },
                onPickLocation = { navController.navigate(Routes.PICK_LOCATION) },
                onSubmit = { form ->
                    isSubmitting = true
                    scope.launch {
                        val result = createGame(context, form)
                        isSubmitting = false
                        when (result) {
                            is GameChangeResult.Done -> {
                                // show the new game right away, at the top of Organized Games
                                organizedGames.add(0, result.game.toGameSummary())
                                Toast.makeText(context, "Game created", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                            is GameChangeResult.Failed ->
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = modifier
            )
        }

        // the same Input Game Info screen as create, filled in with the game. Only its organizer can edit it
        composable(
            route = Routes.EDIT_GAME,
            arguments = listOf(navArgument(Routes.GAME_ID_ARG) { type = NavType.IntType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getInt(Routes.GAME_ID_ARG) ?: 0
            // saveable so the loaded game and its player count survive going to Pick Location
            var initial by rememberSaveable { mutableStateOf<GameInfoForm?>(null) }
            var playerCount by rememberSaveable { mutableIntStateOf(0) }
            var loadError by remember { mutableStateOf<String?>(null) }
            var isSubmitting by remember { mutableStateOf(false) }
            // set by the Pick Location screen, starts as the game's current place
            val pickedLocation by backStackEntry.savedStateHandle
                .getStateFlow<GameLocation?>(PICKED_LOCATION_KEY, null)
                .collectAsState()

            LaunchedEffect(gameId) {
                if (initial != null) return@LaunchedEffect
                when (val result = fetchGameDetails(context, gameId)) {
                    is GameDetailsResult.Loaded -> when {
                        result.details.viewerRole != "organizer" ->
                            loadError = "Only the organizer can edit this game."
                        result.game.status == "cancelled" -> loadError = "This game was cancelled."
                        else -> {
                            val form = runCatching { result.game.toGameInfoForm() }.getOrNull()
                            if (form == null) {
                                loadError = "Couldn't load this game, try again"
                                return@LaunchedEffect
                            }
                            playerCount = result.game.playerCount
                            if (backStackEntry.savedStateHandle.get<GameLocation>(PICKED_LOCATION_KEY) == null) {
                                backStackEntry.savedStateHandle[PICKED_LOCATION_KEY] = form.location
                            }
                            initial = form
                        }
                    }
                    GameDetailsResult.NotFound -> loadError = "This game no longer exists."
                    is GameDetailsResult.Failed -> loadError = result.message
                }
            }

            InputGameInfoScreen(
                screenTitle = "Edit Game",
                submitLabel = "Save Changes",
                initial = initial,
                minPlayers = playerCount,
                isLoading = initial == null && loadError == null,
                loadError = loadError,
                isSubmitting = isSubmitting,
                location = pickedLocation,
                onBack = { navController.popBackStack() },
                onPickLocation = { navController.navigate(Routes.PICK_LOCATION) },
                onSubmit = { form ->
                    isSubmitting = true
                    scope.launch {
                        val result = updateGame(context, gameId, form)
                        isSubmitting = false
                        when (result) {
                            // Game Details reloads when it's shown again, so it has the changes
                            is GameChangeResult.Done -> {
                                Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                            is GameChangeResult.Failed ->
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = modifier
            )
        }

        composable(Routes.PICK_LOCATION) {
            // start the map at the place already chosen on the Input Game Info screen, if any
            val currentLocation = remember {
                navController.previousBackStackEntry?.savedStateHandle?.get<GameLocation>(PICKED_LOCATION_KEY)
            }

            PickLocationScreen(
                initialLocation = currentLocation,
                onLocationPicked = { location ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_LOCATION_KEY, location)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
                modifier = modifier
            )
        }
    }
}

// opens directions to the place in Google Maps, or the browser if Maps isn't installed
private fun openDirections(context: Context, location: GameLocation) {
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1&destination=${location.latitude},${location.longitude}"
    )
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to show directions", Toast.LENGTH_SHORT).show()
    }
}
