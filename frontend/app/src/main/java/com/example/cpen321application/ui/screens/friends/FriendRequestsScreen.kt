package com.example.cpen321application.ui.screens.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.friend.model.FriendRequestItem
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.LazyTabScreen
import com.example.cpen321application.ui.components.ListNote
import com.example.cpen321application.ui.components.loadStatus
import com.example.cpen321application.ui.theme.SportsOnSurface
import com.example.cpen321application.ui.theme.SportsOutline
import com.example.cpen321application.ui.theme.SportsOutlineVariant
import com.example.cpen321application.ui.theme.SportsPrimary

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
    val onBack: () -> Unit = {},
    val onRetry: (() -> Unit)? = null,
    val onAccept: (FriendRequestItem) -> Unit = {},
    val onDecline: (FriendRequestItem) -> Unit = {},
    val onOpenProfile: (Int) -> Unit = {},
    val onTabSelected: (BottomTab) -> Unit = {}
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

        if (state.requests.isEmpty() && !state.isLoading && state.errorMessage == null) {
            item(key = "empty") {
                ListNote("No friend requests right now. You'll get a notification when someone adds you.")
            }
        }
    }
}

// a request: who sent it, then Decline and Accept, like the Join Requests cards
@Composable
private fun RequestCard(
    request: FriendRequestItem,
    isBusy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, SportsOutlineVariant, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendAvatar(request.sender.initial, onClick = onOpenProfile)
            Text(text = request.sender.name, color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onDecline,
                enabled = !isBusy,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, SportsOutline),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = SportsPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Decline", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onAccept,
                enabled = !isBusy,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Accept", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
