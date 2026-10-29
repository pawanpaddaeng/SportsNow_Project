package com.example.cpen321application.navigation.routes

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.core.notifications.GameEvents
import com.example.cpen321application.game.model.JoinRequestItem
import com.example.cpen321application.game.model.JoinRequestsHeader
import com.example.cpen321application.game.model.toJoinRequestItem
import com.example.cpen321application.game.model.toJoinRequestsHeader
import com.example.cpen321application.game.repository.JoinRequestDecisionResult
import com.example.cpen321application.game.repository.PendingJoinRequestsResult
import com.example.cpen321application.game.repository.decideJoinRequest
import com.example.cpen321application.game.repository.fetchPendingJoinRequests
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.games.JoinRequestsActions
import com.example.cpen321application.ui.screens.games.JoinRequestsScreen
import com.example.cpen321application.ui.screens.games.JoinRequestsUiState
import kotlinx.coroutines.launch

// the organizer reviews who asked to join, opened from the Game Details banner
@Composable
fun JoinRequestsRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val gameId = backStackEntry.gameId()
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
        app.scope.launch {
            try {
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
            } finally {
                isLoading = false
            }
        }
    }

    // the card goes away once the server saved the answer
    fun decide(request: JoinRequestItem, approve: Boolean) {
        busyRequestIds = busyRequestIds + request.requestId
        app.scope.launch {
            val result = try {
                decideJoinRequest(context, gameId, request.requestId, approve)
            } finally {
                busyRequestIds = busyRequestIds - request.requestId
            }
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

    OnResume(backStackEntry) { loadRequests() }

    // live updates: someone asked to join this game while the list is open
    LaunchedEffect(gameId) {
        GameEvents.events.collect { event ->
            if (event.gameId == gameId && event.type == "join_request") loadRequests()
        }
    }

    JoinRequestsScreen(
        state = JoinRequestsUiState(
            header = header,
            requests = requests,
            isLoading = isLoading,
            errorMessage = error,
            busyRequestIds = busyRequestIds
        ),
        actions = JoinRequestsActions(
            onBack = { app.navController.popBackStack() },
            onRetry = if (unavailable) null else ({ loadRequests() }),
            onApprove = { request -> decide(request, approve = true) },
            onDecline = { request -> decide(request, approve = false) },
            onTabSelected = { tab -> app.openTab(tab) }
        ),
        modifier = modifier
    )
}
