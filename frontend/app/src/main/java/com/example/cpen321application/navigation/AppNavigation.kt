package com.example.cpen321application.navigation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.cpen321application.games.DiscoverGamesResult
import com.example.cpen321application.games.GameChangeResult
import com.example.cpen321application.games.GameDetailsResult
import com.example.cpen321application.games.JoinRequestDecisionResult
import com.example.cpen321application.games.JoinRequestResult
import com.example.cpen321application.games.MyGamesResult
import com.example.cpen321application.games.PendingJoinRequestsResult
import com.example.cpen321application.games.cancelGame
import com.example.cpen321application.games.createGame
import com.example.cpen321application.games.decideJoinRequest
import com.example.cpen321application.games.fetchPendingJoinRequests
import com.example.cpen321application.games.updateGame
import com.example.cpen321application.games.fetchDiscoverGames
import com.example.cpen321application.games.fetchGameDetails
import com.example.cpen321application.games.fetchMyGames
import com.example.cpen321application.games.sendJoinRequest
import com.example.cpen321application.location.DeviceLocation
import com.example.cpen321application.location.currentDeviceLocation
import com.example.cpen321application.location.hasLocationPermission
import com.example.cpen321application.location.isLocationEnabled
import com.example.cpen321application.notifications.GameEvents
import com.example.cpen321application.notifications.NotificationRegistration
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.screens.auth.SignInScreen
import com.example.cpen321application.ui.screens.auth.SignUpScreen
import com.example.cpen321application.ui.screens.discover.DiscoverGame
import com.example.cpen321application.ui.screens.discover.DiscoverScreen
import com.example.cpen321application.ui.screens.discover.toDiscoverGame
import com.example.cpen321application.ui.screens.games.GameDetails
import com.example.cpen321application.ui.screens.games.GameDetailsScreen
import com.example.cpen321application.ui.screens.games.GameInfoForm
import com.example.cpen321application.ui.screens.games.InputGameInfoScreen
import com.example.cpen321application.ui.screens.games.GameLocation
import com.example.cpen321application.ui.screens.games.JoinRequestItem
import com.example.cpen321application.ui.screens.games.JoinRequestSheet
import com.example.cpen321application.ui.screens.games.JoinRequestsHeader
import com.example.cpen321application.ui.screens.games.JoinRequestsScreen
import com.example.cpen321application.ui.screens.games.toJoinRequestItem
import com.example.cpen321application.ui.screens.games.toJoinRequestsHeader
import com.example.cpen321application.ui.screens.games.toGameDetails
import com.example.cpen321application.ui.screens.games.toGameInfoForm
import com.example.cpen321application.ui.screens.games.PickLocationScreen
import com.example.cpen321application.ui.screens.home.GameSummary
import com.example.cpen321application.ui.screens.home.HomeScreen
import com.example.cpen321application.ui.screens.home.toGameSummary
import kotlinx.coroutines.launch
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.screens.profile.ProfileScreen
import com.example.cpen321application.users.ProfileResult
import com.example.cpen321application.users.UserProfile
import com.example.cpen321application.users.fetchMyProfile
import com.example.cpen321application.ui.screens.profile.EditProfileScreen
import com.example.cpen321application.users.ProfileForm
import com.example.cpen321application.users.updateMyProfile

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
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }

    fun refreshProfile() {
        if (isLoadingProfile) return
        isLoadingProfile = true
        scope.launch {
            when (val result = fetchMyProfile(context)) {
                is ProfileResult.Loaded -> {
                    profile = result.profile
                    profileError = null
                }
                is ProfileResult.Failed -> profileError = result.message
            }
            isLoadingProfile = false
        }
    }

    var isSavingProfile by remember { mutableStateOf(false) }
    var saveProfileError by remember { mutableStateOf<String?>(null) }

    fun saveProfile(form: ProfileForm) {
        if (isSavingProfile) return
        isSavingProfile = true
        saveProfileError = null
        scope.launch {
            when (val result = updateMyProfile(context, form)) {
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
            isSavingProfile = false
        }
    }

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

    // games shown on Discover and the location they were loaded for. Kept here like the
    // Home games so they stay visible while a refresh loads and after opening a game
    val discoverGames = remember { mutableStateListOf<DiscoverGame>() }
    var discoverLocation by remember { mutableStateOf<DeviceLocation?>(null) }
    var hasLoadedDiscover by remember { mutableStateOf(false) }
    var isLoadingDiscover by remember { mutableStateOf(false) }
    var discoverError by remember { mutableStateOf<String?>(null) }
    // place the user picked on the map to search around, null searches around the phone
    var discoverPlace by remember { mutableStateOf<GameLocation?>(null) }

    /*  loads the games near the picked place, or near the user when none is picked.
        The phone's location is only looked up again when refreshLocation is set (or
        none is known yet), to save battery. On failure the last loaded games stay
     */
    fun loadDiscover(refreshLocation: Boolean) {
        if (isLoadingDiscover) return
        isLoadingDiscover = true
        scope.launch {
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
                isLoadingDiscover = false
                return@launch
            }
            discoverLocation = location
            when (val result = fetchDiscoverGames(context, location.latitude, location.longitude)) {
                is DiscoverGamesResult.Loaded -> {
                    discoverGames.clear()
                    discoverGames.addAll(result.games.map { it.toDiscoverGame() })
                    hasLoadedDiscover = true
                    discoverError = null
                }
                is DiscoverGamesResult.Failed -> discoverError = result.message
            }
            isLoadingDiscover = false
        }
    }

    // bottom bar: Home and Discover are built, the other tabs aren't yet
    fun openTab(tab: BottomTab) {
        when (tab) {
            BottomTab.Home -> navController.popBackStack(Routes.HOME, inclusive = false)
            // Home stays underneath so back from Discover returns to it
            BottomTab.Discover -> navController.navigate(Routes.DISCOVER) {
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
        saveProfileError=null
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

    // a join request was approved while the app is open: the game now belongs on Home
    LaunchedEffect(Unit) {
        GameEvents.events.collect { event ->
            if (event.type == "join_request_approved") refreshGames()
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
                onTabSelected = { tab-> openTab(tab)}
                onCreateGame = { navController.navigate(Routes.CREATE_GAME) },
                onGameClick = { game ->
                    game.id.toIntOrNull()?.let { navController.navigate(Routes.gameDetails(it)) }
                },
                onTabSelected = { tab -> openTab(tab) }
            )
        }

        composable(Routes.DISCOVER) { backStackEntry ->
            // only ask once per visit, the system dialog pauses and resumes this screen
            var askedForPermission by remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                if (results.values.any { it }) {
                    loadDiscover(refreshLocation = true)
                } else {
                    discoverError = "SportsNow needs your location to find games near you. " +
                        "Allow location access, then try again."
                }
            }

            fun askForPermission() {
                askedForPermission = true
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }

            /*  reload whenever the screen is shown, e.g. coming back after sending a join
                request. Coming back from Pick Location with a new place searches there,
                going back without confirming keeps the current place
             */
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
                    val handle = backStackEntry.savedStateHandle
                    val start = handle.remove<GameLocation>(PICK_START_KEY)
                    val picked = handle.remove<GameLocation>(PICKED_LOCATION_KEY)
                    if (picked != null && picked != start) discoverPlace = picked
                    when {
                        discoverPlace != null || hasLocationPermission(context) -> loadDiscover(refreshLocation = false)
                        !askedForPermission -> askForPermission()
                    }
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }

            // a new location only when the user asks for it, otherwise the last one is reused
            fun reloadWithNewLocation() {
                if (discoverPlace != null || hasLocationPermission(context)) {
                    loadDiscover(refreshLocation = true)
                } else {
                    askForPermission()
                }
            }

            // Pick Location starts at the picked place, or at the user's location
            fun openPickLocation() {
                val start = discoverPlace ?: discoverLocation?.let {
                    GameLocation(name = "Your location", address = "", latitude = it.latitude, longitude = it.longitude)
                }
                backStackEntry.savedStateHandle[PICK_START_KEY] = start
                navController.navigate(Routes.PICK_LOCATION)
            }

            DiscoverScreen(
                games = discoverGames,
                location = discoverLocation,
                place = discoverPlace,
                onChangeLocation = { openPickLocation() },
                onUseMyLocation = {
                    discoverPlace = null
                    reloadWithNewLocation()
                },
                hasLoaded = hasLoadedDiscover,
                isLoading = isLoadingDiscover,
                errorMessage = discoverError,
                onRetry = { reloadWithNewLocation() },
                onRefresh = { reloadWithNewLocation() },
                onGameClick = { game -> navController.navigate(Routes.gameDetails(game.gameId)) },
                onTabSelected = { tab -> openTab(tab) },
                modifier = modifier
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
            var showJoinSheet by remember { mutableStateOf(false) }
            var isSendingRequest by remember { mutableStateOf(false) }
            var joinRequestError by remember { mutableStateOf<String?>(null) }
            // opened from a Discover card: highlight Discover and go back there after a join request
            val openedFromDiscover = remember {
                navController.previousBackStackEntry?.destination?.route == Routes.DISCOVER
            }

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

            // a notification about this game arrived (new request, approval, edit, ...)
            LaunchedEffect(gameId) {
                GameEvents.events.collect { event -> if (event.gameId == gameId) loadDetails() }
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
                onJoinRequests = { navController.navigate(Routes.joinRequests(gameId)) },
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
                onRequestToJoin = {
                    joinRequestError = null
                    showJoinSheet = true
                },
                selectedTab = if (openedFromDiscover) BottomTab.Discover else BottomTab.Home,
                onTabSelected = { tab -> openTab(tab) },
                modifier = modifier
            )

            val shownDetails = details
            if (showJoinSheet && shownDetails != null) {
                JoinRequestSheet(
                    details = shownDetails,
                    isSending = isSendingRequest,
                    errorMessage = joinRequestError,
                    onDismiss = { showJoinSheet = false },
                    onSend = { answers ->
                        isSendingRequest = true
                        joinRequestError = null
                        scope.launch {
                            val result = sendJoinRequest(
                                context = context,
                                gameId = gameId,
                                skillLevel = answers.skillLevel,
                                playFrequency = answers.playFrequency,
                                message = answers.message
                            )
                            isSendingRequest = false
                            when (result) {
                                is JoinRequestResult.Sent -> {
                                    showJoinSheet = false
                                    Toast.makeText(context, "Request sent", Toast.LENGTH_SHORT).show()
                                    // Discover reloads when shown, so the card says "Request pending"
                                    if (openedFromDiscover) navController.popBackStack() else loadDetails()
                                }
                                is JoinRequestResult.Failed -> joinRequestError = result.message
                            }
                        }
                    }
                )
            }
        }

        // the organizer reviews who asked to join, opened from the Game Details banner
        composable(
            route = Routes.JOIN_REQUESTS,
            arguments = listOf(navArgument(Routes.GAME_ID_ARG) { type = NavType.IntType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getInt(Routes.GAME_ID_ARG) ?: 0
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
                scope.launch {
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
                    isLoading = false
                }
            }

            // sends the organizer's answer, the card goes away once the server saved it
            fun decide(request: JoinRequestItem, approve: Boolean) {
                busyRequestIds = busyRequestIds + request.requestId
                scope.launch {
                    val result = decideJoinRequest(context, gameId, request.requestId, approve)
                    busyRequestIds = busyRequestIds - request.requestId
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
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) loadRequests()
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }

            // live updates: someone asked to join this game while the list is open
            LaunchedEffect(gameId) {
                GameEvents.events.collect { event ->
                    if (event.gameId == gameId && event.type == "join_request") loadRequests()
                }
            }

            JoinRequestsScreen(
                header = header,
                requests = requests,
                isLoading = isLoading,
                errorMessage = error,
                busyRequestIds = busyRequestIds,
                onRetry = if (unavailable) null else ({ loadRequests() }),
                onBack = { navController.popBackStack() },
                onApprove = { request -> decide(request, approve = true) },
                onDecline = { request -> decide(request, approve = false) },
                onTabSelected = { tab -> openTab(tab) },
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

        composable(Routes.PROFILE) { backStackEntry ->
            DisposableEffect(backStackEntry) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) refreshProfile()
                }
                backStackEntry.lifecycle.addObserver(observer)
                onDispose { backStackEntry.lifecycle.removeObserver(observer) }
            }

            ProfileScreen(
                profile = profile,
                modifier = modifier,
                isLoading = isLoadingProfile,
                errorMessage = profileError,
                onEdit = {
                    saveProfileError = null
                    navController.navigate(Routes.EDIT_PROFILE)
                },
                onRetry = { refreshProfile() },
                onTabSelected = { tab -> openTab(tab) }
            )
        }

        composable(Routes.EDIT_PROFILE) {
            val current = profile
            if (current == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                EditProfileScreen(
                    profile = current,
                    onSave = { form -> saveProfile(form) },
                    onCancel = { navController.popBackStack() },
                    modifier = modifier,
                    isSaving = isSavingProfile,
                    errorMessage = saveProfileError
                )
            }
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
