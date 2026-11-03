package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.cpen321application.friend.model.FriendshipStatus
import com.example.cpen321application.friend.repository.FriendResult
import com.example.cpen321application.friend.repository.sendFriendRequest
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.components.PlayerProfileDialog
import com.example.cpen321application.ui.components.PlayerProfileUiState
import com.example.cpen321application.user.model.PlayerProfile
import com.example.cpen321application.user.repository.PublicProfileResult
import com.example.cpen321application.user.repository.fetchPublicProfile
import kotlinx.coroutines.launch

/*  the profile popup for userId, shared by every screen that lists players: loads their
    public profile and sends a friend request from it. Closing it leaves the screen as it was
 */
@Composable
fun PlayerProfilePopup(app: AppState, userId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var state by remember(userId) { mutableStateOf(PlayerProfileUiState(isLoading = true)) }

    fun load() {
        state = PlayerProfileUiState(isLoading = true)
        app.scope.launch {
            val result = try {
                fetchPublicProfile(context, userId)
            } finally {
                state = state.copy(isLoading = false)
            }
            state = when (result) {
                is PublicProfileResult.Loaded -> state.copy(profile = result.profile)
                is PublicProfileResult.Failed -> state.copy(errorMessage = result.message, canRetry = result.canRetry)
            }
        }
    }

    // on success the button turns into "Friend request sent", the server notifies them
    fun addFriend(profile: PlayerProfile) {
        state = state.copy(isAddingFriend = true, errorMessage = null)
        app.scope.launch {
            val result = try {
                sendFriendRequest(context, profile.userId)
            } finally {
                state = state.copy(isAddingFriend = false)
            }
            state = when (result) {
                is FriendResult.Done -> state.copy(profile = profile.copy(friendship = FriendshipStatus.RequestSent))
                is FriendResult.Failed -> state.copy(errorMessage = result.message)
            }
        }
    }

    LaunchedEffect(userId) { load() }

    PlayerProfileDialog(
        state = state,
        onRetry = { load() },
        onAddFriend = { state.profile?.let { addFriend(it) } },
        onDismiss = onDismiss
    )
}