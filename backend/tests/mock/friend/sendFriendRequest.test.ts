import * as mysql from '../../../src/core/db/mysql';
import * as logger from '../../../src/core/logger';
import * as deviceTokens from '../../../src/core/notifications/deviceTokens';
import * as push from '../../../src/core/notifications/push';
import * as friendsDb from '../../../src/friend/db/friends';
import { waitUntil } from '../../chatSocketClient';
import { fakeFirebase } from '../fakeFirebase';
import type * as FakeFirebase from '../fakeFirebase';
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
jest.mock('firebase-admin/app', () => jest.requireActual<typeof FakeFirebase>('../fakeFirebase'));
jest.mock('firebase-admin/messaging', () => jest.requireActual<typeof FakeFirebase>('../fakeFirebase'));

const REQUEST_ID = 7;

cleanUpAfterTests();

// Interface POST /api/friends/requests
describe('Mocked: POST /api/friends/requests', () => {
  // Mocked behavior: sendFriendRequest throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: Olivia sends a request to Paul ({ user_id: 2 })
  // Expected status code: 500
  // Expected behavior: the error is handled and nobody is notified
  // Expected output: { msg: "server_error", error: "Could not send your friend request, try again later" }
  test('Database fails', async () => {
    // 1. sign in (not what this test checks)
    const sender = testOrganizer();
    const token = signIn(sender);
    const receiver = testPlayer();

    // 2. make saving the request fail
    const sendFriendRequest = jest.spyOn(friendsDb, 'sendFriendRequest').mockRejectedValue(new Error('database down'));
    const sendPushToUser = jest.spyOn(push, 'sendPushToUser').mockResolvedValue(1);

    // 3. send the request through the interface
    const response = await api.post('/api/friends/requests').set(withToken(token)).send({ user_id: receiver.userId });

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({
      msg: 'server_error',
      error: 'Could not send your friend request, try again later',
    });
    expect(sendFriendRequest).toHaveBeenCalledWith(sender.userId, receiver.userId);
    expect(sendPushToUser).not.toHaveBeenCalled();
  });

  // Mocked behavior: sendFriendRequest saves nothing and reports a new request 7.
  //   sendPushToUser fails, as if Firebase were down. logError is watched to check the
  //   failure is logged. Sign in is mocked only so the route can run
  // Input: Olivia sends a request to Paul ({ user_id: 2 })
  // Expected status code: 201, the notification is best effort
  // Expected behavior: the request is still sent and the failed notification is logged
  // Expected output: { msg: "success", request: { request_id: 7, user_id: 2, status: "pending" } }
  test('Notification fails', async () => {
    // 1. sign in as the sender (not what this test checks)
    const token = signIn(testOrganizer());
    const receiver = testPlayer();

    // 2. a request that saves, and a notification that fails
    const failure = new Error('firebase down');
    jest.spyOn(friendsDb, 'sendFriendRequest').mockResolvedValue({ ok: true, requestId: REQUEST_ID });
    jest.spyOn(push, 'sendPushToUser').mockRejectedValue(failure);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    // 3. send the request through the interface, and let the notification finish
    const response = await api.post('/api/friends/requests').set(withToken(token)).send({ user_id: receiver.userId });
    await letNotificationsFinish();

    // 4. check the reply and that the failure was logged
    expect(response.status).toBe(201);
    expect(response.body).toEqual({
      msg: 'success',
      request: { request_id: REQUEST_ID, user_id: receiver.userId, status: 'pending' },
    });
    expect(logError).toHaveBeenCalledWith('Sending a notification failed:', failure);
  });

  // Mocked behavior: sendFriendRequest reports a new request 7. The database query
  //   (selectRows) finds two devices for Paul. Firebase is replaced: it hasn't started yet, and it answers the send
  //   with one success and one "not registered", a token that will never work again. The
  //   database write (executeWrite) is watched. Sign in is mocked only so the route can run
  // Input: Olivia sends a request to Paul ({ user_id: 2 })
  // Expected status code: 201
  // Expected behavior: Firebase is started once, the push goes to both of Paul's devices, and
  //   the dead token is deleted
  // Expected output: { msg: "success", request: { request_id: 7, user_id: 2, status: "pending" } }
  test('Receiver\'s devices get the push and a dead token is removed', async () => {
    // 1. sign in as the sender (not what this test checks)
    fakeFirebase.reset();
    const sender = testOrganizer();
    const token = signIn(sender);
    const receiver = testPlayer();

    // 2. a request that saves, a receiver with two devices, and Firebase's answer
    jest.spyOn(friendsDb, 'sendFriendRequest').mockResolvedValue({ ok: true, requestId: REQUEST_ID });
    const selectRows = jest.spyOn(mysql, 'selectRows').mockResolvedValue([{ token: 'token-a' }, { token: 'token-b' }]);
    const executeWrite = jest.spyOn(mysql, 'executeWrite').mockResolvedValue({ insertId: 0, affectedRows: 1, changedRows: 0 });
    fakeFirebase.sendEachForMulticast.mockResolvedValue({
      successCount: 1,
      responses: [{ success: true }, { success: false, error: { code: 'messaging/registration-token-not-registered' } }],
    });

    // 3. send the request through the interface, and let the push finish
    const response = await api.post('/api/friends/requests').set(withToken(token)).send({ user_id: receiver.userId });
    await waitUntil(() => executeWrite.mock.calls.length > 0);

    // 4. check the reply, the push, and that only the dead token was deleted
    expect(response.status).toBe(201);
    expect(response.body).toEqual({
      msg: 'success',
      request: { request_id: REQUEST_ID, user_id: receiver.userId, status: 'pending' },
    });
    expect(selectRows).toHaveBeenCalledWith('SELECT token FROM device_tokens WHERE user_id = ?', [receiver.userId]);
    expect(fakeFirebase.initializeApp).toHaveBeenCalledTimes(1);
    expect(fakeFirebase.initializeApp).toHaveBeenCalledWith({ credential: 'default-credential' });
    expect(fakeFirebase.sendEachForMulticast).toHaveBeenCalledWith({
      tokens: ['token-a', 'token-b'],
      notification: { title: 'New friend request', body: `${sender.firstName} sent you a friend request` },
      data: { type: 'friend_request' },
    });
    expect(executeWrite).toHaveBeenCalledWith('DELETE FROM device_tokens WHERE token IN (?)', ['token-b']);
  });

  // Mocked behavior: sendFriendRequest reports a new request 7. Paul has one device. Firebase
  //   is replaced: it has already started, and the send throws, as if Firebase were down.
  //   logError is watched. Sign in is mocked only so the route can run
  // Input: Olivia sends a request to Paul ({ user_id: 2 })
  // Expected status code: 201, the notification is best effort
  // Expected behavior: Firebase isn't started again, the failed send is logged, and the
  //   request is still sent
  // Expected output: { msg: "success", request: { request_id: 7, user_id: 2, status: "pending" } }
  test('Firebase fails to send the push', async () => {
    // 1. sign in as the sender (not what this test checks)
    fakeFirebase.reset();
    fakeFirebase.apps = [{}];
    const token = signIn(testOrganizer());
    const receiver = testPlayer();

    // 2. a request that saves, a receiver with one device, and a send that fails
    const failure = new Error('firebase down');
    jest.spyOn(friendsDb, 'sendFriendRequest').mockResolvedValue({ ok: true, requestId: REQUEST_ID });
    jest.spyOn(deviceTokens, 'getDeviceTokens').mockResolvedValue(['token-a']);
    fakeFirebase.sendEachForMulticast.mockRejectedValue(failure);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    // 3. send the request through the interface, and let the push finish
    const response = await api.post('/api/friends/requests').set(withToken(token)).send({ user_id: receiver.userId });
    await waitUntil(() => logError.mock.calls.length > 0);

    // 4. check the reply and that the failure was logged
    expect(response.status).toBe(201);
    expect(fakeFirebase.initializeApp).not.toHaveBeenCalled();
    expect(logError).toHaveBeenCalledWith(`Sending push notification to user ${receiver.userId} failed:`, failure);
  });
});
