package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.friends.FriendsActions
import com.example.cpen321application.ui.screens.friends.FriendsScreen
import com.example.cpen321application.ui.screens.friends.FriendsUiState

// the Friends tab, reloads whenever the screen is shown
@Composable
fun FriendsRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val friends = app.friendsState
    val searching = friends.query.isNotBlank()

    OnResume(backStackEntry) { friends.refresh() }

    FriendsScreen(
        state = FriendsUiState(
            query = friends.query,
            friends = friends.friends,
            requests = friends.requests,
            searchResults = friends.searchResults,
            isLoading = if (searching) friends.isSearching else friends.isLoading,
            errorMessage = if (searching) friends.searchError else friends.error,
            busyRequestIds = friends.busyRequestIds,
            busyUserIds = friends.busyUserIds
        ),
        actions = FriendsActions(
            onQueryChange = { friends.updateQuery(it) },
            onRetry = { if (searching) friends.retrySearch() else friends.refresh() },
            onAddFriend = { friends.addFriend(it) },
            onAccept = { friends.answer(it, accept = true) },
            onDecline = { friends.answer(it, accept = false) },
            onOpenProfile = { userId -> app.openProfile(userId) },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}
