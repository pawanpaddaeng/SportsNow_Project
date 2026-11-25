package com.example.cpen321application.ui.screens.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.friend.model.FriendRequestItem
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.DecisionButtons
import com.example.cpen321application.ui.components.LazyTabScreen
import com.example.cpen321application.ui.components.RequestCardFrame
import com.example.cpen321application.ui.components.loadStatus
import com.example.cpen321application.ui.components.emptyListNote
import com.example.cpen321application.ui.components.noAction
import com.example.cpen321application.ui.components.noActionWith
import com.example.cpen321application.ui.theme.SportsOnSurface

/*  what the Friend Requests screen shows. isLoading shows a loading bar and errorMessage a
    problem. busyRequestIds are answers being sent, their buttons are disabled
 */
data class FriendRequestsUiState(
    val requests: List<FriendRequestItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val busyRequestIds: Set<Int> = emptySet()
)

// what the Friend Requests screen's controls do
data class FriendRequestsActions(
    val onBack: () -> Unit = noAction,
    val onRetry: (() -> Unit)? = null,
    val onAccept: (FriendRequestItem) -> Unit = noActionWith,
    val onDecline: (FriendRequestItem) -> Unit = noActionWith,
    val onOpenProfile: (Int) -> Unit = noActionWith,
    val onTabSelected: (BottomTab) -> Unit = noActionWith
)

/*
    Friend Requests screen, opened from the button at the top of Notifications (like
    Instagram's): the requests sent to the user that are waiting for an answer, newest
    first, each with Decline and Accept. The sender is only told when it's accepted
 */
@Composable
fun FriendRequestsScreen(state: FriendRequestsUiState, actions: FriendRequestsActions, modifier: Modifier = Modifier) {
    LazyTabScreen(selectedTab = BottomTab.Notifications, onTabSelected = actions.onTabSelected, modifier = modifier) {
        item(key = "top") { BackTitleBar(title = "Friend Requests", onBack = actions.onBack) }

        loadStatus(isLoading = state.isLoading, errorMessage = state.errorMessage, onRetry = actions.onRetry)

        items(state.requests, key = { "request${it.requestId}" }) { request ->
            RequestCard(
                request = request,
                isBusy = request.requestId in state.busyRequestIds,
                onAccept = { actions.onAccept(request) },
                onDecline = { actions.onDecline(request) },
                onOpenProfile = { actions.onOpenProfile(request.sender.userId) }
            )
        }

        emptyListNote(
            state.requests, state.isLoading, state.errorMessage, key = "empty",
            text = "No friend requests right now. You'll get a notification when someone adds you."
        )
    }
}

// tag on every request card, so E2E tests can find one person's request among others
const val FRIEND_REQUEST_CARD_TAG = "friendRequestCard"

// a request: who sent it, then Decline and Accept, like the Join Requests cards
@Composable
private fun RequestCard(
    request: FriendRequestItem,
    isBusy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenProfile: () -> Unit
) {
    RequestCardFrame(modifier = Modifier.testTag(FRIEND_REQUEST_CARD_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendAvatar(request.sender.initial, onClick = onOpenProfile)
            Text(text = request.sender.name, color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        DecisionButtons(acceptLabel = "Accept", isBusy = isBusy, onAccept = onAccept, onDecline = onDecline)
    }
}
