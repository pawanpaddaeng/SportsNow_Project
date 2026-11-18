import * as chatsDb from '../../../src/chat/db/chats';
import { type ChatMessage } from '../../../src/chat/db/chats';
import { type ChatSocket, startChatSocket } from '../../../src/chat/socket/chatSocket';
import * as logger from '../../../src/core/logger';
import * as push from '../../../src/core/notifications/push';
import { createAuthToken } from '../../../src/user/auth/jwt';
import * as usersDb from '../../../src/user/db/users';
import {
  closeAllClients,
  connectToChat,
  openChatConnection,
  waitForChatServer,
  waitUntil,
} from '../../chatSocketClient';
import type * as MockEnv from '../mockEnv';
import { cleanUpAfterTests, testOrganizer, testPlayer } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

// this file's own ports, test files run at the same time
const PORT = 18102;
const HEARTBEAT_PORT = 18103;
// how often the server pings its connections
const HEARTBEAT_MS = 30_000;
// the id of the test chat
const CHAT_ID = 7;

let chatSocket: ChatSocket;

beforeAll(async () => {
  chatSocket = startChatSocket(PORT);
  await waitForChatServer(PORT);
});

afterAll(() => {
  chatSocket.close();
});

afterEach(() => {
  closeAllClients();
});

cleanUpAfterTests();

// Olivia and Paul are both signed in: the user lookup (mocked) finds either by id. Returns their tokens
function signInBoth(): { organizerToken: string; playerToken: string } {
  const users = [testOrganizer(), testPlayer()];
  jest.spyOn(usersDb, 'findUserById').mockImplementation((userId) =>
    Promise.resolve(users.find((user) => user.userId === userId) ?? null));
  return { organizerToken: createAuthToken(1), playerToken: createAuthToken(2) };
}

// chat 7 lets both users in and is open, as a game chat or a friend chat
function allowChat(isFriendChat: boolean): void {
  jest.spyOn(chatsDb, 'getChatAccess').mockResolvedValue({ isMember: true, isClosed: false, isFriendChat });
  jest.spyOn(chatsDb, 'getChatMemberIds').mockResolvedValue([1, 2]);
}

// the message as the database would save it, sent by Paul
function savedMessage(text: string): ChatMessage {
  return {
    messageId: 300,
    chatId: CHAT_ID,
    senderId: 2,
    senderFirstName: 'Paul',
    senderLastName: 'Player',
    text,
    createdAt: '2026-11-18T12:00:00Z',
  };
}

