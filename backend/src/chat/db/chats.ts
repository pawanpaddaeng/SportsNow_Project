import { executeWrite, type Queryable, selectRows } from '../../core/db/mysql';

// most messages sent to the app at once, it asks for older ones with ?before=
export const MESSAGES_PAGE_SIZE = 50;

// a message as read back from the database, with its sender's name
export interface ChatMessage {
  messageId: number;
  chatId: number;
  senderId: number;
  senderFirstName: string;
  senderLastName: string;
  text: string;
  // UTC ISO 8601, like the game times
  createdAt: string;
}

interface ChatMessageRow {
  message_id: number;
  chat_id: number;
  sender_id: number;
  first_name: string;
  last_name: string;
  text: string;
  // TIMESTAMP column read as seconds since 1970 so time zones can't shift it
  created_at_unix: number;
}

// messages with their sender's name, shared by every query that returns messages
const SELECT_MESSAGES = `
  SELECT m.message_id, m.chat_id, m.sender_id, u.first_name, u.last_name, m.text,
         UNIX_TIMESTAMP(m.created_at) AS created_at_unix
  FROM chat_messages m
  JOIN users u ON u.user_id = m.sender_id`;

// opens the chat of a new game, in the transaction creating the game. Returns chat_id
export async function createGameChat(connection: Queryable, gameId: number): Promise<number> {
  const result = await executeWrite('INSERT INTO chats (game_id) VALUES (?)', [gameId], connection);
  return result.insertId;
}

// the chat of the game, or null if it has none
export async function getGameChatId(gameId: number): Promise<number | null> {
  const rows = await selectRows<{ chat_id: number }>(
    'SELECT chat_id FROM chats WHERE game_id = ? LIMIT 1',
    [gameId],
  );
  return rows[0]?.chat_id ?? null;
}

// what the user may do with a chat
export interface ChatAccess {
  // only the game's current players can open its chat
  isPlayer: boolean;
  // the game was completed or cancelled, the chat can be read but not written to
  isClosed: boolean;
}

interface ChatAccessRow {
  status: string;
  is_player: number;
}

// the user's access to the chat, or null if no chat has the id
export async function getChatAccess(chatId: number, userId: number): Promise<ChatAccess | null> {
  const rows = await selectRows<ChatAccessRow>(
    `SELECT g.status,
            EXISTS (SELECT 1 FROM game_players p WHERE p.game_id = c.game_id AND p.user_id = ?) AS is_player
     FROM chats c
     JOIN games g ON g.game_id = c.game_id
     WHERE c.chat_id = ? LIMIT 1`,
    [userId, chatId],
  );
  const row = rows[0];
  if (!row) return null;
  return { isPlayer: Number(row.is_player) === 1, isClosed: row.status !== 'scheduled' };
}

function toChatMessage(row: ChatMessageRow): ChatMessage {
  return {
    messageId: row.message_id,
    chatId: row.chat_id,
    senderId: row.sender_id,
    senderFirstName: row.first_name,
    senderLastName: row.last_name,
    text: row.text,
    // same format as the other times, without milliseconds
    createdAt: new Date(Number(row.created_at_unix) * 1000).toISOString().replace('.000Z', 'Z'),
  };
}

/*  one page of the chat's messages, newest first. before is the oldest message_id the
    app already has, null for the newest page. Ordered by id, which never ties like a
    time can, so paging can't skip or repeat a message
 */
export async function getChatMessages(chatId: number, before: number | null): Promise<ChatMessage[]> {
  const rows = await selectRows<ChatMessageRow>(
    `${SELECT_MESSAGES}
     WHERE m.chat_id = ? ${before === null ? '' : 'AND m.message_id < ?'}
     ORDER BY m.message_id DESC
     LIMIT ${MESSAGES_PAGE_SIZE}`,
    before === null ? [chatId] : [chatId, before],
  );
  return rows.map(toChatMessage);
}

// saves a message and reads it back so everyone gets the stored id, sender name and time
export async function createChatMessage(chatId: number, senderId: number, text: string): Promise<ChatMessage> {
  const result = await executeWrite(
    'INSERT INTO chat_messages (chat_id, sender_id, text) VALUES (?, ?, ?)',
    [chatId, senderId, text],
  );
  const rows = await selectRows<ChatMessageRow>(`${SELECT_MESSAGES} WHERE m.message_id = ?`, [result.insertId]);
  const row = rows[0];
  if (!row) throw new Error(`Message ${result.insertId} not found after creating it`);
  return toChatMessage(row);
}

// the users who can currently be in the chat: the players of its game
export async function getChatPlayerIds(chatId: number): Promise<number[]> {
  const rows = await selectRows<{ user_id: number }>(
    `SELECT p.user_id FROM chats c
     JOIN game_players p ON p.game_id = c.game_id
     WHERE c.chat_id = ?`,
    [chatId],
  );
  return rows.map((row) => row.user_id);
}
