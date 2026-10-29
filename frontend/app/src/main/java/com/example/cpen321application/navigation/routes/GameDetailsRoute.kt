package com.example.cpen321application.navigation.routes

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.core.notifications.GameEvents
import com.example.cpen321application.core.rememberUnexpectedErrorHandler
import com.example.cpen321application.core.runGuarded
import com.example.cpen321application.game.location.hasLocationPermission
import com.example.cpen321application.game.repository.GameChangeResult
import com.example.cpen321application.game.repository.GameDetailsResult
import com.example.cpen321application.game.repository.JoinRequestDecisionResult
import com.example.cpen321application.game.repository.JoinRequestResult
import com.example.cpen321application.game.repository.PendingJoinRequestsResult
import com.example.cpen321application.game.repository.cancelGame
import com.example.cpen321application.game.repository.createGame
import com.example.cpen321application.game.repository.decideJoinRequest
import com.example.cpen321application.game.repository.fetchGameDetails
import com.example.cpen321application.game.repository.fetchPendingJoinRequests
import com.example.cpen321application.game.repository.sendJoinRequest
import com.example.cpen321application.game.repository.updateGame
import com.example.cpen321application.navigation.PICKED_LOCATION_KEY
import com.example.cpen321application.navigation.PICK_START_KEY
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.screens.discover.DiscoverActions
import com.example.cpen321application.ui.screens.discover.DiscoverScreen
import com.example.cpen321application.ui.screens.discover.DiscoverUiState
import com.example.cpen321application.ui.screens.games.GameDetails
import com.example.cpen321application.ui.screens.games.GameDetailsActions
import com.example.cpen321application.ui.screens.games.GameDetailsScreen
import com.example.cpen321application.ui.screens.games.GameDetailsUiState
import com.example.cpen321application.ui.screens.games.GameInfoForm
import com.example.cpen321application.ui.screens.games.GameLocation
import com.example.cpen321application.ui.screens.games.InputGameInfoActions
import com.example.cpen321application.ui.screens.games.InputGameInfoScreen
import com.example.cpen321application.ui.screens.games.InputGameInfoUiState
import com.example.cpen321application.ui.screens.games.JoinRequestItem
import com.example.cpen321application.ui.screens.games.JoinRequestSheet
import com.example.cpen321application.ui.screens.games.JoinRequestsActions
import com.example.cpen321application.ui.screens.games.JoinRequestsHeader
import com.example.cpen321application.ui.screens.games.JoinRequestsScreen
import com.example.cpen321application.ui.screens.games.JoinRequestsUiState
import com.example.cpen321application.ui.screens.games.PickLocationScreen
import com.example.cpen321application.ui.screens.games.toGameDetails
import com.example.cpen321application.ui.screens.games.toGameInfoForm
import com.example.cpen321application.ui.screens.games.toJoinRequestItem
import com.example.cpen321application.ui.screens.games.toJoinRequestsHeader
import com.example.cpen321application.ui.screens.home.HomeActions
import com.example.cpen321application.ui.screens.home.HomeScreen
import com.example.cpen321application.ui.screens.home.HomeUiState
import com.example.cpen321application.ui.screens.home.toGameSummary
import kotlinx.coroutines.launch

// the game id of a route like game/{gameId}
private fun NavBackStackEntry.gameId(): Int = arguments?.getInt(Routes.GAME_ID_ARG) ?: 0

