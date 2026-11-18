import { executeWrite, selectRows } from '../../../src/core/db/mysql';
import { getGameChatId } from '../../../src/chat/db/chats';

// setup and checks shared by the unmocked chat tests, straight on the database

// an id far above any id the tests create
export const MISSING_CHAT_ID = 999999999;

// the chat opened with the game when it was created
export async function gameChatId(gameId: number): Promise<number> {
  const chatId = await getGameChatId(gameId);
  if (chatId === null) throw new Error(`Game ${gameId} has no chat`);
  return chatId;
}

// saves a message in the chat, without going through the WebSocket. Returns its id
export async function addMessage(chatId: number, senderId: number, text: string): Promise<number> {
  const { insertId } = await executeWrite(
    'INSERT INTO chat_messages (chat_id, sender_id, text) VALUES (?, ?, ?)',
    [chatId, senderId, text],
  );
  return insertId;
}

// saves count messages "Message 1", "Message 2", ... in that order. Returns their ids, oldest first
export async function addMessages(chatId: number, senderId: number, count: number): Promise<number[]> {
  const ids: number[] = [];
  for (let i = 1; i <= count; i++) {
    ids.push(await addMessage(chatId, senderId, `Message ${i}`));
  }
  return ids;
}

// takes the user out of the game, as leaving or being removed does
export async function removeFromGame(gameId: number, userId: number): Promise<void> {
  await executeWrite('DELETE FROM game_players WHERE game_id = ? AND user_id = ?', [gameId, userId]);
}

// ends the friendship both ways, the friend chat itself stays
export async function unfriend(userId: number, otherUserId: number): Promise<void> {
  await executeWrite(
    'DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)',
    [userId, otherUserId, otherUserId, userId],
  );
}

export interface SavedMessage {
  message_id: number;
  sender_id: number;
  text: string;
}

// the messages really saved in the chat, oldest first
export function getSavedMessages(chatId: number): Promise<SavedMessage[]> {
  return selectRows<SavedMessage>(
    'SELECT message_id, sender_id, text FROM chat_messages WHERE chat_id = ? ORDER BY message_id',
    [chatId],
  );
}
