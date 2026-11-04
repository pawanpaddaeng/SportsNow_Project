import type { ChatMessage } from '../db/chats';

// JSON shape of a chat message, the same from the history endpoint and the WebSocket
export function chatMessageJson(message: ChatMessage) {
  return {
    message_id: message.messageId,
    chat_id: message.chatId,
    sender: {
      user_id: message.senderId,
      first_name: message.senderFirstName,
      last_name: message.senderLastName,
    },
    text: message.text,
    created_at: message.createdAt,
  };
}
