package com.example.cpen321application.navigation.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cpen321application.chat.dto.ChatSocketEvent
import com.example.cpen321application.chat.model.ChatMessage
import com.example.cpen321application.chat.model.toChatMessage
import com.example.cpen321application.chat.repository.ChatInfoResult
import com.example.cpen321application.chat.repository.ChatMessagesResult
import com.example.cpen321application.chat.repository.fetchChatMessages
import com.example.cpen321application.chat.socket.ChatConnection
import com.example.cpen321application.chat.socket.ChatConnectionListener
import com.example.cpen321application.chat.socket.ChatDisconnect
import com.example.cpen321application.core.session.AuthTokenStore
import com.example.cpen321application.core.session.Session
import com.example.cpen321application.core.session.jwtToken
import com.example.cpen321application.game.repository.GameDetailsResult
import com.example.cpen321application.game.repository.fetchGameDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// how long to wait before reconnecting after the connection was lost
private const val RECONNECT_DELAY_MS = 3_000L
private const val NOT_SENT_MESSAGE = "Not sent, check your connection"

/*  what differs between chats: the title until it's loaded, the problem shown when the
    user can't be in the chat, and how to find the chat
 */
class ChatSource(
    val initialTitle: String,
    val notAllowedMessage: String,
    val load: suspend (Context) -> ChatInfoResult
)

// the chat of a game, for its current players
fun gameChatSource(gameId: Int) = ChatSource(
    initialTitle = "Game Chat",
    notAllowedMessage = "You can't open this chat, you're no longer in this game.",
    load = { context ->
        when (val result = fetchGameDetails(context, gameId)) {
            is GameDetailsResult.Loaded -> ChatInfoResult.Found(result.details.chatId, result.game.title)
            GameDetailsResult.NotFound -> ChatInfoResult.Unavailable("This game no longer exists.")
            is GameDetailsResult.Failed -> ChatInfoResult.Failed(result.message)
        }
    }
)

/*  the chat with a friend, it never closes. The friends list (or the message's notification)
    already gives its id and the friend's name, so nothing needs loading before its messages.
    Whether they're still friends is checked when the messages load
 */
fun friendChatSource(chatId: Int, friendName: String) = ChatSource(
    initialTitle = friendName.ifBlank { "Chat" },
    notAllowedMessage = "You can't open this chat, you're no longer friends.",
    load = { ChatInfoResult.Found(chatId = chatId, title = friendName.ifBlank { "Chat" }) }
)

/*  a chat while its screen is open: loads the messages, keeps a WebSocket open for new
    ones and sends the user's. start and stop follow the screen being shown, so the
    connection never stays open in the background. A lost connection reconnects by itself
    and reloads the newest messages, in case any arrived while it was down
 */
class ChatState(private val context: Context, private val source: ChatSource, private val scope: CoroutineScope) {
    var title by mutableStateOf(source.initialTitle)
        private set
    // newest first: messages still being sent or that failed, then the stored ones by id
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isConnected by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    // retrying won't help: the chat is gone or the user can't be in it
    var isUnavailable by mutableStateOf(false)
        private set
    // the game was completed or cancelled: the messages can be read but no new ones sent
    var isClosed by mutableStateOf(false)
        private set

    private var chatId: Int? = null
    private var connection: ChatConnection? = null
    // callbacks of a connection that was replaced or closed are ignored
    private var connectionNumber = 0
    private var openJob: Job? = null
    private var olderJob: Job? = null
    private var hasOlderMessages = true
    private var wasRejectedJwt = false

    private fun myUserId(): Int? = currentJwt()?.let { AuthTokenStore.userIdOf(it) }

    private fun currentJwt(): String? = jwtToken ?: AuthTokenStore.load(context)

    fun start() {
        stop()
        openJob = scope.launch { open() }
    }

    fun stop() {
        openJob?.cancel()
        olderJob?.cancel()
        connectionNumber++
        connection?.close()
        connection = null
        isConnected = false
        isLoading = false
        failSendingMessages()
    }

    private suspend fun open() {
        isLoading = true
        val id = chatId ?: loadChatId()
        if (id == null) {
            isLoading = false
            return
        }
        chatId = id
        when (val result = fetchChatMessages(context, id)) {
            is ChatMessagesResult.Loaded -> {
                merge(result.messages.map { it.toChatMessage(myUserId()) })
                error = null
                isLoading = false
                isClosed = result.isClosed
                // nothing new can arrive in a closed chat, so it doesn't need a connection
                if (!isClosed) connect(id)
            }
            ChatMessagesResult.Unavailable -> becomeUnavailable(source.notAllowedMessage)
            is ChatMessagesResult.Failed -> {
                error = result.message
                isLoading = false
                reopenAfterDelay()
            }
        }
    }

