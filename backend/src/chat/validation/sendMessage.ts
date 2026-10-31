import type { ParseResult } from '../../core/validation';

// longest message, matching chat_messages.text
export const MAX_MESSAGE_LENGTH = 1000;

export interface SendMessage {
  chatId: number;
  text: string;
  // chosen by the app and sent back with the reply, so it knows which message it answers
  clientId: string | null;
}

function parseJson(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

// checks a send_message request from the WebSocket, the text comes back without outer spaces
export function parseSendMessage(raw: string): ParseResult<SendMessage> {
  const input = parseJson(raw);
  if (typeof input !== 'object' || input === null) return { ok: false, error: 'Request must be a JSON object' };

  const { type, chat_id: chatId, text, client_id: clientId } = input as Record<string, unknown>;
  if (type !== 'send_message') return { ok: false, error: 'type must be send_message' };
  if (typeof chatId !== 'number') return { ok: false, error: 'chat_id must be a number' };
  if (typeof text !== 'string' || text.trim() === '') return { ok: false, error: 'text must not be empty' };
  if (text.trim().length > MAX_MESSAGE_LENGTH) {
    return { ok: false, error: `text must be at most ${MAX_MESSAGE_LENGTH} characters` };
  }
  return {
    ok: true,
    value: { chatId, text: text.trim(), clientId: typeof clientId === 'string' ? clientId : null },
  };
}
