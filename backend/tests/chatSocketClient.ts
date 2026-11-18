import { WebSocket } from 'ws';

// a test app on the chat WebSocket, shared by the mocked and unmocked chat tests

// how long a test waits for a reply before failing
const REPLY_TIMEOUT_MS = 3000;

// what the server sends back over the WebSocket
export interface ChatReply {
  type: string;
  msg?: string;
  error?: string;
  client_id: string | null;
  message?: {
    message_id: number;
    chat_id: number;
    sender: { user_id: number; first_name: string; last_name: string };
    text: string;
    created_at: string;
  };
}

// how the server closed the connection
export interface CloseInfo {
  code: number;
  reason: string;
}

// every connection the tests opened, so closeAllClients can close them after each test
const openClients = new Set<WebSocket>();

// one open connection: what it was sent is kept in order, so tests read replies one at a time
export class ChatTestClient {
  private readonly replies: ChatReply[] = [];
  private waiting: ((reply: ChatReply) => void) | null = null;
  // settles when the connection closes, with the server's close code
  readonly closed: Promise<CloseInfo>;

  constructor(private readonly webSocket: WebSocket) {
    webSocket.on('message', (raw) => {
      const reply = JSON.parse(String(raw)) as ChatReply;
      if (this.waiting) {
        const resolve = this.waiting;
        this.waiting = null;
        resolve(reply);
      } else {
        this.replies.push(reply);
      }
    });
    this.closed = new Promise((resolve) => {
      webSocket.once('close', (code, reason) => { resolve({ code, reason: reason.toString() }); });
    });
  }

  // sends a send_message request
  sendMessage(chatId: number, text: string, clientId?: string): void {
    this.sendRaw(JSON.stringify({ type: 'send_message', chat_id: chatId, text, client_id: clientId }));
  }

  // sends exactly this text, e.g. a request that isn't JSON
  sendRaw(text: string): void {
    this.webSocket.send(text);
  }

  // the next reply, failing the test if none comes in time
  nextReply(): Promise<ChatReply> {
    const queued = this.replies.shift();
    if (queued) return Promise.resolve(queued);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiting = null;
        reject(new Error('No reply from the chat WebSocket'));
      }, REPLY_TIMEOUT_MS);
      this.waiting = (reply) => {
        clearTimeout(timer);
        resolve(reply);
      };
    });
  }

  // the replies that arrived and haven't been read, after giving any late ones a moment
  async unreadReplies(waitMs = 200): Promise<ChatReply[]> {
    await new Promise((resolve) => { setTimeout(resolve, waitMs); });
    return this.replies.splice(0);
  }

  isOpen(): boolean {
    return this.webSocket.readyState === WebSocket.OPEN;
  }
}

/*  opens a connection to the chat as the app does: ?chat_id= and the token in the
    Authorization header. Leave either out with null. Resolves with the open connection,
    or with the HTTP status the server rejected it with. autoPong false makes a client
    that never answers the server's pings
 */
export function openChatConnection(
  port: number,
  chatId: number | string | null,
  token: string | null,
  autoPong = true,
): Promise<ChatTestClient | number> {
  const query = chatId === null ? '' : `?chat_id=${chatId}`;
  const headers = token === null ? {} : { Authorization: `Bearer ${token}` };
  const webSocket = new WebSocket(`ws://127.0.0.1:${port}/${query}`, { headers, autoPong });
  openClients.add(webSocket);
  // the error after a rejected connection is expected, the test reads the status instead
  webSocket.on('error', () => undefined);

  return new Promise((resolve, reject) => {
    webSocket.once('open', () => { resolve(new ChatTestClient(webSocket)); });
    webSocket.once('unexpected-response', (_req, res) => {
      res.resume();
      resolve(res.statusCode ?? 0);
    });
    webSocket.once('close', () => { reject(new Error('Connection closed before it opened')); });
  });
}

// opens a connection that the test expects to be accepted
export async function connectToChat(port: number, chatId: number, token: string, autoPong = true): Promise<ChatTestClient> {
  const result = await openChatConnection(port, chatId, token, autoPong);
  if (typeof result === 'number') throw new Error(`Connection was rejected with ${result}`);
  return result;
}

// closes every connection the test opened, call it after each test
export function closeAllClients(): void {
  for (const webSocket of openClients) webSocket.terminate();
  openClients.clear();
}

// how often the waits below check again
const CHECK_EVERY_MS = 20;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => { setTimeout(resolve, ms); });
}

// whether the chat server answers on the port: a plain HTTP request gets 426 Upgrade Required
function chatServerAnswers(port: number): Promise<boolean> {
  return fetch(`http://127.0.0.1:${port}/`).then((response) => response.status === 426, () => false);
}

/*  waits until the chat server answers on the port, it starts listening a moment after
    starting. Returns whether it did in time, e.g. expect(await waitForChatServer(port)).toBe(true)
 */
export async function waitForChatServer(port: number): Promise<boolean> {
  for (let waited = 0; waited < REPLY_TIMEOUT_MS; waited += CHECK_EVERY_MS) {
    if (await chatServerAnswers(port)) return true;
    await sleep(CHECK_EVERY_MS);
  }
  return chatServerAnswers(port);
}

/*  for work the server does after replying: waits until isDone is true, or the reply time
    passed. Returns isDone's last answer, the test then checks the result
 */
export async function waitUntil(isDone: () => boolean): Promise<boolean> {
  for (let waited = 0; waited < REPLY_TIMEOUT_MS; waited += CHECK_EVERY_MS) {
    if (isDone()) return true;
    await sleep(CHECK_EVERY_MS);
  }
  return isDone();
}
