package com.example.cpen321application.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import com.example.cpen321application.core.notifications.GameEvents
import com.example.cpen321application.core.rememberSafeCoroutineScope
import com.example.cpen321application.navigation.Routes
import com.example.cpen321application.navigation.state.AppState
import com.example.cpen321application.navigation.state.ChatState
import com.example.cpen321application.navigation.state.friendChatSource
import com.example.cpen321application.navigation.state.gameChatSource
import com.example.cpen321application.ui.screens.chat.ChatActions
import com.example.cpen321application.ui.screens.chat.ChatScreen
import com.example.cpen321application.ui.screens.chat.ChatUiState

// notifications that change whether the user can read or write in the game's chat
private val CHAT_CHANGING_EVENTS = setOf("game_completed", "game_cancelled", "removed_from_game")

private fun NavBackStackEntry.friendChatId(): Int = arguments?.getInt(Routes.CHAT_ID_ARG) ?: 0
private fun NavBackStackEntry.friendName(): String = arguments?.getString(Routes.FRIEND_NAME_ARG).orEmpty()

// the players of a game chat with each other, opened from the Game Details button
@Composable
fun GameChatRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val gameId = backStackEntry.gameId()
    // this screen's scope, so leaving the screen stops its work
    val scope = rememberSafeCoroutineScope()
    val chat = remember(gameId) { ChatState(context, gameChatSource(gameId), scope) }

    // the game ended or the user was removed while the screen is open: load what's true now.
    // Only while it's shown, the connection stays closed in the background and it reloads on return
    LaunchedEffect(gameId) {
        GameEvents.events.collect { event ->
            val changesChat = event.type in CHAT_CHANGING_EVENTS && event.gameId == gameId
            if (changesChat && backStackEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) chat.start()
        }
    }

    ChatRoom(app, chat, backStackEntry, modifier)
}

// two friends chat with each other, opened from the friends list or a message's notification
@Composable
fun FriendChatRoute(app: AppState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    val context = LocalContext.current
    val chatId = backStackEntry.friendChatId()
    val friendName = backStackEntry.friendName()
    // this screen's scope, so leaving the screen stops its work
    val scope = rememberSafeCoroutineScope()
    val chat = remember(chatId) { ChatState(context, friendChatSource(chatId, friendName), scope) }

    ChatRoom(app, chat, backStackEntry, modifier)
}

// the Chat screen for any chat, with its connection following the screen
@Composable
private fun ChatRoom(app: AppState, chat: ChatState, backStackEntry: NavBackStackEntry, modifier: Modifier) {
    // the connection is only open while the screen is shown, not with the app in the background
    DisposableEffect(backStackEntry) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> chat.start()
                Lifecycle.Event.ON_STOP -> chat.stop()
                else -> Unit
            }
        }
        backStackEntry.lifecycle.addObserver(observer)
        onDispose {
            backStackEntry.lifecycle.removeObserver(observer)
            chat.stop()
        }
    }

    ChatScreen(
        state = ChatUiState(
            title = chat.title,
            messages = chat.messages,
            isLoading = chat.isLoading,
            isConnected = chat.isConnected,
            errorMessage = chat.error,
            isClosed = chat.isClosed,
            canWrite = !chat.isUnavailable && !chat.isClosed
        ),
        actions = ChatActions(
            onBack = { app.navController.popBackStack() },
            onRetry = if (chat.isUnavailable) null else ({ chat.start() }),
            onSend = { text -> chat.send(text) },
            onRetrySend = { message -> chat.retry(message) },
            onLoadOlder = { chat.loadOlder() },
            onOpenProfile = { userId -> app.openProfile(userId) }
        ),
        modifier = modifier
    )
}
