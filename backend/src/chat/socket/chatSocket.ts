import { createServer, type IncomingMessage } from 'node:http';
import type { Duplex } from 'node:stream';
import { type RawData, WebSocket, WebSocketServer } from 'ws';
import { logError, logInfo } from '../../core/logger';
import { readId } from '../../core/validation';
import { checkToken } from '../../user/auth/middleware';
import { createChatMessage, getChatAccess, getChatPlayerIds } from '../db/chats';
import { chatMessageJson } from '../routes/chatJson';
import { parseSendMessage, type SendMessage } from '../validation/sendMessage';

// far more than the longest message, larger requests close the connection
const MAX_REQUEST_BYTES = 16 * 1024;
// how often connections are checked, phones can vanish without closing them
const HEARTBEAT_MS = 30_000;
// close code for a user who is no longer a player of the game
const CLOSE_NOT_A_PLAYER = 4403;

// who a connection belongs to and the one chat it is open for
interface ChatClient {
  userId: number;
  chatId: number;
  isAlive: boolean;
}

// a running chat socket, closed when the server shuts down
export interface ChatSocket {
  close(): void;
}

// answers the upgrade request with an error instead of opening the connection
function rejectUpgrade(socket: Duplex, status: number): void {
  socket.end(`HTTP/1.1 ${status} Rejected\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
}

function readBearerToken(req: IncomingMessage): string | null {
  const header = req.headers.authorization;
  return header?.startsWith('Bearer ') ? header.slice('Bearer '.length).trim() || null : null;
}

/*  decides who is connecting and to which chat: ?chat_id= and the JWT in the Authorization
    header. Returns the new client, or the status to reject with. A closed chat can still
    be opened, sending to it is what fails
 */
async function checkConnection(req: IncomingMessage): Promise<ChatClient | number> {
  const chatId = readId(new URL(req.url ?? '/', 'http://localhost').searchParams.get('chat_id') ?? '');
  const token = readBearerToken(req);
  if (chatId === null || token === null) return 400;

  const auth = await checkToken(token);
  if (!auth.ok) return 401;

  const access = await getChatAccess(chatId, auth.user.userId);
  if (!access) return 404;
  if (!access.isPlayer) return 403;
  return { userId: auth.user.userId, chatId, isAlive: true };
}

/*  the WebSocket server for chats, on its own port (Caddy sends wss://DOMAIN/ws to it).
    The app connects while a chat screen is shown, then:
      sends    { type: "send_message", chat_id, text, client_id? }
      receives { type: "new_message", message, client_id } when anyone sends to the chat,
               client_id is the sender's so their app knows its message arrived
      receives { type: "error", msg, error, client_id } when its message wasn't sent:
               invalid_request, chat_not_found, forbidden, chat_closed or server_error
    Connecting is rejected with 400 (no chat_id or token), 401 (bad token), 403 (not a
    player of the game), 404 (no such chat) or 500
 */
export function startChatSocket(port: number): ChatSocket {
  const webSockets = new WebSocketServer({ noServer: true, maxPayload: MAX_REQUEST_BYTES });
  const clients = new Map<WebSocket, ChatClient>();

  // sends to the chat's connections, closing those of users no longer in the game
  async function broadcast(chatId: number, reply: object): Promise<void> {
    const playerIds = new Set(await getChatPlayerIds(chatId));
    for (const [webSocket, client] of clients) {
      if (client.chatId !== chatId) continue;
      if (!playerIds.has(client.userId)) {
        webSocket.close(CLOSE_NOT_A_PLAYER, 'Not a player of this game');
      } else if (webSocket.readyState === WebSocket.OPEN) {
        webSocket.send(JSON.stringify(reply));
      }
    }
  }

  // checks again on every message, the user may have left or the game may have ended since connecting
  async function sendToChat(webSocket: WebSocket, client: ChatClient, message: SendMessage): Promise<void> {
    const sendError = (msg: string, error: string): void => {
      webSocket.send(JSON.stringify({ type: 'error', msg, error, client_id: message.clientId }));
    };
    if (message.chatId !== client.chatId) return sendError('forbidden', 'This connection is for a different chat');

    const access = await getChatAccess(client.chatId, client.userId);
    if (!access) return sendError('chat_not_found', 'This chat does not exist');
    if (!access.isPlayer) {
      sendError('forbidden', 'Only players of this game can use its chat');
      return webSocket.close(CLOSE_NOT_A_PLAYER, 'Not a player of this game');
    }
    if (access.isClosed) return sendError('chat_closed', 'This chat is closed because the game ended');

    const saved = await createChatMessage(client.chatId, client.userId, message.text);
    await broadcast(client.chatId, {
      type: 'new_message',
      message: chatMessageJson(saved),
      client_id: message.clientId,
    });
  }

  function handleRequest(webSocket: WebSocket, client: ChatClient, raw: RawData): void {
    const parsed = parseSendMessage(raw.toString());
    if (!parsed.ok) {
      webSocket.send(JSON.stringify({ type: 'error', msg: 'invalid_request', error: parsed.error, client_id: null }));
      return;
    }
    sendToChat(webSocket, client, parsed.value).catch((error: unknown) => {
      logError(`Sending to chat ${client.chatId} failed:`, error);
      webSocket.send(JSON.stringify({
        type: 'error',
        msg: 'server_error',
        error: 'Could not send the message',
        client_id: parsed.value.clientId,
      }));
    });
  }

  function openConnection(req: IncomingMessage, socket: Duplex, head: Buffer, client: ChatClient): void {
    webSockets.handleUpgrade(req, socket, head, (webSocket) => {
      clients.set(webSocket, client);
      webSocket.on('pong', () => { client.isAlive = true; });
      webSocket.on('message', (raw) => { handleRequest(webSocket, client, raw); });
      webSocket.on('close', () => { clients.delete(webSocket); });
      webSocket.on('error', (error) => { logError(`Chat ${client.chatId} connection failed:`, error); });
    });
  }

  const server = createServer((_req, res) => {
    res.writeHead(426).end();
  });
  server.on('upgrade', (req, socket, head) => {
    checkConnection(req)
      .then((result) => {
        if (typeof result === 'number') rejectUpgrade(socket, result);
        else openConnection(req, socket, head, result);
      })
      .catch((error: unknown) => {
        logError('Opening a chat connection failed:', error);
        rejectUpgrade(socket, 500);
      });
  });
  server.listen(port, () => {
    logInfo(`Chat WebSocket listening on port ${port}`);
  });

  // drops connections that didn't answer the last ping
  const heartbeat = setInterval(() => {
    for (const [webSocket, client] of clients) {
      if (!client.isAlive) {
        webSocket.terminate();
      } else {
        client.isAlive = false;
        webSocket.ping();
      }
    }
  }, HEARTBEAT_MS);

  return {
    close() {
      clearInterval(heartbeat);
      for (const webSocket of clients.keys()) webSocket.terminate();
      server.close();
    },
  };
}
