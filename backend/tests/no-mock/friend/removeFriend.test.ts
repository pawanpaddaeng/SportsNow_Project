import { type ChatSocket, startChatSocket } from '../../../src/chat/socket/chatSocket';
import { createAuthToken } from '../../../src/user/auth/jwt';
import { closeAllClients, connectToChat, waitForChatServer } from '../../chatSocketClient';
import { addMessages, getSavedMessages } from '../chat/chatHelpers';
import { addUser, api, asUser, type Call, cleanUpAfterTests, expectSignInRequired } from '../helpers';
import { countFriendshipRows, getFriendChatIds, makeFriends, MISSING_ID } from './friendHelpers';

// this file's own port, test files run at the same time
const PORT = 18104;
// the close code for a user who is no longer in the chat, or a chat that is gone
const CLOSE_NOT_A_MEMBER = 4403;

let chatSocket: ChatSocket;
let userId: number;
let friendId: number;
let chatId: number;

beforeAll(async () => {
  chatSocket = startChatSocket(PORT);
  expect(await waitForChatServer(PORT)).toBe(true);
});

afterAll(() => {
  chatSocket.close();
});

// every test starts with Uma and Fay as friends, with a chat holding 3 messages
beforeEach(async () => {
  userId = await addUser('Uma');
  friendId = await addUser('Fay');
  chatId = await makeFriends(userId, friendId);
  await addMessages(chatId, userId, 3);
});

afterEach(() => {
  closeAllClients();
});

cleanUpAfterTests();

