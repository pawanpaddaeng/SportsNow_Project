package com.example.cpen321application.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cpen321application.core.notifications.FriendChatLink
import com.example.cpen321application.core.notifications.FriendEvents
import com.example.cpen321application.core.notifications.GameEvents
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.core.session.jwtToken
import com.example.cpen321application.navigation.routes.CreateGameRoute
import com.example.cpen321application.navigation.routes.DiscoverRoute
import com.example.cpen321application.navigation.routes.EditGameRoute
import com.example.cpen321application.navigation.routes.EditProfileRoute
import com.example.cpen321application.navigation.routes.FriendChatRoute
import com.example.cpen321application.navigation.routes.FriendsRoute
import com.example.cpen321application.navigation.routes.GameChatRoute
import com.example.cpen321application.navigation.routes.GameDetailsRoute
import com.example.cpen321application.navigation.routes.HomeRoute
import com.example.cpen321application.navigation.routes.JoinRequestsRoute
import com.example.cpen321application.navigation.routes.PickLocationRoute
import com.example.cpen321application.navigation.routes.PlayerProfilePopup
import com.example.cpen321application.navigation.routes.ProfileRoute
import com.example.cpen321application.navigation.routes.RatePlayersRoute
import com.example.cpen321application.navigation.routes.SignInRoute
import com.example.cpen321application.navigation.routes.SignUpRoute
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.navigation.state.rememberAppState

private val gameIdArgument = listOf(navArgument(Routes.GAME_ID_ARG) { type = NavType.IntType })
private val friendChatArguments = listOf(
    navArgument(Routes.CHAT_ID_ARG) { type = NavType.IntType },
    navArgument(Routes.FRIEND_NAME_ARG) { type = NavType.StringType; defaultValue = "" }
)

/*  connects each route to its screen, the shared state lives in AppState. gameToOpen is
    the game of a tapped invite notification and friendChatToOpen the chat of a tapped
    friend's message notification, their callbacks are called once they're handled
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    gameToOpen: Int? = null,
    onGameOpened: () -> Unit = {},
    friendChatToOpen: FriendChatLink? = null,
    onFriendChatOpened: () -> Unit = {}
) {
    val app = rememberAppState()
    val startDestination = remember { app.startDestination() }

    ListenForAppEvents(app)

    // signed in from a previous session: register notifications once at start
    LaunchedEffect(Unit) {
        if (startDestination == Routes.HOME) {
            app.registerNotifications()
        }
    }

    // a game invite was tapped: open the game so the friend can ask to join (if signed in)
    OpenFromNotification(gameToOpen?.let(Routes::gameDetails), onGameOpened) { app.navController.navigate(it) }
    // a friend's message was tapped: open the chat with them (if signed in)
    OpenFromNotification(friendChatToOpen?.let { Routes.friendChat(it.chatId, it.friendName) }, onFriendChatOpened) {
        app.navController.navigate(it)
    }

    NavHost(
        navController = app.navController,
        startDestination = startDestination
    ) {
        composable(Routes.SIGN_IN) { SignInRoute(app, it, modifier) }
        composable(Routes.SIGN_UP) { SignUpRoute(app, modifier) }
        composable(Routes.HOME) { HomeRoute(app, it, modifier) }
        composable(Routes.DISCOVER) { DiscoverRoute(app, it, modifier) }
        composable(Routes.FRIENDS) { FriendsRoute(app, it, modifier) }
        composable(Routes.GAME_DETAILS, arguments = gameIdArgument) { GameDetailsRoute(app, it, modifier) }
        composable(Routes.JOIN_REQUESTS, arguments = gameIdArgument) { JoinRequestsRoute(app, it, modifier) }
        composable(Routes.GAME_CHAT, arguments = gameIdArgument) { GameChatRoute(app, it, modifier) }
        composable(Routes.FRIEND_CHAT, arguments = friendChatArguments) { FriendChatRoute(app, it, modifier) }
        composable(Routes.RATE_PLAYERS, arguments = gameIdArgument) { RatePlayersRoute(app, it, modifier) }
        composable(Routes.CREATE_GAME) { CreateGameRoute(app, it, modifier) }
        composable(Routes.EDIT_GAME, arguments = gameIdArgument) { EditGameRoute(app, it, modifier) }
        composable(Routes.PICK_LOCATION) { PickLocationRoute(app, modifier) }
        composable(Routes.PROFILE) { ProfileRoute(app, it, modifier) }
        composable(Routes.EDIT_PROFILE) { EditProfileRoute(app, modifier) }
    }
    app.profileUserId?.let { userId ->
        PlayerProfilePopup(app = app, userId = userId, onDismiss = { app.closeProfile() })
    }
}

// what can happen anywhere in the app, whichever screen is showing
@Composable
private fun ListenForAppEvents(app: AppState) {
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

    // a friend request arrived or was answered while the app is open
    LaunchedEffect(Unit) {
        FriendEvents.events.collect { app.friendsState.refresh() }
    }
}

// goes to route once a tapped notification asks for it, only when signed in. onOpened clears the request
@Composable
private fun OpenFromNotification(route: String?, onOpened: () -> Unit, navigate: (String) -> Unit) {
    LaunchedEffect(route) {
        if (route != null) {
            if (jwtToken != null) navigate(route)
            onOpened()
        }
    }
}
