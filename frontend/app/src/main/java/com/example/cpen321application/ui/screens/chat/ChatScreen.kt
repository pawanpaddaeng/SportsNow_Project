package com.example.cpen321application.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.chat.model.ChatMessage
import com.example.cpen321application.ui.components.BackTitleBar
import com.example.cpen321application.ui.components.sportsFieldColors
import com.example.cpen321application.ui.theme.*

// what the Chat screen shows, messages are newest first like the server sends them
data class ChatUiState(
    val title: String,
    val messages: List<ChatMessage>
)

// what the Chat screen's controls do
data class ChatActions(
    val onBack: () -> Unit,
    val onSend: (String) -> Unit = {}
)

/*
    Chat screen: the messages with the newest at the bottom and a box to write a new one.
    Blank messages can't be sent. It has no bottom bar so the box stays above the keyboard
 */
@Composable
fun ChatScreen(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // a new message arrived, show it
    LaunchedEffect(state.messages.firstOrNull()?.messageId) {
        listState.animateScrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
    ) {
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            BackTitleBar(title = state.title, onBack = actions.onBack)
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(state.messages, key = { it.messageId }) { message ->
                MessageRow(message)
            }
        }
        MessageInput(onSend = actions.onSend)
    }
}

// someone else's message on the left with their initial and name, the user's own on the right
@Composable
private fun MessageRow(message: ChatMessage) {
    Row(
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!message.isMine) SenderInitial(message.initial)
        MessageBubble(message)
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