// Interface WebSocket send_message (wss://DOMAIN/ws?chat_id=)
describe('Mocked: WebSocket send_message', () => {
  // Mocked behavior: getChatAccess throws while the connection is checked, as if the
  //   database failed. Sign in is mocked only so the check reaches it. logError is watched
  // Input: Paul connects to chat 7
  // Expected status code: 500
  // Expected behavior: the connection isn't opened, the failure is logged
  // Expected output: the HTTP status only, the connection never opens
  test('Database fails while checking a new connection', async () => {
    const { playerToken } = signInBoth();
    jest.spyOn(chatsDb, 'getChatAccess').mockRejectedValue(new Error('database down'));
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    expect(await openChatConnection(PORT, CHAT_ID, playerToken)).toBe(500);
    expect(logError).toHaveBeenCalledWith('Opening a chat connection failed:', expect.any(Error));
  });

  // Mocked behavior: chat 7 lets Paul in, createChatMessage throws, as if the database
  //   failed. logError is watched
  // Input: Paul sends { chat_id: 7, text: "Hi", client_id: "c-1" }
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the failure is logged, Paul's app is told the message wasn't sent
  //   and the connection stays open
  // Expected output: { type: "error", msg: "server_error", error: "Could not send the message", client_id: "c-1" }
  test('Database fails saving the message', async () => {
    const { playerToken } = signInBoth();
    allowChat(false);
    const createChatMessage = jest.spyOn(chatsDb, 'createChatMessage').mockRejectedValue(new Error('database down'));
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);
    const player = await connectToChat(PORT, CHAT_ID, playerToken);

    player.sendMessage(CHAT_ID, 'Hi', 'c-1');

    expect(await player.nextReply()).toEqual({
      type: 'error', msg: 'server_error', error: 'Could not send the message', client_id: 'c-1',
    });
    expect(createChatMessage).toHaveBeenCalledWith(CHAT_ID, 2, 'Hi');
    expect(logError).toHaveBeenCalledWith(`Sending to chat ${CHAT_ID} failed:`, expect.any(Error));
    expect(player.isOpen()).toBe(true);
  });

  // Mocked behavior: chat 7 lets both in, createChatMessage saves the message, then
  //   getChatMemberIds throws, as if the database failed. logError is watched
  // Input: Olivia and Paul are connected, Paul sends "Hi" with client_id "c-2"
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the message is already saved, so Paul is told it's sent. Olivia isn't
  //   sent it, she gets it when her chat next loads. The failure is logged
  // Expected output: new_message to Paul, nothing to Olivia
  test('Database fails reading who is in the chat after saving', async () => {
    const { organizerToken, playerToken } = signInBoth();
    allowChat(false);
    jest.spyOn(chatsDb, 'getChatMemberIds').mockRejectedValue(new Error('database down'));
    jest.spyOn(chatsDb, 'createChatMessage').mockResolvedValue(savedMessage('Hi'));
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);
    const organizer = await connectToChat(PORT, CHAT_ID, organizerToken);
    const player = await connectToChat(PORT, CHAT_ID, playerToken);

    player.sendMessage(CHAT_ID, 'Hi', 'c-2');

    expect(await player.nextReply()).toEqual({
      type: 'new_message',
      message: {
        message_id: 300,
        chat_id: CHAT_ID,
        sender: { user_id: 2, first_name: 'Paul', last_name: 'Player' },
        text: 'Hi',
        created_at: '2026-11-18T12:00:00Z',
      },
      client_id: 'c-2',
    });
    expect(await organizer.unreadReplies()).toEqual([]);
    expect(logError).toHaveBeenCalledWith(`Reading the members of chat ${CHAT_ID} failed:`, expect.any(Error));
  });

  // Mocked behavior: chat 7 is a friend chat of Olivia and Paul, createChatMessage saves
  //   each message, sendPushToUsers is replaced to check what Olivia is sent
  // Input: Paul sends 100 "a"s, then 101 emoji, while Olivia doesn't have the chat open
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the notification shows at most 100 characters of the message, with
  //   "..." when it was cut, counting an emoji as one character so none is split in two
  // Expected output: bodies "aaa...a" (100, no "...") and 100 emoji followed by "..."
  test('Friend notification shows the first 100 characters', async () => {
    const { playerToken } = signInBoth();
    allowChat(true);
    const exactly100 = 'a'.repeat(100);
    const emoji101 = '😀'.repeat(101);
    jest.spyOn(chatsDb, 'createChatMessage')
      .mockResolvedValueOnce(savedMessage(exactly100))
      .mockResolvedValueOnce(savedMessage(emoji101));
    const sendPushToUsers = jest.spyOn(push, 'sendPushToUsers').mockResolvedValue(1);
    const player = await connectToChat(PORT, CHAT_ID, playerToken);

    player.sendMessage(CHAT_ID, exactly100, 'p-1');
    await player.nextReply();
    player.sendMessage(CHAT_ID, emoji101, 'p-2');
    await player.nextReply();

    await waitUntil(() => { expect(sendPushToUsers).toHaveBeenCalledTimes(2); });
    expect(sendPushToUsers).toHaveBeenNthCalledWith(1, [1], expect.objectContaining({ title: 'Paul', body: exactly100 }));
    expect(sendPushToUsers).toHaveBeenNthCalledWith(2, [1], expect.objectContaining({
      title: 'Paul',
      body: `${'😀'.repeat(100)}...`,
    }));
  });

  // Mocked behavior: chat 7 is a friend chat, createChatMessage saves the message,
  //   sendPushToUsers throws as if Firebase failed. logError is watched
  // Input: Paul sends "Hi" with client_id "c-3" while Olivia doesn't have the chat open
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: the message is still sent, the failed notification is only logged
  // Expected output: new_message to Paul
  test('Friend notification fails', async () => {
    const { playerToken } = signInBoth();
    allowChat(true);
    jest.spyOn(chatsDb, 'createChatMessage').mockResolvedValue(savedMessage('Hi'));
    jest.spyOn(push, 'sendPushToUsers').mockRejectedValue(new Error('firebase down'));
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);
    const player = await connectToChat(PORT, CHAT_ID, playerToken);

    player.sendMessage(CHAT_ID, 'Hi', 'c-3');

    expect(await player.nextReply()).toMatchObject({ type: 'new_message', client_id: 'c-3' });
    await waitUntil(() => {
      expect(logError).toHaveBeenCalledWith(`Notifying about a message in chat ${CHAT_ID} failed:`, expect.any(Error));
    });
  });

  // Mocked behavior: chat 7 lets both in. Fake timers run the server's check every
  //   30 seconds without waiting
  // Input: Olivia's app answers the server's pings, Paul's never does. Two checks pass
  // Expected status code: none
  // Expected behavior: a connection that didn't answer the last ping is dropped, one that
  //   answers stays open
  // Expected output: Paul's connection closes (1006, dropped without a close message),
  //   Olivia's is still open
  test('Connections that stop answering pings are dropped', async () => {
    const { organizerToken, playerToken } = signInBoth();
    allowChat(false);
    // only the check every 30 seconds is faked, the connections still need real timers
    jest.useFakeTimers({
      doNotFake: ['Date', 'hrtime', 'nextTick', 'performance', 'queueMicrotask', 'setImmediate',
        'clearImmediate', 'setTimeout', 'clearTimeout'],
    });
    const heartbeatSocket = startChatSocket(HEARTBEAT_PORT);
    try {
      await waitForChatServer(HEARTBEAT_PORT);
      const answers = await connectToChat(HEARTBEAT_PORT, CHAT_ID, organizerToken);
      const silent = await connectToChat(HEARTBEAT_PORT, CHAT_ID, playerToken, false);

      // 1. the first check pings both, then give Olivia's app a moment to answer
      jest.advanceTimersByTime(HEARTBEAT_MS);
      await answers.unreadReplies();

      // 2. the next check drops Paul, who never answered
      jest.advanceTimersByTime(HEARTBEAT_MS);
      expect((await silent.closed).code).toBe(1006);
      expect(answers.isOpen()).toBe(true);
    } finally {
      heartbeatSocket.close();
      jest.useRealTimers();
    }
  });
});
