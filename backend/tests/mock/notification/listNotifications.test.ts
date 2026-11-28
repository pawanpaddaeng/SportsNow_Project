import * as friendsDb from '../../../src/friend/db/friends';
import * as notificationsDb from '../../../src/notification/db/notifications';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, signIn, testOrganizer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/notifications
describe('Mocked: GET /api/notifications', () => {
  // Mocked behavior: getNotifications, or getIncomingFriendRequests, throws as if the
  //   database were down. The other reads are replaced so only that one fails. Sign in is
  //   mocked only so the route can run
  // Input: the organizer asks for their notifications
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not load your notifications, try again later" }
  test.each([
    ['the notifications', 'notifications'],
    ['the friend requests', 'requests'],
  ])('Database fails reading %s', async (_name, failing) => {
    const token = signIn(testOrganizer());
    const failure = new Error('database down');
    jest.spyOn(notificationsDb, 'countUnreadNotifications').mockResolvedValue(0);
    jest.spyOn(notificationsDb, 'getNotifications').mockImplementation(
      () => (failing === 'notifications' ? Promise.reject(failure) : Promise.resolve([])));
    jest.spyOn(friendsDb, 'getIncomingFriendRequests').mockImplementation(
      () => (failing === 'requests' ? Promise.reject(failure) : Promise.resolve([])));

    const response = await api.get('/api/notifications').set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body).toEqual({
      msg: 'server_error',
      error: 'Could not load your notifications, try again later',
    });
  });
});