// Interface DELETE /api/friends/{userId}
describe('Unmocked: DELETE /api/friends/{userId}', () => {
  // Input: Uma removes Fay, with no jwt_token and with a jwt_token that isn't a real token
  // Expected status code: 400 with no token, 401 with a bad token
  // Expected behavior: they stay friends and keep their chat and its messages
  // Expected output: { msg: "invalid_request" } and { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    await expectSignInRequired((headers) => api.delete(`/api/friends/${friendId}`).set(headers) as Call);

    expect(await countFriendshipRows(userId, friendId)).toBe(2);
    expect(await getFriendChatIds(userId, friendId)).toEqual([chatId]);
    expect(await getSavedMessages(chatId)).toHaveLength(3);
  });

  // Input: a userId that isn't a positive whole number
  // Expected status code: 400
  // Expected behavior: Uma and Fay stay friends
  // Expected output: { msg: "invalid_request", error: "userId must be a positive whole number" }
  test.each([
    ['"abc"', 'abc'],
    ['0', '0'],
    ['-3', '-3'],
    ['1.5', '1.5'],
  ])('Bad userId %s', async (_name, badId) => {
    const response = await api.delete(`/api/friends/${badId}`).set(asUser(userId));

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'userId must be a positive whole number' });
    expect(await countFriendshipRows(userId, friendId)).toBe(2);
  });

  // Input: Uma removes her own userId
  // Expected status code: 409
  // Expected behavior: nothing changes
  // Expected output: { msg: "own_friend", error: "You can't remove yourself as a friend" }
  test('Cannot remove yourself', async () => {
    const response = await api.delete(`/api/friends/${userId}`).set(asUser(userId));

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'own_friend', error: "You can't remove yourself as a friend" });
    expect(await countFriendshipRows(userId, friendId)).toBe(2);
  });

  // Input: Uma removes a user she isn't friends with (Nia), and a userId no user has
  // Expected status code: 404
  // Expected behavior: nothing changes, Uma and Fay stay friends with their chat
  // Expected output: { msg: "not_friends", error: "You're not friends with this user" }
  test('Not friends with this user', async () => {
    const strangerId = await addUser('Nia');

    for (const otherId of [strangerId, MISSING_ID]) {
      const response = await api.delete(`/api/friends/${otherId}`).set(asUser(userId));

      expect(response.status).toBe(404);
      expect(response.body).toEqual({ msg: 'not_friends', error: "You're not friends with this user" });
    }
    expect(await countFriendshipRows(userId, friendId)).toBe(2);
    expect(await getFriendChatIds(userId, friendId)).toEqual([chatId]);
  });

  // Input: Uma removes Fay
  // Expected status code: 200
  // Expected behavior: the friendship is removed both ways, their chat and its messages are
  //   deleted, neither sees the other in GET /api/friends, and the old chat can no longer be
  //   loaded by either of them
  // Expected output: { msg: "success" }
  test('Removes the friend and deletes the chat', async () => {
    const response = await api.delete(`/api/friends/${friendId}`).set(asUser(userId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(await countFriendshipRows(userId, friendId)).toBe(0);
    expect(await getFriendChatIds(userId, friendId)).toEqual([]);
    expect(await getSavedMessages(chatId)).toEqual([]);

    for (const id of [userId, friendId]) {
      const friends = await api.get('/api/friends').set(asUser(id));
      expect(friends.body).toEqual({ msg: 'success', friends: [] });

      const messages = await api.get(`/api/chats/${chatId}/messages`).set(asUser(id));
      expect(messages.status).toBe(404);
      expect(messages.body).toEqual({ msg: 'chat_not_found', error: 'This chat does not exist' });
    }
  });

  // Input: Uma removes Fay, then removes her again
  // Expected status code: 200, then 404
  // Expected behavior: the second call finds nothing to remove
  // Expected output: { msg: "success" }, then { msg: "not_friends", error: "You're not friends with this user" }
  test('Removing twice', async () => {
    await api.delete(`/api/friends/${friendId}`).set(asUser(userId)).expect(200);

    const again = await api.delete(`/api/friends/${friendId}`).set(asUser(userId));

    expect(again.status).toBe(404);
    expect(again.body).toEqual({ msg: 'not_friends', error: "You're not friends with this user" });
  });

  // Input: Fay has their chat open over the WebSocket, Uma removes her, then Fay sends a message
  // Expected status code: 200 for the removal, the rest are WebSocket replies
  // Expected behavior: nothing is saved and Fay's connection is closed with 4403
  // Expected output: { type: "error", msg: "chat_not_found", error: "This chat does not exist",
  //   client_id: "f-1" }, then the close
  test('Removed friend with the chat open is disconnected', async () => {
    const friend = await connectToChat(PORT, chatId, createAuthToken(friendId));

    await api.delete(`/api/friends/${friendId}`).set(asUser(userId)).expect(200);
    friend.sendMessage(chatId, 'Still there?', 'f-1');

    expect(await friend.nextReply()).toEqual({
      type: 'error', msg: 'chat_not_found', error: 'This chat does not exist', client_id: 'f-1',
    });
    expect(await friend.closed).toEqual({ code: CLOSE_NOT_A_MEMBER, reason: 'This chat does not exist' });
    expect(await getSavedMessages(chatId)).toEqual([]);
  });

  // Input: Uma removes Fay, then Fay sends Uma a new friend request and Uma accepts it
  // Expected status code: 200, 201, 200
  // Expected behavior: they are friends again with a new, empty chat with a different chat_id,
  //   none of the old messages come back
  // Expected output: GET /api/friends lists Fay with the new chat_id, and its messages are empty
  test('Friends again get a new, empty chat', async () => {
    await api.delete(`/api/friends/${friendId}`).set(asUser(userId)).expect(200);

    const sent = await api.post('/api/friends/requests').set(asUser(friendId)).send({ user_id: userId });
    expect(sent.status).toBe(201);
    const requestId = (sent.body as { request: { request_id: number } }).request.request_id;
    await api.patch(`/api/friends/requests/${requestId}`).set(asUser(userId))
      .send({ decision: 'accept' }).expect(200);

    expect(await countFriendshipRows(userId, friendId)).toBe(2);
    const newChatIds = await getFriendChatIds(userId, friendId);
    expect(newChatIds).toHaveLength(1);
    const newChatId = newChatIds[0];
    expect(newChatId).not.toBe(chatId);

    const friends = await api.get('/api/friends').set(asUser(userId));
    expect(friends.body).toEqual({
      msg: 'success',
      friends: [{ user_id: friendId, first_name: 'Fay', last_name: 'Tester', chat_id: newChatId }],
    });
    const messages = await api.get(`/api/chats/${newChatId}/messages`).set(asUser(userId));
    expect(messages.status).toBe(200);
    expect(messages.body).toMatchObject({ msg: 'success', messages: [] });
  });
});
