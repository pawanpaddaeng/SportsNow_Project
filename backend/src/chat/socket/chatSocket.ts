import { createServer, type IncomingMessage } from 'node:http';
import type { Duplex } from 'node:stream';
import { type RawData, WebSocket, WebSocketServer } from 'ws';
import { logError, logInfo } from '../../core/logger';
import { type PushMessage, sendPushToUsers } from '../../core/notifications/push';
import { readId } from '../../core/validation';
import { checkToken } from '../../user/auth/middleware';
import { type ChatMessage, createChatMessage, getChatAccess, getChatMemberIds } from '../db/chats';
import { chatMessageJson } from '../routes/chatJson';
import { parseSendMessage, type SendMessage } from '../validation/sendMessage';
import { messagePush } from './messagePush';

// far more than the longest message, larger requests close the connection
const MAX_REQUEST_BYTES = 16 * 1024;
// how often connections are checked, phones can vanish without closing them
const HEARTBEAT_MS = 30_000;
// close code for a user who can no longer be in the chat, or a chat that is gone
const CLOSE_NOT_A_MEMBER = 4403;

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
  const token = header?.startsWith('Bearer ') ? header.slice('Bearer '.length).trim() : '';
  return token === '' ? null : token;
}

// a request's text. The server keeps ws's default binaryType "nodebuffer", so ws always hands it over as one Buffer
function requestText(raw: RawData): string {
  return (raw as Buffer).toString('utf8');
}

// sends to one connection, it may have closed already so a failure is only logged
function sendJson(webSocket: WebSocket, reply: object): void {
  if (webSocket.readyState !== WebSocket.OPEN) return;
  try {
    webSocket.send(JSON.stringify(reply));
  } catch (error) {
    logError('Sending over a chat connection failed:', error);
  }
}

/*  decides who is connecting and to which chat: ?chat_id= and the JWT in the Authorization
    header. Returns the new client, or the status to reject with. A closed chat can still
    be opened, sending to it is what fails
 */
async function checkConnection(req: IncomingMessage): Promise<ChatClient | number> {
  // Node always sets url on an upgrade request, String() only satisfies its optional type
  const chatId = readId(new URL(String(req.url), 'http://localhost').searchParams.get('chat_id') ?? '');
  const token = readBearerToken(req);
  if (chatId === null || token === null) return 400;

  const auth = await checkToken(token);
  if (!auth.ok) return 401;

  const access = await getChatAccess(chatId, auth.user.userId);
  if (!access) return 404;
  if (!access.isMember) return 403;
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
    player of the game or a friend), 404 (no such chat) or 500. A message also sends a
    notification to the chat's other members who don't have it open
 */
export function startChatSocket(port: number): ChatSocket {
  const webSockets = new WebSocketServer({ noServer: true, maxPayload: MAX_REQUEST_BYTES });
  const clients = new Map<WebSocket, ChatClient>();

  /*  sends to the chat's connections, closing those of users no longer in the chat. The
      message is already saved by now, so if the members can't be read only the sender is
      told: it's sent, and the others get it when their chat next loads
   */
  async function broadcast(chatId: number, sender: WebSocket, reply: object): Promise<void> {
    let memberIds: Set<number>;
    try {
      memberIds = new Set(await getChatMemberIds(chatId));
    } catch (error) {
      logError(`Reading the members of chat ${chatId} failed:`, error);
      sendJson(sender, reply);
      return;
    }
    for (const [webSocket, client] of clients) {
      if (client.chatId !== chatId) continue;
      if (memberIds.has(client.userId)) sendJson(webSocket, reply);
      else webSocket.close(CLOSE_NOT_A_MEMBER, 'Not a member of this chat');
    }
  }

  // sends push to the chat's members other than the sender who don't have the chat open, the message already reached those who do
  async function notifyAbsentMembers(message: ChatMessage, push: PushMessage): Promise<void> {
    const connected = new Set<number>();
    for (const client of clients.values()) {
      if (client.chatId === message.chatId) connected.add(client.userId);
    }
    const memberIds = await getChatMemberIds(message.chatId);
    const absent = memberIds.filter((id) => id !== message.senderId && !connected.has(id));
    await sendPushToUsers(absent, push);
  }

  // checks again on every message, the user may have left or the game may have ended since connecting
  async function sendToChat(webSocket: WebSocket, client: ChatClient, message: SendMessage): Promise<void> {
    const sendError = (msg: string, error: string): void => {
      sendJson(webSocket, { type: 'error', msg, error, client_id: message.clientId });
    };
    if (message.chatId !== client.chatId) {
      sendError('forbidden', 'This connection is for a different chat');
      return;
    }

    const access = await getChatAccess(client.chatId, client.userId);
    if (!access) {
      sendError('chat_not_found', 'This chat does not exist');
      // e.g. the friend deleted their account, nothing more can be sent here
      webSocket.close(CLOSE_NOT_A_MEMBER, 'This chat does not exist');
      return;
    }
    if (!access.isMember) {
      sendError('forbidden', "You can't use this chat");
      webSocket.close(CLOSE_NOT_A_MEMBER, 'Not a member of this chat');
      return;
    }
    if (access.isClosed) {
      sendError('chat_closed', 'This chat is closed because the game ended');
      return;
    }

    const saved = await createChatMessage(client.chatId, client.userId, message.text);
    await broadcast(client.chatId, webSocket, {
      type: 'new_message',
      message: chatMessageJson(saved),
      client_id: message.clientId,
    });

    // best effort, the message is already delivered and saved so a failure is only logged
    notifyAbsentMembers(saved, messagePush(saved, access)).catch((error: unknown) => {
      logError(`Notifying about a message in chat ${client.chatId} failed:`, error);
    });
  }

  function handleRequest(webSocket: WebSocket, client: ChatClient, raw: RawData): void {
    const parsed = parseSendMessage(requestText(raw));
    if (!parsed.ok) {
      sendJson(webSocket, { type: 'error', msg: 'invalid_request', error: parsed.error, client_id: null });
      return;
    }
    sendToChat(webSocket, client, parsed.value).catch((error: unknown) => {
      logError(`Sending to chat ${client.chatId} failed:`, error);
      sendJson(webSocket, {
        type: 'error',
        msg: 'server_error',
        error: 'Could not send the message',
        client_id: parsed.value.clientId,
      });
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
