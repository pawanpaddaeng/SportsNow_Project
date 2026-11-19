import { performance } from 'perf_hooks';

import { type ChatSocket, startChatSocket } from '../../src/chat/socket/chatSocket';
import { logInfo } from '../../src/core/logger';
import { createAuthToken } from '../../src/user/auth/jwt';
import { closeAllClients, connectToChat, waitForChatServer } from '../chatSocketClient';
import { gameChatId } from '../no-mock/chat/chatHelpers';
import { addGame, addPlayer, addUser, cleanUpAfterTests } from '../no-mock/helpers';

/*  NFR 2 (Chat Responsiveness): when a user sends a message in a game chat or friend chat,
    the other members who have the chat open should receive it within 1 second. This checks
    the server's part: from a message being sent to the chat WebSocket to it reaching another
    member who has the chat open.
 */

// this file's own port, test files run at the same time
const PORT = 18104;
const MESSAGE_COUNT = 20;
const LIMIT_MS = 1000;

// 20 messages, each allowed up to 1 second, takes longer than Jest's default 5 seconds
jest.setTimeout(60_000);

let chatSocket: ChatSocket;

beforeAll(async () => {
  chatSocket = startChatSocket(PORT);
  expect(await waitForChatServer(PORT)).toBe(true);
});

afterAll(() => {
  chatSocket.close();
});

afterEach(() => {
  closeAllClients();
});

cleanUpAfterTests();

// Interface WebSocket send_message (wss://DOMAIN/ws?chat_id=)
describe('NFR 2: WebSocket send_message responsiveness', () => {
  // Input: the organizer and a player of a game both have its chat open, and the player
  //   sends 20 messages one after another
  // Expected status code: none, it's a WebSocket reply
  // Expected behavior: each message is saved and pushed to the organizer
  // Expected output: the organizer gets each { type: "new_message", message } within 1 second
  //   of it being sent, in the order they were sent
  test('Each message reaches the other member within 1 second', async () => {
    // 1. a game with two players, both with its chat open
    const organizerId = await addUser('Receiver');
    const playerId = await addUser('Sender');
    const gameId = await addGame(organizerId);
    await addPlayer(gameId, playerId);
    const chatId = await gameChatId(gameId);
    const receiver = await connectToChat(PORT, chatId, createAuthToken(organizerId));
    const sender = await connectToChat(PORT, chatId, createAuthToken(playerId));

    // 2. send each message and time how long until the other member gets it
    const timesMs: number[] = [];
    for (let index = 1; index <= MESSAGE_COUNT; index++) {
      const text = `Message ${index}`;
      const start = performance.now();
      sender.sendMessage(chatId, text);
      const reply = await receiver.nextReply();
      timesMs.push(performance.now() - start);

      // 3. check it's the message that was sent
      expect(reply.type).toBe('new_message');
      expect(reply.message).toMatchObject({ chat_id: chatId, text, sender: { user_id: playerId } });
    }

    // 4. check every message arrived in time
    const slowestMs = Math.max(...timesMs);
    const averageMs = timesMs.reduce((sum, time) => sum + time, 0) / MESSAGE_COUNT;
    logInfo(`Chat with ${MESSAGE_COUNT} messages: average ${averageMs.toFixed(0)} ms, ` +
      `slowest ${slowestMs.toFixed(0)} ms`);
    expect(slowestMs).toBeLessThan(LIMIT_MS);
  });
});
