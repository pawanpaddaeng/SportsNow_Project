package com.example.cpen321application.ui.screens.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.game.model.JoinRequestItem
import com.example.cpen321application.game.model.JoinRequestsHeader
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.RetryMessage
import com.example.cpen321application.ui.theme.*

/*  what the Join Requests screen shows. header is null until the game loads. isLoading
    shows a loading bar, errorMessage a problem (with Retry when the actions' onRetry is
    given). busyRequestIds are requests whose answer is being sent, their buttons are disabled
 */
data class JoinRequestsUiState(
    val header: JoinRequestsHeader?,
    val requests: List<JoinRequestItem>,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val busyRequestIds: Set<Int> = emptySet()
)

// what the Join Requests screen's controls do, onRetry is null when retrying won't help
data class JoinRequestsActions(
    val onBack: () -> Unit,
    val onRetry: (() -> Unit)? = null,
    val onApprove: (JoinRequestItem) -> Unit = {},
    val onDecline: (JoinRequestItem) -> Unit = {},
    val onTabSelected: (BottomTab) -> Unit = {}
)

/*
    Join Requests screen from the Figma mockup (Accept players to join): the organizer's
    pending requests for one game, each with Decline and Approve. Approve is disabled
    once the game is full. The actions connect it to other screens through AppNavigation
 */
@Composable
fun JoinRequestsScreen(state: JoinRequestsUiState, actions: JoinRequestsActions, modifier: Modifier = Modifier) {
    val header = state.header
    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(selectedTab = BottomTab.Home, onTabSelected = actions.onTabSelected)
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "top") { BackTitleBar(title = "Join Requests", onBack = actions.onBack) }

            if (header != null) {
                item(key = "game") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = header.title, color = SportsOnSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(text = header.subtitle, color = SportsOnSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }

            loadStatus(isLoading = state.isLoading, errorMessage = state.errorMessage, onRetry = actions.onRetry)

            if (header?.isFull == true && state.requests.isNotEmpty()) {
                item(key = "full") {
                    Note("The game is full. Edit the game to add spots before approving more players.")
                }
            }

            items(state.requests, key = { it.requestId }) { request ->
                RequestCard(
                    request = request,
                    isBusy = request.requestId in state.busyRequestIds,
                    canApprove = header?.isFull == false,
                    onApprove = { actions.onApprove(request) },
                    onDecline = { actions.onDecline(request) }
                )
            }

            if (header != null) {
                item(key = "footer") {
                    Note(
                        if (state.requests.isEmpty()) {
                            "No join requests right now. You'll get a notification when someone asks to join."
                        } else {
                            "Approved players are added to the game and its chat."
                        }
                    )
                }
            }
        }
    }
}

// thin loading bar while the list updates and the error of a failed update, the current requests stay visible
private fun LazyListScope.loadStatus(isLoading: Boolean, errorMessage: String?, onRetry: (() -> Unit)?) {
    if (isLoading) {
        item(key = "loading") {
            LinearProgressIndicator(
                color = SportsPrimary,
                trackColor = SportsPrimaryContainer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (errorMessage != null) {
        item(key = "error") {
            RetryMessage(message = errorMessage, onRetry = onRetry, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        color = SportsOnSurfaceVariant,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

// one request: initial, name, details, the quoted message and the two buttons
@Composable
private fun RequestCard(
    request: JoinRequestItem,
    isBusy: Boolean,
    canApprove: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SportsPrimaryContainer)
            ) {
                Text(text = request.initial, color = SportsOnPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = request.name, color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = request.details, color = SportsOnSurfaceVariant, fontSize = 12.sp)
            }
        }

        if (request.message != null) {
            Text(
                text = "“${request.message}”",
                color = SportsOnSurface,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SportsSurfaceContainer)
                    .padding(12.dp)
            )
        }

        DecisionButtons(isBusy = isBusy, canApprove = canApprove, onApprove = onApprove, onDecline = onDecline)
    }
}

// Decline and Approve side by side, Approve shows progress while the answer is sent
@Composable
private fun DecisionButtons(isBusy: Boolean, canApprove: Boolean, onApprove: () -> Unit, onDecline: () -> Unit) {
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
            onClick = onApprove,
            enabled = !isBusy && canApprove,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Approve", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
