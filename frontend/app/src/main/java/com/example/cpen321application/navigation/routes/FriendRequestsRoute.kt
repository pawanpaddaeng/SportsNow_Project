package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.friends.FriendRequestsActions
import com.example.cpen321application.ui.screens.friends.FriendRequestsScreen
import com.example.cpen321application.ui.screens.friends.FriendRequestsUiState

/*  the friend requests waiting for an answer, opened from Notifications. They're loaded and
    answered through the Friends tab's state, so accepting someone adds them to its friends
    list straight away. Reloads whenever the screen is shown
 */
@Composable
fun FriendRequestsRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val friends = app.friendsState

    OnResume(backStackEntry) { friends.refresh() }

    // a request was answered, or a new one arrived: the bell and the Friend requests button count them
    LaunchedEffect(friends.requests.size) { app.notificationsState.refresh() }

    FriendRequestsScreen(
        state = FriendRequestsUiState(
            requests = friends.requests,
            isLoading = friends.isLoading,
            errorMessage = friends.error,
            busyRequestIds = friends.busyRequestIds
        ),
        actions = FriendRequestsActions(
            onBack = { app.navController.popBackStack() },
            onRetry = { friends.refresh() },
            onAccept = { friends.answer(it, accept = true) },
            onDecline = { friends.answer(it, accept = false) },
            onOpenProfile = { userId -> app.openProfile(userId) },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}
