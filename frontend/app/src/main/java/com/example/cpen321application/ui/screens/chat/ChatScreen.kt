package com.example.cpen321application.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.chat.model.ChatMessage
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.RetryMessage
import com.example.cpen321application.ui.components.sportsFieldColors
import com.example.cpen321application.ui.theme.*

// how close to the oldest loaded message scrolling loads the next page
private const val LOAD_OLDER_MARGIN = 5

/*  what the Chat screen shows, messages are newest first like the server sends them.
    isConnected false shows that new messages aren't arriving, isClosed that the game ended
    so the chat is read only. canWrite false hides the box to write in
 */
data class ChatUiState(
    val title: String,
    val messages: List<ChatMessage>,
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val isClosed: Boolean = false,
    val canWrite: Boolean = true
)

// what the Chat screen's controls do, onRetry is null when retrying won't help
data class ChatActions(
    val onBack: () -> Unit,
    val onRetry: (() -> Unit)? = null,
    val onSend: (String) -> Unit = {},
    val onRetrySend: (ChatMessage) -> Unit = {},
    val onLoadOlder: () -> Unit = {}
)

/*
    Chat screen: the messages with the newest at the bottom and a box to write a new one.
    Blank messages can't be sent, and a message that failed can be tapped to send it again.
    It has no bottom bar so the box stays above the keyboard
 */
@Composable
fun ChatScreen(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    FollowNewestMessage(listState, newestKey = state.messages.firstOrNull()?.key)
    LoadOlderNearTheTop(listState, onLoadOlder = actions.onLoadOlder)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
    ) {
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            BackTitleBar(title = state.title, onBack = actions.onBack)
        }
        ChatStatus(state, onRetry = actions.onRetry)
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(state.messages, key = { it.key }) { message ->
                MessageRow(message, canRetry = state.canWrite, onRetrySend = { actions.onRetrySend(message) })
            }
        }
        if (state.canWrite) MessageInput(onSend = actions.onSend)
        if (state.isClosed) ClosedNote()
    }
}

// a new message arrived or was sent, show it
@Composable
private fun FollowNewestMessage(listState: LazyListState, newestKey: String?) {
    LaunchedEffect(newestKey) {
        listState.animateScrollToItem(0)
    }
}

@Composable
private fun LoadOlderNearTheTop(listState: LazyListState, onLoadOlder: () -> Unit) {
    val nearOldest by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - LOAD_OLDER_MARGIN
        }
    }
    LaunchedEffect(nearOldest) {
        if (nearOldest) onLoadOlder()
    }
}

// shown instead of the box to write in once the game has ended
@Composable
private fun ClosedNote() {
    Text(
        text = "This game has ended, so the chat is read only.",
        color = SportsOnSurfaceVariant,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(SportsSurfaceContainer)
            .padding(16.dp)
    )
}

// loading bar, the problem that stopped the chat, or that it's reconnecting
@Composable
private fun ChatStatus(state: ChatUiState, onRetry: (() -> Unit)?) {
    when {
        // a closed chat has no connection to wait for
        state.isClosed && state.errorMessage == null -> Unit
        state.errorMessage != null ->
            RetryMessage(message = state.errorMessage, onRetry = onRetry, modifier = Modifier.padding(16.dp))
        state.isLoading && state.messages.isEmpty() -> LinearProgressIndicator(
            color = SportsPrimary,
            trackColor = SportsPrimaryContainer,
            modifier = Modifier.fillMaxWidth()
        )
        !state.isConnected && !state.isLoading -> Text(
            text = "Connecting...",
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// someone else's message on the left with their initial and name, the user's own on the right
@Composable
private fun MessageRow(message: ChatMessage, canRetry: Boolean, onRetrySend: () -> Unit) {
    Row(
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!message.isMine) SenderInitial(message.initial)
        Column(horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start) {
            MessageBubble(message)
            if (message.failureMessage != null) {
                Text(
                    text = if (canRetry) "${message.failureMessage}. Tap to try again." else "${message.failureMessage}.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(enabled = canRetry, onClick = onRetrySend)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SenderInitial(initial: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(SportsPrimaryContainer)
    ) {
        Text(text = initial, color = SportsOnPrimaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val textColor = if (message.isMine) Color.White else SportsOnSurface
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (message.isMine) SportsPrimary else SportsSurfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (!message.isMine) {
            Text(text = message.senderName, color = SportsOnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(text = message.text, color = textColor, fontSize = 14.sp)
        Text(text = message.time, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

// the box to write in and the send button, the box empties once the message is sent
@Composable
private fun MessageInput(onSend: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(text = "Message", fontSize = 14.sp) },
            shape = RoundedCornerShape(24.dp),
            colors = sportsFieldColors(),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                onSend(draft.trim())
                draft = ""
            },
            enabled = draft.isNotBlank(),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = SportsPrimary,
                contentColor = Color.White,
                disabledContainerColor = SportsOutlineVariant,
                disabledContentColor = Color.White
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
        }
    }
}
