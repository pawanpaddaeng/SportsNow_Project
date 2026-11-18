import * as notificationsDb from '../../../src/notification/db/notifications';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface PATCH /api/notifications/read
describe('Mocked: PATCH /api/notifications/read', () => {
  // Mocked behavior: markNotificationsRead is replaced and reports 2 notifications marked.
  //   Sign in is mocked only so the route can run
  // Input: a signed in player marks their notifications read
  // Expected status code: 200
  // Expected behavior: the signed in user's notifications are marked read
  // Expected output: { msg: "success" }
  test('Marks the signed in user\'s notifications read', async () => {
    const player = testPlayer();
    const token = signIn(player);
    const markRead = jest.spyOn(notificationsDb, 'markNotificationsRead').mockResolvedValue(2);

    const response = await api.patch('/api/notifications/read').set(withToken(token));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(markRead).toHaveBeenCalledWith(player.userId);
  });

  // Mocked behavior: markNotificationsRead throws as if the database were down. Sign in is
  //   mocked only so the route can run
  // Input: a signed in player marks their notifications read
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not update your notifications, try again later" }
  test('Database fails', async () => {
    const token = signIn(testPlayer());
    const markRead = jest.spyOn(notificationsDb, 'markNotificationsRead').mockRejectedValue(new Error('database down'));

    const response = await api.patch('/api/notifications/read').set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body).toEqual({
      msg: 'server_error',
      error: 'Could not update your notifications, try again later',
    });
    expect(markRead).toHaveBeenCalledTimes(1);
  });
});