@Composable
fun HomeRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    OnResume(backStackEntry) { app.refreshGames() }

    HomeScreen(
        state = HomeUiState(
            joinedGames = app.joinedGames,
            organizedGames = app.organizedGames,
            isRefreshing = app.isRefreshingGames,
            errorMessage = app.gamesError
        ),
        actions = HomeActions(
            onRefresh = { app.refreshGames() },
            onCreateGame = { app.navController.navigate(Routes.CREATE_GAME) },
            onGameClick = { game ->
                game.id.toIntOrNull()?.let { app.navController.navigate(Routes.gameDetails(it)) }
            },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}

@Composable
fun DiscoverRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    // only ask once per visit, the system dialog pauses and resumes this screen
    var askedForPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            app.loadDiscover(refreshLocation = true)
        } else {
            app.discoverError = "SportsNow needs your location to find games near you. " +
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
        if (app.discoverPlace != null || hasLocationPermission(context)) {
            app.loadDiscover(refreshLocation = true)
        } else {
            askForPermission()
        }
    }

    // Pick Location starts at the picked place, or at the user's location
    fun openPickLocation() {
        val start = app.discoverPlace ?: app.discoverLocation?.let {
            GameLocation(name = "Your location", address = "", latitude = it.latitude, longitude = it.longitude)
        }
        backStackEntry.savedStateHandle[PICK_START_KEY] = start
        app.navController.navigate(Routes.PICK_LOCATION)
    }

    /*  reload whenever the screen is shown, e.g. coming back after sending a join
        request. Coming back from Pick Location with a new place searches there,
        going back without confirming keeps the current place
     */
    OnResume(backStackEntry) {
        val handle = backStackEntry.savedStateHandle
        val start = handle.remove<GameLocation>(PICK_START_KEY)
        val picked = handle.remove<GameLocation>(PICKED_LOCATION_KEY)
        if (picked != null && picked != start) app.discoverPlace = picked
        when {
            app.discoverPlace != null || hasLocationPermission(context) -> app.loadDiscover(refreshLocation = false)
            !askedForPermission -> askForPermission()
        }
    }

    DiscoverScreen(
        state = DiscoverUiState(
            games = app.discoverGames,
            location = app.discoverLocation,
            place = app.discoverPlace,
            hasLoaded = app.hasLoadedDiscover,
            isLoading = app.isLoadingDiscover,
            errorMessage = app.discoverError
        ),
        actions = DiscoverActions(
            onRetry = { reloadWithNewLocation() },
            onRefresh = { reloadWithNewLocation() },
            onChangeLocation = { openPickLocation() },
            onUseMyLocation = {
                app.discoverPlace = null
                reloadWithNewLocation()
            },
            onGameClick = { game -> app.navController.navigate(Routes.gameDetails(game.gameId)) },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}

@Composable
fun GameDetailsRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val navController = app.navController
    val gameId = backStackEntry.gameId()
    var details by remember { mutableStateOf<GameDetails?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var showJoinSheet by remember { mutableStateOf(false) }
    // opened from a Discover card: highlight Discover and go back there after a join request
    val openedFromDiscover = remember {
        navController.previousBackStackEntry?.destination?.route == Routes.DISCOVER
    }

    // on failure the last loaded details stay visible with the error above them
    fun loadDetails() {
        if (isLoading) return
        isLoading = true
        app.scope.launch {
            try {
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
            } finally {
                isLoading = false
            }
        }
    }

    // the screen asks the organizer to confirm before calling this
    fun cancel() {
        isCancelling = true
        app.scope.launch {
            val result = try {
                cancelGame(context, gameId)
            } finally {
                isCancelling = false
            }
            when (result) {
                is GameChangeResult.Done -> {
                    Toast.makeText(context, "Game cancelled", Toast.LENGTH_SHORT).show()
                    loadDetails()
                }
                is GameChangeResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // reload whenever the screen is shown, e.g. coming back after editing the game
    OnResume(backStackEntry) { loadDetails() }

    // a notification about this game arrived (new request, approval, edit, ...)
    LaunchedEffect(gameId) {
        GameEvents.events.collect { event -> if (event.gameId == gameId) loadDetails() }
    }

    GameDetailsScreen(
        state = GameDetailsUiState(
            details = details,
            isLoading = isLoading,
            errorMessage = error,
            isCancelling = isCancelling,
            selectedTab = if (openedFromDiscover) BottomTab.Discover else BottomTab.Home
        ),
        actions = GameDetailsActions(
            onBack = { navController.popBackStack() },
            onRetry = if (notFound) null else ({ loadDetails() }),
            onEdit = { navController.navigate(Routes.editGame(gameId)) },
            onDirections = { location -> openDirections(context, location) },
            onJoinRequests = { navController.navigate(Routes.joinRequests(gameId)) },
            onCancelGame = { cancel() },
            // placeholder until leaving a game is built
            onLeaveGame = { Toast.makeText(context, "Leaving a game isn't available yet", Toast.LENGTH_SHORT).show() },
            onRequestToJoin = { showJoinSheet = true },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )

    val shownDetails = details
    if (showJoinSheet && shownDetails != null) {
        JoinRequestPopup(
            app = app,
            details = shownDetails,
            onDismiss = { showJoinSheet = false },
            onSent = {
                showJoinSheet = false
                // Discover reloads when shown, so the card says "Request pending"
                if (openedFromDiscover) navController.popBackStack() else loadDetails()
            }
        )
    }
}

/*  the join request popup for a game: sends the answers to its organizer and shows
    the server's problem in the popup if the request can't be made
 */
@Composable
private fun JoinRequestPopup(app: AppState, details: GameDetails, onDismiss: () -> Unit, onSent: () -> Unit) {
    val context = LocalContext.current
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    JoinRequestSheet(
        details = details,
        isSending = isSending,
        errorMessage = error,
        onDismiss = onDismiss,
        onSend = { answers ->
            isSending = true
            error = null
            app.scope.launch {
                val result = try {
                    sendJoinRequest(
                        context = context,
                        gameId = details.gameId,
                        skillLevel = answers.skillLevel,
                        playFrequency = answers.playFrequency,
                        message = answers.message
                    )
                } finally {
                    isSending = false
                }
                when (result) {
                    is JoinRequestResult.Sent -> {
                        Toast.makeText(context, "Request sent", Toast.LENGTH_SHORT).show()
                        onSent()
                    }
                    is JoinRequestResult.Failed -> error = result.message
                }
            }
        }
    )
}

// the organizer reviews who asked to join, opened from the Game Details banner
@Composable
fun JoinRequestsRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val gameId = backStackEntry.gameId()
    var header by remember { mutableStateOf<JoinRequestsHeader?>(null) }
    val requests = remember { mutableStateListOf<JoinRequestItem>() }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // the game is gone or the user isn't its organizer, retrying won't help
    var unavailable by remember { mutableStateOf(false) }
    var busyRequestIds by remember { mutableStateOf(emptySet<Int>()) }

    // on failure the last loaded requests stay visible with the error above them
    fun loadRequests() {
        if (isLoading) return
        isLoading = true
        app.scope.launch {
            try {
                when (val result = fetchPendingJoinRequests(context, gameId)) {
                    is PendingJoinRequestsResult.Loaded -> {
                        header = result.game.toJoinRequestsHeader()
                        requests.clear()
                        requests.addAll(result.requests.map { it.toJoinRequestItem() })
                        error = null
                    }
                    is PendingJoinRequestsResult.Unavailable -> {
                        header = null
                        requests.clear()
                        error = result.message
                        unavailable = true
                    }
                    is PendingJoinRequestsResult.Failed -> error = result.message
                }
            } finally {
                isLoading = false
            }
        }
    }

    // sends the organizer's answer, the card goes away once the server saved it
    fun decide(request: JoinRequestItem, approve: Boolean) {
        busyRequestIds = busyRequestIds + request.requestId
        app.scope.launch {
            val result = try {
                decideJoinRequest(context, gameId, request.requestId, approve)
            } finally {
                busyRequestIds = busyRequestIds - request.requestId
            }
            when (result) {
                is JoinRequestDecisionResult.Done -> {
                    requests.removeAll { it.requestId == request.requestId }
                    header = result.game.toJoinRequestsHeader()
                    val text = if (approve) "${request.name} was added to the game" else "Request declined"
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                is JoinRequestDecisionResult.Failed -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    // e.g. the requester cancelled or the game filled up meanwhile
                    loadRequests()
                }
            }
        }
    }

    // reload whenever the screen is shown, e.g. coming back from the app's background
    OnResume(backStackEntry) { loadRequests() }

    // live updates: someone asked to join this game while the list is open
    LaunchedEffect(gameId) {
        GameEvents.events.collect { event ->
            if (event.gameId == gameId && event.type == "join_request") loadRequests()
        }
    }

    JoinRequestsScreen(
        state = JoinRequestsUiState(
            header = header,
            requests = requests,
            isLoading = isLoading,
            errorMessage = error,
            busyRequestIds = busyRequestIds
        ),
        actions = JoinRequestsActions(
            onBack = { app.navController.popBackStack() },
            onRetry = if (unavailable) null else ({ loadRequests() }),
            onApprove = { request -> decide(request, approve = true) },
            onDecline = { request -> decide(request, approve = false) },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}

@Composable
fun CreateGameRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val navController = app.navController
    var isSubmitting by remember { mutableStateOf(false) }
    // set by the Pick Location screen when the user confirms a place
    val pickedLocation by backStackEntry.savedStateHandle
        .getStateFlow<GameLocation?>(PICKED_LOCATION_KEY, null)
        .collectAsState()

    fun submit(form: GameInfoForm) {
        isSubmitting = true
        app.scope.launch {
            val result = try {
                createGame(context, form)
            } finally {
                isSubmitting = false
            }
            when (result) {
                is GameChangeResult.Done -> {
                    // show the new game right away, at the top of Organized Games
                    app.organizedGames.add(0, result.game.toGameSummary())
                    Toast.makeText(context, "Game created", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is GameChangeResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    InputGameInfoScreen(
        state = InputGameInfoUiState(isSubmitting = isSubmitting, location = pickedLocation),
        actions = InputGameInfoActions(
            onSubmit = { form -> submit(form) },
            onBack = { navController.popBackStack() },
            onPickLocation = { navController.navigate(Routes.PICK_LOCATION) }
        ),
        modifier = modifier
    )
}

// the same Input Game Info screen as create, filled in with the game. Only its organizer can edit it
@Composable
fun EditGameRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val navController = app.navController
    val safetyNet = rememberUnexpectedErrorHandler()
    val gameId = backStackEntry.gameId()
    // saveable so the loaded game and its player count survive going to Pick Location
    var initial by rememberSaveable { mutableStateOf<GameInfoForm?>(null) }
    var playerCount by rememberSaveable { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    // set by the Pick Location screen, starts as the game's current place
    val pickedLocation by backStackEntry.savedStateHandle
        .getStateFlow<GameLocation?>(PICKED_LOCATION_KEY, null)
        .collectAsState()

    // loads the game into the form, or sets loadError when it can't be edited
    suspend fun loadGame() {
        when (val game = loadEditableGame(context, gameId)) {
            is EditableGame.Loaded -> {
                playerCount = game.playerCount
                if (backStackEntry.savedStateHandle.get<GameLocation>(PICKED_LOCATION_KEY) == null) {
                    backStackEntry.savedStateHandle[PICKED_LOCATION_KEY] = game.form.location
                }
                initial = game.form
            }
            is EditableGame.Unavailable -> loadError = game.message
        }
    }

    fun submit(form: GameInfoForm) {
        isSubmitting = true
        app.scope.launch {
            val result = try {
                updateGame(context, gameId, form)
            } finally {
                isSubmitting = false
            }
            when (result) {
                // Game Details reloads when it's shown again, so it has the changes
                is GameChangeResult.Done -> {
                    Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
                is GameChangeResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // loads the game once, it's kept (saveable) while going to Pick Location and back
    LaunchedEffect(gameId) {
        if (initial == null) {
            runGuarded(safetyNet) { loadGame() }
            // nothing loaded (an unexpected error): show a message instead of loading forever
            if (initial == null && loadError == null) loadError = COULD_NOT_LOAD_GAME
        }
    }

    InputGameInfoScreen(
        state = InputGameInfoUiState(
            screenTitle = "Edit Game",
            submitLabel = "Save Changes",
            initial = initial,
            minPlayers = playerCount,
            isSubmitting = isSubmitting,
            isLoading = initial == null && loadError == null,
            loadError = loadError,
            location = pickedLocation
        ),
        actions = InputGameInfoActions(
            onSubmit = { form -> submit(form) },
            onBack = { navController.popBackStack() },
            onPickLocation = { navController.navigate(Routes.PICK_LOCATION) }
        ),
        modifier = modifier
    )
}

private const val COULD_NOT_LOAD_GAME = "Couldn't load this game, try again"

// the game to edit as the form's starting values, or why it can't be edited
private sealed interface EditableGame {
    data class Loaded(val form: GameInfoForm, val playerCount: Int) : EditableGame
    data class Unavailable(val message: String) : EditableGame
}

// loads a game for the edit screen: only its organizer can edit it, and not once it's cancelled
private suspend fun loadEditableGame(context: Context, gameId: Int): EditableGame =
    when (val result = fetchGameDetails(context, gameId)) {
        is GameDetailsResult.Loaded -> when {
            result.details.viewerRole != "organizer" -> EditableGame.Unavailable("Only the organizer can edit this game.")
            result.game.status == "cancelled" -> EditableGame.Unavailable("This game was cancelled.")
            // the form is null when the game's values can't fill it
            else -> runCatching { result.game.toGameInfoForm() }.getOrNull()
                ?.let { EditableGame.Loaded(form = it, playerCount = result.game.playerCount) }
                ?: EditableGame.Unavailable(COULD_NOT_LOAD_GAME)
        }
        GameDetailsResult.NotFound -> EditableGame.Unavailable("This game no longer exists.")
        is GameDetailsResult.Failed -> EditableGame.Unavailable(result.message)
    }

@Composable
fun PickLocationRoute(app: AppState, modifier: Modifier) {
    val navController = app.navController
    // start the map where the previous screen asked (Discover), otherwise at the
    // place already chosen on the Input Game Info screen, if any
    val currentLocation = remember {
        val handle = navController.previousBackStackEntry?.savedStateHandle
        handle?.get<GameLocation>(PICK_START_KEY) ?: handle?.get<GameLocation>(PICKED_LOCATION_KEY)
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

// opens directions to the place in Google Maps, or the browser if Maps isn't installed
private fun openDirections(context: Context, location: GameLocation) {
    val uri =
        "https://www.google.com/maps/dir/?api=1&destination=${location.latitude},${location.longitude}".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to show directions", Toast.LENGTH_SHORT).show()
    }
}
