import type { PushMessage } from '../../core/notifications/push';
import type { ChatAccess, ChatMessage } from '../db/chats';

// how much of a message its notification shows, in characters
const PUSH_PREVIEW_LENGTH = 100;

// the start of a message for its notification, by character so an emoji isn't split in two
function messagePreview(text: string): string {
  const characters = Array.from(text);
  const preview = characters.slice(0, PUSH_PREVIEW_LENGTH).join('');
  return characters.length > PUSH_PREVIEW_LENGTH ? `${preview}...` : preview;
}

// the notification for a friend's message, tagged so their messages show as one notification
function friendMessagePush(message: ChatMessage): PushMessage {
  return {
    title: message.senderFirstName,
    body: messagePreview(message.text),
    // the chat and its title, so tapping the notification opens it straight away
    data: {
      type: 'friend_message',
      chatId: String(message.chatId),
      friendName: `${message.senderFirstName} ${message.senderLastName}`.trim(),
    },
    tag: `friend-${message.senderId}`,
  };
}

/*  the notification for a message in a game's chat: the game's title, then who wrote what.
    Tagged by game so a chat's messages show as one notification, tapping it opens the chat
 */
function gameMessagePush(message: ChatMessage, gameId: number, gameTitle: string): PushMessage {
  return {
    title: gameTitle,
    body: `${message.senderFirstName}: ${messagePreview(message.text)}`,
    data: { type: 'game_message', gameId: String(gameId) },
    tag: `game-${gameId}`,
  };
}

/*  the notification for a new message, as a friend's or a game chat's by the chat it was sent
    in. The chat server sends it to the members who don't have the chat open
 */
export function messagePush(message: ChatMessage, chat: Pick<ChatAccess, 'gameId' | 'gameTitle'>): PushMessage {
  return chat.gameId === null
    ? friendMessagePush(message)
    : gameMessagePush(message, chat.gameId, chat.gameTitle ?? 'Game chat');
}
