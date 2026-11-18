import * as mysql from '../../../src/core/db/mysql';
import * as logger from '../../../src/core/logger';
import * as push from '../../../src/core/notifications/push';
import * as friendsDb from '../../../src/friend/db/friends';
import * as notificationsDb from '../../../src/notification/db/notifications';
import type * as MockEnv from '../mockEnv';
import {
  api,
  cleanUpAfterTests,
  letNotificationsFinish,
  signIn,
  testOrganizer,
  testPlayer,
  withToken,
} from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const REQUEST_ID = 7;

cleanUpAfterTests();

// Interface PATCH /api/friends/requests/{requestId}
describe('Mocked: PATCH /api/friends/requests/{requestId}', () => {
  // Mocked behavior: decideFriendRequest throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: Olivia answers { decision: "accept" } to request 7
  // Expected status code: 500
  // Expected behavior: the error is handled and nobody is notified
  // Expected output: { msg: "server_error", error: "Could not save your answer, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the receiver (not what this test checks)
    const receiver = testOrganizer();
    const token = signIn(receiver);

    // 2. make saving the answer fail
    const decideFriendRequest = jest.spyOn(friendsDb, 'decideFriendRequest')
      .mockRejectedValue(new Error('database down'));
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. answer through the interface
    const response = await api.patch(`/api/friends/requests/${REQUEST_ID}`).set(withToken(token))
      .send({ decision: 'accept' });

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not save your answer, try again later' });
    expect(decideFriendRequest).toHaveBeenCalledWith(REQUEST_ID, receiver.userId, true);
    expect(sendPushToUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: decideFriendRequest saves nothing and reports that Paul sent request 7.
  //   saveNotifications and sendPushToUser are replaced to check who is told. Sign in is
  //   mocked only so the route can run
  // Input: Olivia answers { decision: "accept" } to request 7
  // Expected status code: 200
  // Expected behavior: the answer is passed on, and Paul gets a friend_request_accepted
  //   notification naming Olivia, saved for his Notifications screen and sent as a push
  // Expected output: { msg: "success" }
  test('Sender is told when the receiver accepts', async () => {
    // 1. sign in as the receiver (not what this test checks)
    const receiver = testOrganizer();
    const token = signIn(receiver);
    const sender = testPlayer();

    // 2. an answer that saves successfully
    const decideFriendRequest = jest.spyOn(friendsDb, 'decideFriendRequest')
      .mockResolvedValue({ ok: true, senderId: sender.userId });
    const saveNotifications = jest.spyOn(notificationsDb, 'saveNotifications').mockResolvedValue();
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. answer through the interface, and let the notification finish
    const response = await api.patch(`/api/friends/requests/${REQUEST_ID}`).set(withToken(token))
      .send({ decision: 'accept' });
    await letNotificationsFinish();

    // 4. check the reply and the notification
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(decideFriendRequest).toHaveBeenCalledWith(REQUEST_ID, receiver.userId, true);
    const body = `${receiver.firstName} accepted your friend request`;
    expect(saveNotifications).toHaveBeenCalledWith([sender.userId], {
      type: 'friend_request_accepted',
      title: 'Friend request accepted',
      body,
      gameId: null,
    });
    expect(sendPushToUser).toHaveBeenCalledTimes(1);
    expect(sendPushToUser).toHaveBeenCalledWith(sender.userId, {
      title: 'Friend request accepted',
      body,
      data: { type: 'friend_request_accepted' },
    });
  });

  // Mocked behavior: decideFriendRequest saves nothing and reports that Paul sent request 7.
  //   saveNotifications and sendPushToUser are replaced to check nobody is told. Sign in is
  //   mocked only so the route can run
  // Input: Olivia answers { decision: "decline" } to request 7
  // Expected status code: 200
  // Expected behavior: the answer is passed on, and Paul is not told he was declined
  // Expected output: { msg: "success" }
  test('Sender is not told when the receiver declines', async () => {
    // 1. sign in as the receiver (not what this test checks)
    const receiver = testOrganizer();
    const token = signIn(receiver);

    // 2. an answer that saves successfully
    const decideFriendRequest = jest.spyOn(friendsDb, 'decideFriendRequest')
      .mockResolvedValue({ ok: true, senderId: testPlayer().userId });
    const saveNotifications = jest.spyOn(notificationsDb, 'saveNotifications').mockResolvedValue();
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. answer through the interface, and give a notification time to be sent
    const response = await api.patch(`/api/friends/requests/${REQUEST_ID}`).set(withToken(token))
      .send({ decision: 'decline' });
    await letNotificationsFinish();

    // 4. check the reply and that nothing was sent
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(decideFriendRequest).toHaveBeenCalledWith(REQUEST_ID, receiver.userId, false);
    expect(saveNotifications).not.toHaveBeenCalled();
    expect(sendPushToUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: decideFriendRequest saves nothing and reports that Paul sent request 7.
  //   sendPushToUser fails, as if Firebase were down. logError is watched to check the
  //   failure is logged. Sign in is mocked only so the route can run
  // Input: Olivia answers { decision: "accept" } to request 7
  // Expected status code: 200, the notification is best effort
  // Expected behavior: the answer is still saved and the failed notification is logged
  // Expected output: { msg: "success" }
  test('Notification fails', async () => {
    // 1. sign in as the receiver (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. an answer that saves, and a notification that fails
    const failure = new Error('firebase down');
    jest.spyOn(friendsDb, 'decideFriendRequest').mockResolvedValue({ ok: true, senderId: testPlayer().userId });
    jest.spyOn(push, 'sendPushToUser').mockRejectedValue(failure);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    // 3. answer through the interface, and let the notification finish
    const response = await api.patch(`/api/friends/requests/${REQUEST_ID}`).set(withToken(token))
      .send({ decision: 'accept' });
    await letNotificationsFinish();

    // 4. check the reply and that the failure was logged
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(logError).toHaveBeenCalledWith('Sending a notification failed:', failure);
  });

  // Mocked behavior: the database transaction is replaced by a fake connection. The first read
  //   finds Paul's pending request 7 to Olivia, locking the two users finds both, but the read
  //   once locked finds nothing, as if the request were deleted while waiting for the lock.
  //   Sign in is mocked only so the route can run
  // Input: Olivia answers { decision: "accept" } to request 7
  // Expected status code: 404
  // Expected behavior: nothing is written and nobody is notified
  // Expected output: { msg: "request_not_found", error: "This friend request does not exist" }
  test('Request is gone once locked', async () => {
    // 1. sign in as the receiver (not what this test checks)
    const receiver = testOrganizer();
    const token = signIn(receiver);
    const sender = testPlayer();

    // 2. a transaction whose reads find the request, then lose it
    const execute = jest.fn()
      .mockResolvedValueOnce([[
        { request_id: REQUEST_ID, sender_id: sender.userId, receiver_id: receiver.userId, status: 'pending' },
      ], []])
      .mockResolvedValueOnce([[{ user_id: receiver.userId }, { user_id: sender.userId }], []])
      .mockResolvedValueOnce([[], []]);
    const connection: mysql.Queryable = { execute };
    jest.spyOn(mysql, 'inTransaction')
      .mockImplementation(<T>(work: (transaction: mysql.Queryable) => Promise<T>): Promise<T> => work(connection));
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. answer through the interface
    const response = await api.patch(`/api/friends/requests/${REQUEST_ID}`).set(withToken(token))
      .send({ decision: 'accept' });

    // 4. check it was not found and only the three reads ran
    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'request_not_found', error: 'This friend request does not exist' });
    expect(execute).toHaveBeenCalledTimes(3);
    expect(sendPushToUser).not.toHaveBeenCalled();
  });
});
