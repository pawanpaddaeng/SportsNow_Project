package com.example.cpen321application.chat.dto

import com.example.cpen321application.core.network.Required
import com.google.gson.annotations.SerializedName

data class ChatSenderDto(
    @SerializedName("user_id") val userId: Int,
    @field:Required @SerializedName("first_name") val firstName: String,
    @field:Required @SerializedName("last_name") val lastName: String
)

// a message as the server sends it, the same from the history endpoint and the WebSocket
data class ChatMessageDto(
    @SerializedName("message_id") val messageId: Int,
    @field:Required val sender: ChatSenderDto,
    @field:Required val text: String,
    @field:Required @SerializedName("created_at") val createdAt: String
)

// reply from GET api/chats/{chatId}/messages, newest first
data class ChatMessagesResponse(
    val msg: String?,
    val messages: List<ChatMessageDto>?,
    @SerializedName("is_closed") val isClosed: Boolean?,
    val error: String?
)

// what the app sends over the WebSocket. clientId comes back with the reply
data class SendMessageRequest(
    @SerializedName("chat_id") val chatId: Int,
    val text: String,
    @SerializedName("client_id") val clientId: String,
    val type: String = "send_message"
)

// what the server sends over the WebSocket: type "new_message" with message, or "error" with msg and error
data class ChatSocketEvent(
    @field:Required val type: String,
    val message: ChatMessageDto?,
    val error: String?,
    @SerializedName("client_id") val clientId: String?
)
