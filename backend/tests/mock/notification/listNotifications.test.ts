import * as friendsDb from '../../../src/friend/db/friends';
import * as notificationsDb from '../../../src/notification/db/notifications';
import { type SavedNotification } from '../../../src/notification/db/notifications';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, GAME_ID, signIn, testOrganizer, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// a join request the organizer hasn't seen, and an accepted friend request they have
function testNotifications(): SavedNotification[] {
  return [
    {
      notificationId: 12,
      type: 'join_request',
      title: 'New join request',
      body: 'Paul wants to join Sunday Soccer',
      gameId: GAME_ID,
      createdAt: '2026-11-18T10:00:00Z',
      isRead: false,
    },
    {
      notificationId: 11,
      type: 'friend_request_accepted',
      title: 'Friend request accepted',
      body: 'Paul accepted your friend request',
      gameId: null,
      createdAt: '2026-11-17T10:00:00Z',
      isRead: true,
    },
  ];
}

// Interface GET /api/notifications
describe('Mocked: GET /api/notifications', () => {
  // Mocked behavior: getNotifications returns two notifications, countUnreadNotifications
  //   returns 1 and getIncomingFriendRequests returns one pending request. Sign in is mocked
  //   only so the route can run
  // Input: the organizer asks for their notifications
  // Expected status code: 200
  // Expected behavior: everything is read for the signed in user and sent in the app's JSON shape
  // Expected output: { msg: "success", notifications, unread_count: 1, friend_request_count: 1 }
  test('Returns the notifications and both counts', async () => {
    const organizer = testOrganizer();
    const token = signIn(organizer);
    const getNotifications = jest.spyOn(notificationsDb, 'getNotifications').mockResolvedValue(testNotifications());
    const countUnread = jest.spyOn(notificationsDb, 'countUnreadNotifications').mockResolvedValue(1);
    const getRequests = jest.spyOn(friendsDb, 'getIncomingFriendRequests').mockResolvedValue([
      { requestId: 3, sender: { userId: testPlayer().userId, firstName: 'Paul', lastName: 'Player' } },
    ]);

    const response = await api.get('/api/notifications').set(withToken(token));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      msg: 'success',
      notifications: [
        {
          notification_id: 12,
          type: 'join_request',
          title: 'New join request',
          body: 'Paul wants to join Sunday Soccer',
          game_id: GAME_ID,
          created_at: '2026-11-18T10:00:00Z',
          is_read: false,
        },
        {
          notification_id: 11,
          type: 'friend_request_accepted',
          title: 'Friend request accepted',
          body: 'Paul accepted your friend request',
          game_id: null,
          created_at: '2026-11-17T10:00:00Z',
          is_read: true,
        },
      ],
      unread_count: 1,
      friend_request_count: 1,
    });
    expect(getNotifications).toHaveBeenCalledWith(organizer.userId);
    expect(countUnread).toHaveBeenCalledWith(organizer.userId);
    expect(getRequests).toHaveBeenCalledWith(organizer.userId);
  });

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
