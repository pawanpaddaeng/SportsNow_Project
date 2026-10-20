package com.example.cpen321application.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cpen321application.auth.Session
import com.example.cpen321application.notifications.GameEvents

// the {gameId} argument of the game routes
private val gameIdArgument = listOf(navArgument(Routes.GAME_ID_ARG) { type = NavType.IntType })

/*
    App navigation composable:
    Deals with the screens in ui/screens and handles
    their interactions with each other, such as switching.
    The shared state lives in AppState, each route's screen in AccountRoutes and GameRoutes
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val app = rememberAppState()
    val startDestination = remember { app.startDestination() }

    // any API call whose session can't be used or renewed asks for sign in through Session
    LaunchedEffect(Unit) {
        Session.signInRequired.collect { app.returnToSignIn() }
    }

    // a join request was approved while the app is open: the game now belongs on Home
    LaunchedEffect(Unit) {
        GameEvents.events.collect { event ->
            if (event.type == "join_request_approved") app.refreshGames()
        }
    }

    // signed in from a previous session: register notifications once at start
    LaunchedEffect(Unit) {
        if (startDestination == Routes.HOME) {
            app.registerNotifications()
        }
    }

    NavHost(
        navController = app.navController,
        startDestination = startDestination
    ) {
        composable(Routes.SIGN_IN) { SignInRoute(app, it, modifier) }
        composable(Routes.SIGN_UP) { SignUpRoute(app, modifier) }
        composable(Routes.HOME) { HomeRoute(app, it, modifier) }
        composable(Routes.DISCOVER) { DiscoverRoute(app, it, modifier) }
        composable(Routes.GAME_DETAILS, arguments = gameIdArgument) { GameDetailsRoute(app, it, modifier) }
        composable(Routes.JOIN_REQUESTS, arguments = gameIdArgument) { JoinRequestsRoute(app, it, modifier) }
        composable(Routes.CREATE_GAME) { CreateGameRoute(app, it, modifier) }
        composable(Routes.EDIT_GAME, arguments = gameIdArgument) { EditGameRoute(app, it, modifier) }
        composable(Routes.PICK_LOCATION) { PickLocationRoute(app, modifier) }
        composable(Routes.PROFILE) { ProfileRoute(app, it, modifier) }
        composable(Routes.EDIT_PROFILE) { EditProfileRoute(app, modifier) }
    }
}