    // the chat's id and title, or null with the problem shown
    private suspend fun loadChatId(): Int? {
        when (val result = source.load(context)) {
            is ChatInfoResult.Found -> {
                title = result.title
                if (result.chatId == null) becomeUnavailable(source.notAllowedMessage)
                return result.chatId
            }
            is ChatInfoResult.Unavailable -> becomeUnavailable(result.message)
            is ChatInfoResult.Failed -> {
                error = result.message
                reopenAfterDelay()
            }
        }
        return null
    }

    private fun becomeUnavailable(message: String) {
        error = message
        isUnavailable = true
        isLoading = false
    }

    private fun reopenAfterDelay() {
        openJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            open()
        }
    }

    private fun connect(id: Int) {
        val jwt = currentJwt() ?: return
        val number = ++connectionNumber
        // the listener runs on OkHttp's thread, the state only changes on the main thread
        fun onMain(action: () -> Unit) {
            scope.launch { if (number == connectionNumber) action() }
        }
        connection = ChatConnection(id, jwt, object : ChatConnectionListener {
            override fun onOpen() = onMain {
                isConnected = true
                wasRejectedJwt = false
            }
            override fun onEvent(event: ChatSocketEvent) = onMain { handleEvent(event) }
            override fun onDisconnected(reason: ChatDisconnect) = onMain { handleDisconnect(reason) }
        })
    }

    private fun handleEvent(event: ChatSocketEvent) {
        val message = event.message
        if (event.type == "new_message" && message != null) {
            messages = messages.filterNot { it.key == event.clientId }
            merge(listOf(message.toChatMessage(myUserId())))
        } else if (event.type == "error") {
            markFailed(event.clientId, event.error ?: NOT_SENT_MESSAGE)
            if (event.msg == "chat_closed") closeChat()
        }
    }

    // the server says the game ended while the chat was open
    private fun closeChat() {
        isClosed = true
        connectionNumber++
        connection?.close()
        connection = null
        isConnected = false
        failSendingMessages()
    }

    /*  a rejected JWT gets one more try, since reopening reloads the messages and that renews
        an expired one. Rejected again, the session can't be used so the user has to sign in
     */
    private fun handleDisconnect(reason: ChatDisconnect) {
        connectionNumber++
        connection = null
        isConnected = false
        failSendingMessages()
        when (reason) {
            ChatDisconnect.Lost -> reopenAfterDelay()
            ChatDisconnect.NotAllowed -> becomeUnavailable(source.notAllowedMessage)
            ChatDisconnect.Unauthorized -> {
                if (wasRejectedJwt) {
                    Session.requireSignIn()
                } else {
                    wasRejectedJwt = true
                    reopenAfterDelay()
                }
            }
        }
    }

    // adds the stored messages, keeping the list in order and each message once
    private fun merge(stored: List<ChatMessage>) {
        val (unsent, sent) = messages.partition { it.messageId == null }
        messages = unsent + (sent + stored)
            .distinctBy { it.messageId }
            .sortedByDescending { it.messageId }
    }

    private fun markFailed(key: String?, reason: String) {
        messages = messages.map {
            if (it.key == key) it.copy(isSending = false, failureMessage = reason) else it
        }
    }

    private fun failSendingMessages() {
        messages = messages.map {
            if (it.isSending) it.copy(isSending = false, failureMessage = NOT_SENT_MESSAGE) else it
        }
    }

    // shows the message right away, it's replaced by the stored one when the server sends it back
    fun send(text: String) {
        val key = UUID.randomUUID().toString()
        messages = listOf(
            ChatMessage(key, null, "You", text, "Sending...", isMine = true, isSending = true)
        ) + messages
        transmit(key, text)
    }

    fun retry(message: ChatMessage) {
        messages = messages.map {
            if (it.key == message.key) it.copy(isSending = true, failureMessage = null) else it
        }
        transmit(message.key, message.text)
    }

    private fun transmit(key: String, text: String) {
        if (connection?.send(text, key) != true) markFailed(key, NOT_SENT_MESSAGE)
    }

    // the next page when the user scrolls up to the oldest messages, a failure tries again on the next scroll
    fun loadOlder() {
        val id = chatId ?: return
        val oldest = messages.lastOrNull { it.messageId != null }?.messageId ?: return
        if (!hasOlderMessages || olderJob?.isActive == true) return
        olderJob = scope.launch {
            val result = fetchChatMessages(context, id, before = oldest)
            if (result is ChatMessagesResult.Loaded) {
                hasOlderMessages = result.messages.isNotEmpty()
                merge(result.messages.map { it.toChatMessage(myUserId()) })
            }
        }
    }
}
