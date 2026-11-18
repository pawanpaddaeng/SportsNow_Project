import * as chatsDb from '../../../src/chat/db/chats';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// the id of the test chat
const CHAT_ID = 7;

// Interface GET /api/chats/{chatId}/messages
describe('Mocked: GET /api/chats/:chatId/messages', () => {
  // Mocked behavior: getChatAccess throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: a signed in player opens chat 7
  // Expected status code: 500
  // Expected behavior: the error is handled, the messages are never read
  // Expected output: { msg: "server_error", error: "Could not load messages, try again later" }
  test('Database fails checking access to the chat', async () => {
    // 1. sign in (not what this test checks)
    const user = testPlayer();
    const token = signIn(user);

    // 2. make checking the user's access fail
    const getChatAccess = jest.spyOn(chatsDb, 'getChatAccess').mockRejectedValue(new Error('database down'));
    const getChatMessages = jest.spyOn(chatsDb, 'getChatMessages');

    // 3. open the chat through the interface
    const response = await api.get(`/api/chats/${CHAT_ID}/messages`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load messages, try again later' });
    expect(getChatAccess).toHaveBeenCalledWith(CHAT_ID, user.userId);
    expect(getChatMessages).not.toHaveBeenCalled();
  });

  // Mocked behavior: getChatAccess says the user is a member of an open chat,
  //   getChatMessages throws, as if the database failed
  // Input: a signed in player opens chat 7 with ?before=120
  // Expected status code: 500
  // Expected behavior: the error is handled, getChatMessages was asked for the page before message 120
  // Expected output: { msg: "server_error", error: "Could not load messages, try again later" }
  test('Database fails reading the messages', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testPlayer());

    // 2. let the user in, then make reading the messages fail
    jest.spyOn(chatsDb, 'getChatAccess').mockResolvedValue({ isMember: true, isClosed: false, isFriendChat: false });
    const getChatMessages = jest.spyOn(chatsDb, 'getChatMessages').mockRejectedValue(new Error('database down'));

    // 3. read the older page through the interface
    const response = await api.get(`/api/chats/${CHAT_ID}/messages?before=120`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load messages, try again later' });
    expect(getChatMessages).toHaveBeenCalledWith(CHAT_ID, 120);
  });
});
