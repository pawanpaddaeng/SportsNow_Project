import { executeWrite } from '../../../src/core/db/mysql';
import { NOTIFICATION_PAGE_SIZE } from '../../../src/notification/db/notifications';
import { addFriendRequest } from '../friend/friendHelpers';
import { addGame, addUser, api, asUser, cleanUpAfterTests, withToken } from '../helpers';
import { addNotification, addNotifications } from './notificationHelpers';

// how the server writes created_at, e.g. "2026-11-18T02:00:00Z"
const UTC_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;

let userId: number;

beforeEach(async () => {
  userId = await addUser('Nia');
});

cleanUpAfterTests();

// Interface GET /api/notifications
describe('Unmocked: GET /api/notifications', () => {
  // Input: no jwt_token, and a jwt_token that isn't a real token
  // Expected status code: 400 with no token, 401 with a bad token
  // Expected behavior: no notifications are read
  // Expected output: { msg: "invalid_request" } and { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    const noToken = await api.get('/api/notifications');
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.get('/api/notifications').set(withToken('not-a-real-token'));
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });
  });

  // Input: a signed in user with no notifications and no friend requests
  // Expected status code: 200
  // Expected behavior: nothing is listed and both counts are 0
  // Expected output: { msg: "success", notifications: [], unread_count: 0, friend_request_count: 0 }
  test('User has no notifications', async () => {
    const response = await api.get('/api/notifications').set(asUser(userId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', notifications: [], unread_count: 0, friend_request_count: 0 });
  });

  // Input: a read friend notification (oldest), an unread join request for a game, and two
  //   unread invites made at the same time (the second saved last)
  // Expected status code: 200
  // Expected behavior: newest first, notifications made at the same time newest id first,
  //   and only the unread ones counted
  // Expected output: { msg: "success", notifications, unread_count: 3, friend_request_count: 0 }
  //   in the order second invite, first invite, join request, friend notification, each
  //   { notification_id, type, title, body, game_id, created_at, is_read }
  test('Notifications are listed newest first', async () => {
    // 1. notifications made at known times
    const gameId = await addGame(userId);
    const friendNote = await addNotification(userId, {
      createdAt: '2026-01-01 10:00:00', readAt: '2026-01-01 11:00:00',
    });
    const joinNote = await addNotification(userId, {
      type: 'join_request', title: 'New join request', body: 'Pat wants to join Evening Basketball',
      gameId, createdAt: '2026-01-02 10:00:00',
    });
    const invite = { type: 'game_invite' as const, title: 'Game invite', gameId, createdAt: '2026-01-03 10:00:00' };
    const firstInvite = await addNotification(userId, { ...invite, body: 'Amy invited you to Evening Basketball' });
    const secondInvite = await addNotification(userId, { ...invite, body: 'Bob invited you to Evening Basketball' });

    // 2. list them through the interface
    const response = await api.get('/api/notifications').set(asUser(userId));

    // 3. check the order, the counts and what each notification holds
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ msg: 'success', unread_count: 3, friend_request_count: 0 });
    const notifications = response.body.notifications as Record<string, unknown>[];
    expect(notifications.map((n) => n.notification_id)).toEqual([secondInvite, firstInvite, joinNote, friendNote]);
    expect(notifications[0]).toEqual({
      notification_id: secondInvite,
      type: 'game_invite',
      title: 'Game invite',
      body: 'Bob invited you to Evening Basketball',
      game_id: gameId,
      created_at: expect.stringMatching(UTC_TIME),
      is_read: false,
    });
    expect(notifications[3]).toEqual({
      notification_id: friendNote,
      type: 'friend_request_accepted',
      title: 'Friend request accepted',
      body: 'Sam accepted your friend request',
      game_id: null,
      created_at: expect.stringMatching(UTC_TIME),
      is_read: true,
    });
  });

  // Input: two notifications for another user, and one for the signed in user
  // Expected status code: 200
  // Expected behavior: only the user's own notification is listed and counted
  // Expected output: { msg: "success", notifications } holding only the user's one, unread_count: 1
  test('Only the user\'s own notifications are listed', async () => {
    const otherId = await addUser('Oto');
    await addNotification(otherId);
    await addNotification(otherId);
    const ownNote = await addNotification(userId);

    const response = await api.get('/api/notifications').set(asUser(userId));

    expect(response.status).toBe(200);
    expect(response.body.unread_count).toBe(1);
    expect((response.body.notifications as { notification_id: number }[]).map((n) => n.notification_id))
      .toEqual([ownNote]);
  });

  // Input: 5 more unread notifications than the screen lists (NOTIFICATION_PAGE_SIZE + 5)
  // Expected status code: 200
  // Expected behavior: only the newest NOTIFICATION_PAGE_SIZE are listed, but every unread
  //   one is counted for the badge
  // Expected output: { msg: "success" } with NOTIFICATION_PAGE_SIZE notifications and
  //   unread_count NOTIFICATION_PAGE_SIZE + 5
  test('At most the newest 50 are listed, every unread one is counted', async () => {
    const total = NOTIFICATION_PAGE_SIZE + 5;
    await addNotifications(userId, total);

    const response = await api.get('/api/notifications').set(asUser(userId));

    expect(response.status).toBe(200);
    expect(response.body.notifications).toHaveLength(NOTIFICATION_PAGE_SIZE);
    expect(response.body.unread_count).toBe(total);
  });

  // Input: two pending friend requests to the user, and an accepted and a declined one to
  //   the user, a pending one the user sent, and a pending one between two other users
  // Expected status code: 200
  // Expected behavior: only requests to the user still waiting for an answer are counted,
  //   and friend requests aren't listed as notifications
  // Expected output: { msg: "success", notifications: [], unread_count: 0, friend_request_count: 2 }
  test('Friend requests waiting for the user are counted', async () => {
    const otherId = await addUser('Oli');
    await addFriendRequest(await addUser('Amy'), userId, 'pending');
    await addFriendRequest(await addUser('Bob'), userId, 'pending');
    await addFriendRequest(await addUser('Acc'), userId, 'accepted');
    await addFriendRequest(await addUser('Dec'), userId, 'declined');
    await addFriendRequest(userId, await addUser('Asked'), 'pending');
    await addFriendRequest(otherId, await addUser('Pat'), 'pending');

    const response = await api.get('/api/notifications').set(asUser(userId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success', notifications: [], unread_count: 0, friend_request_count: 2 });
  });

  // Input: a notification about a game, and one not about a game, then the game is deleted
  // Expected status code: 200
  // Expected behavior: the deleted game's notification goes with it (it would open nothing),
  //   the other stays
  // Expected output: { msg: "success" } listing only the notification not about the game, unread_count: 1
  test('A deleted game\'s notifications are removed', async () => {
    const gameId = await addGame(userId);
    await addNotification(userId, { type: 'game_starting', title: 'Game starting soon', gameId });
    const friendNote = await addNotification(userId);
    await executeWrite('DELETE FROM games WHERE game_id = ?', [gameId]);

    const response = await api.get('/api/notifications').set(asUser(userId));

    expect(response.status).toBe(200);
    expect(response.body.unread_count).toBe(1);
    expect((response.body.notifications as { notification_id: number }[]).map((n) => n.notification_id))
      .toEqual([friendNote]);
  });
});
