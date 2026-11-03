package com.example.cpen321application.chat.model

import com.example.cpen321application.chat.dto.ChatMessageDto
import com.example.cpen321application.game.formatDayTime

/*  one message as the Chat screen shows it. isMine puts it on the right without a name.
    messageId is null until the server has stored it. While it's on its way isSending is
    true, and failureMessage says why it wasn't sent. key stays the same for the list.
    senderId opens the sender's profile, it's null for the user's own unsent messages
 */
data class ChatMessage(
    val key: String,
    val messageId: Int?,
    val senderName: String,
    val text: String,
    val time: String,
    val isMine: Boolean,
    val isSending: Boolean = false,
    val failureMessage: String? = null,
    val senderId: Int? = null
) {
    val initial get() = senderName.take(1).uppercase()
}

fun ChatMessageDto.toChatMessage(myUserId: Int?) = ChatMessage(
    key = "message-$messageId",
    messageId = messageId,
    senderName = "${sender.firstName} ${sender.lastName}".trim(),
    text = text,
    time = formatDayTime(createdAt),
    isMine = sender.userId == myUserId,
    senderId = sender.userId
)
