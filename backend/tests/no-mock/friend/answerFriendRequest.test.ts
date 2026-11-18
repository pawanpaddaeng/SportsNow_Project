import { addUser, api, asUser, cleanUpAfterTests, withToken } from '../helpers';
import { noticesAfterAWhile, waitForNotices } from '../notification/notificationHelpers';
import {
  addFriendRequest,
  countFriendshipRows,
  getFriendChatIds,
  getFriendRequest,
  MISSING_ID,
} from './friendHelpers';

let senderId: number;
let receiverId: number;
let requestId: number;

// every test starts with a pending request from Sid to Rae
beforeEach(async () => {
  senderId = await addUser('Sid');
  receiverId = await addUser('Rae');
  requestId = await addFriendRequest(senderId, receiverId);
});

cleanUpAfterTests();

// Interface PATCH /api/friends/requests/{requestId}
describe('Unmocked: PATCH /api/friends/requests/{requestId}', () => {
  // Input: { decision: "accept" } for the pending request, with no jwt_token and with a
  //   jwt_token that isn't a real token
  // Expected status code: 400 with no token, 401 with a bad token
  // Expected behavior: the request stays pending
  // Expected output: { msg: "invalid_request" } and { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const noToken = await api.patch(`/api/friends/requests/${requestId}`).send({ decision: 'accept' });
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.patch(`/api/friends/requests/${requestId}`).set(withToken('not-a-real-token'))
      .send({ decision: 'accept' });
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });

    expect((await getFriendRequest(requestId))?.status).toBe('pending');
  });

  // Input: a requestId that isn't a positive whole number, or a decision that is missing or
  //   isn't "accept" or "decline"
  // Expected status code: 400
  // Expected behavior: the real request stays pending
  // Expected output: { msg: "invalid_request", error } naming the bad input
  test.each([
    ['requestId "abc"', 'abc', { decision: 'accept' }, 'requestId must be a positive whole number'],
    ['requestId 0', '0', { decision: 'accept' }, 'requestId must be a positive whole number'],
    ['no decision', null, {}, 'decision must be "accept" or "decline"'],
    ['decision "maybe"', null, { decision: 'maybe' }, 'decision must be "accept" or "decline"'],
  ])('Bad input: %s', async (_name, badId, body, error) => {
    const response = await api.patch(`/api/friends/requests/${badId ?? String(requestId)}`)
      .set(asUser(receiverId)).send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error });
    expect((await getFriendRequest(requestId))?.status).toBe('pending');
  });

  // Input: { decision: "accept" } for a request id no request has
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "request_not_found", error: "This friend request does not exist" }
  test('Request does not exist', async () => {
    const response = await api.patch(`/api/friends/requests/${MISSING_ID}`).set(asUser(receiverId))
      .send({ decision: 'accept' });

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'request_not_found', error: 'This friend request does not exist' });
  });

  // Input: { decision: "accept" } for Sid's request to Rae, answered by Sid (the sender) and by a third user
  // Expected status code: 404, a request to someone else is treated as not found
  // Expected behavior: the request stays pending and nobody becomes friends
  // Expected output: { msg: "request_not_found", error: "This friend request does not exist" }
  test('Only the receiver can answer', async () => {
    const otherId = await addUser('Oli');

    for (const answererId of [senderId, otherId]) {
      const response = await api.patch(`/api/friends/requests/${requestId}`).set(asUser(answererId))
        .send({ decision: 'accept' });

      expect(response.status).toBe(404);
      expect(response.body).toEqual({ msg: 'request_not_found', error: 'This friend request does not exist' });
    }
    expect((await getFriendRequest(requestId))?.status).toBe('pending');
    expect(await countFriendshipRows(senderId, receiverId)).toBe(0);
  });

  // Input: Rae answers { decision: "accept" } to Sid's pending request
  // Expected status code: 200
  // Expected behavior: the request is accepted and answered, the two become friends both
  //   ways with one shared chat, and each sees the other in GET /api/friends with that chat.
  //   Sid gets a friend_request_accepted notification saved for his Notifications screen (he
  //   has no device, so no push leaves the server)
  // Expected output: { msg: "success" }
  test('Receiver accepts', async () => {
    const response = await api.patch(`/api/friends/requests/${requestId}`).set(asUser(receiverId))
      .send({ decision: 'accept' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(await getFriendRequest(requestId)).toMatchObject({ status: 'accepted', answered: 1 });
    expect(await countFriendshipRows(senderId, receiverId)).toBe(2);
    const chatIds = await getFriendChatIds(senderId, receiverId);
    expect(chatIds).toHaveLength(1);

    const sendersFriends = await api.get('/api/friends').set(asUser(senderId));
    expect(sendersFriends.body).toEqual({
      msg: 'success',
      friends: [{ user_id: receiverId, first_name: 'Rae', last_name: 'Tester', chat_id: chatIds[0] }],
    });
    const receiversFriends = await api.get('/api/friends').set(asUser(receiverId));
    expect(receiversFriends.body).toEqual({
      msg: 'success',
      friends: [{ user_id: senderId, first_name: 'Sid', last_name: 'Tester', chat_id: chatIds[0] }],
    });
    expect(await waitForNotices(senderId)).toEqual([{
      type: 'friend_request_accepted',
      title: 'Friend request accepted',
      body: 'Rae accepted your friend request',
      game_id: null,
    }]);
  });

  // Input: Rae answers { decision: "decline" } to Sid's pending request
  // Expected status code: 200
  // Expected behavior: the request is declined and answered, they don't become friends,
  //   no chat is opened, and Sid isn't told he was declined
  // Expected output: { msg: "success" }
  test('Receiver declines', async () => {
    const response = await api.patch(`/api/friends/requests/${requestId}`).set(asUser(receiverId))
      .send({ decision: 'decline' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(await getFriendRequest(requestId)).toMatchObject({ status: 'declined', answered: 1 });
    expect(await countFriendshipRows(senderId, receiverId)).toBe(0);
    expect(await getFriendChatIds(senderId, receiverId)).toEqual([]);
    expect(await noticesAfterAWhile(senderId)).toEqual([]);
  });

  // Input: Rae answers a request she already accepted, once with "decline" and once with "accept"
  // Expected status code: 409
  // Expected behavior: the first answer stands, they stay friends with one chat
  // Expected output: { msg: "already_responded", error: "This request was already answered" }
  test('A request can only be answered once', async () => {
    await api.patch(`/api/friends/requests/${requestId}`).set(asUser(receiverId))
      .send({ decision: 'accept' }).expect(200);

    for (const decision of ['decline', 'accept']) {
      const response = await api.patch(`/api/friends/requests/${requestId}`).set(asUser(receiverId))
        .send({ decision });

      expect(response.status).toBe(409);
      expect(response.body).toEqual({ msg: 'already_responded', error: 'This request was already answered' });
    }
    expect((await getFriendRequest(requestId))?.status).toBe('accepted');
    expect(await countFriendshipRows(senderId, receiverId)).toBe(2);
    expect(await getFriendChatIds(senderId, receiverId)).toHaveLength(1);
  });
});
