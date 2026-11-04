package com.example.cpen321application.chat.repository

import android.content.Context
import com.example.cpen321application.chat.dto.ChatMessageDto
import com.example.cpen321application.core.NETWORK_MESSAGE
import com.example.cpen321application.core.SESSION_ENDED_MESSAGE
import com.example.cpen321application.core.network.ServerApi
import com.example.cpen321application.core.session.Session
import com.google.gson.JsonParseException
import java.io.IOException

private const val COULD_NOT_LOAD_MESSAGES = "Couldn't load messages, try again"
private const val COULD_NOT_OPEN_CHAT = "Couldn't open this chat, try again"
private const val NOT_FRIENDS_MESSAGE = "You can't open this chat, you're no longer friends."

sealed interface ChatMessagesResult {
    data class Loaded(val messages: List<ChatMessageDto>, val isClosed: Boolean) : ChatMessagesResult
    // retrying won't help: the chat is gone or the user can't be in it
    data object Unavailable : ChatMessagesResult
    data class Failed(val message: String) : ChatMessagesResult
}

// a page of the chat's messages, newest first. before is the oldest message_id the app has
suspend fun fetchChatMessages(context: Context, chatId: Int, before: Int? = null): ChatMessagesResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.gameService.getChatMessages(authorization = authorization, chatId = chatId, before = before)
        } ?: return ChatMessagesResult.Failed(SESSION_ENDED_MESSAGE)
        if (response.code() == 403 || response.code() == 404) return ChatMessagesResult.Unavailable

        val reply = response.body()
        val messages = reply?.messages
        if (reply?.msg == "success" && messages != null) {
            ChatMessagesResult.Loaded(messages = messages, isClosed = reply.isClosed == true)
        } else {
            ChatMessagesResult.Failed(COULD_NOT_LOAD_MESSAGES)
        }
    } catch (e: IOException) {
        ChatMessagesResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        ChatMessagesResult.Failed(COULD_NOT_LOAD_MESSAGES)
    }
}

// which chat to open and what to call it, found differently for a game and a friend
sealed interface ChatInfoResult {
    // chatId is null when the user can't open it, the title can still be shown
    data class Found(val chatId: Int?, val title: String) : ChatInfoResult
    // retrying won't help
    data class Unavailable(val message: String) : ChatInfoResult
    data class Failed(val message: String) : ChatInfoResult
}

// the chat with a friend, titled with their name
suspend fun fetchFriendChat(context: Context, userId: Int): ChatInfoResult {
    return try {
        val response = Session.call(context) { authorization ->
            ServerApi.friendService.getFriendChat(authorization = authorization, userId = userId)
        } ?: return ChatInfoResult.Failed(SESSION_ENDED_MESSAGE)
        if (response.code() == 403) return ChatInfoResult.Unavailable(NOT_FRIENDS_MESSAGE)

        val reply = response.body()
        val friend = reply?.friend
        if (reply?.msg == "success" && reply.chatId != null && friend != null) {
            ChatInfoResult.Found(chatId = reply.chatId, title = "${friend.firstName} ${friend.lastName}".trim())
        } else {
            ChatInfoResult.Failed(COULD_NOT_OPEN_CHAT)
        }
    } catch (e: IOException) {
        ChatInfoResult.Failed(NETWORK_MESSAGE)
    } catch (e: JsonParseException) {
        ChatInfoResult.Failed(COULD_NOT_OPEN_CHAT)
    }
}
