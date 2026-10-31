package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.cpen321application.chat.model.ChatMessage
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.ui.screens.chat.ChatActions
import com.example.cpen321application.ui.screens.chat.ChatScreen
import com.example.cpen321application.ui.screens.chat.ChatUiState

// sample messages, newest first, until the chat is connected to the server
private val SAMPLE_MESSAGES = listOf(
    ChatMessage(3, "Alex Kim", "See you all at the courts!", "3:12 PM", isMine = false),
    ChatMessage(2, "You", "I can bring a spare ball.", "3:08 PM", isMine = true),
    ChatMessage(1, "Alex Kim", "Does anyone have a ball?", "3:05 PM", isMine = false)
)

// the players of a game chat with each other, opened from the Game Details button
@Composable
fun GameChatRoute(app: AppState, modifier: Modifier) {
    val messages = remember { mutableStateListOf(*SAMPLE_MESSAGES.toTypedArray()) }

    ChatScreen(
        state = ChatUiState(title = "Game Chat", messages = messages),
        actions = ChatActions(
            onBack = { app.navController.popBackStack() },
            onSend = { text ->
                val nextId = messages.first().messageId + 1
                messages.add(0, ChatMessage(nextId, "You", text, "Now", isMine = true))
            }
        ),
        modifier = modifier
    )
}
