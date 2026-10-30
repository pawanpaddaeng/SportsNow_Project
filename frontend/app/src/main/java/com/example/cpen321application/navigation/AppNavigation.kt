package com.example.cpen321application.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cpen321application.core.notifications.GameEvents
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.navigation.routes.CreateGameRoute
import com.example.cpen321application.navigation.routes.DiscoverRoute
import com.example.cpen321application.navigation.routes.EditGameRoute
import com.example.cpen321application.navigation.routes.EditProfileRoute
import com.example.cpen321application.navigation.routes.GameDetailsRoute
import com.example.cpen321application.navigation.routes.HomeRoute
import com.example.cpen321application.navigation.routes.JoinRequestsRoute
import com.example.cpen321application.navigation.routes.PickLocationRoute
import com.example.cpen321application.navigation.routes.ProfileRoute
import com.example.cpen321application.navigation.routes.SignInRoute
import com.example.cpen321application.navigation.routes.SignUpRoute
import com.example.cpen321application.navigation.state.rememberAppState

private val gameIdArgument = listOf(navArgument(Routes.GAME_ID_ARG) { type = NavType.IntType })

// connects each route to its screen, the shared state lives in AppState
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val app = rememberAppState()
    val startDestination = remember { app.startDestination() }

    // any API call whose session can't be used or renewed asks for sign in through Session
    LaunchedEffect(Unit) {
        Session.signInRequired.collect { app.returnToSignIn() }
    }

    // a join request was approved, or the user was removed from a game, while the app is
    // open: the game now belongs on Home, or no longer does
    LaunchedEffect(Unit) {
        GameEvents.events.collect { event ->
            if (event.type == "join_request_approved" || event.type == "removed_from_game") app.homeState.refresh()
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
