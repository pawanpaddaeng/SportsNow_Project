package com.example.cpen321application.chat.model

// one message as the Chat screen shows it. isMine puts it on the right without a name
data class ChatMessage(
    val messageId: Int,
    val senderName: String,
    val text: String,
    val time: String,
    val isMine: Boolean
) {
    val initial get() = senderName.take(1).uppercase()
}
