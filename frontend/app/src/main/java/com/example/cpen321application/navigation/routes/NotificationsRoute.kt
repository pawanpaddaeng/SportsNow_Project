package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.notification.model.AppNotification
import com.example.cpen321application.notification.model.NotificationType
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.screens.notifications.NotificationsActions
import com.example.cpen321application.ui.screens.notifications.NotificationsScreen
import com.example.cpen321application.ui.screens.notifications.NotificationsUiState
import java.time.Instant

// the Notifications tab, reloads (and marks what it shows as seen) whenever the screen is shown
@Composable
fun NotificationsRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val notifications = app.notificationsState

    OnResume(backStackEntry) { notifications.refresh(markRead = true) }

    NotificationsScreen(
        state = NotificationsUiState(
            notifications = notifications.notifications,
            friendRequestCount = notifications.friendRequestCount,
            isLoading = notifications.isLoading,
            errorMessage = notifications.error,
            now = Instant.now()
        ),
        actions = NotificationsActions(
            onRetry = { notifications.refresh(markRead = true) },
            onOpenFriendRequests = { app.navController.navigate(Routes.FRIEND_REQUESTS) },
            onOpenNotification = { openNotification(app, it) },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}

/*  opens where the user can act on the notification: the organizer's Join Requests for a
    new request, the game for anything else about a game, the Friends tab for a new friend
 */
private fun openNotification(app: AppState, notification: AppNotification) {
    val gameId = notification.gameId
    when {
        notification.type == NotificationType.FriendRequestAccepted -> app.openTab(BottomTab.Friends)
        gameId == null || notification.type == NotificationType.Other -> Unit
        notification.type == NotificationType.JoinRequest -> app.navController.navigate(Routes.joinRequests(gameId))
        else -> app.navController.navigate(Routes.gameDetails(gameId))
    }
}
