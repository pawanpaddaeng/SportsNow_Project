import { addUser, api, asUser, cleanUpAfterTests, withToken } from '../helpers';
import { addNotification, getSavedNotifications } from './notificationHelpers';

// when the already read notification in these tests was read
const READ_EARLIER = '2026-01-01 11:00:00';

let userId: number;

beforeEach(async () => {
  userId = await addUser('Ria');
});

cleanUpAfterTests();

// Interface PATCH /api/notifications/read
describe('Unmocked: PATCH /api/notifications/read', () => {
  // Input: no jwt_token, and a jwt_token that isn't a real token, for a user with an unread notification
  // Expected status code: 400 with no token, 401 with a bad token
  // Expected behavior: the notification stays unread
  // Expected output: { msg: "invalid_request" } and { msg: "invalid_token", error: "Invalid jwt_token" }
  test('Rejects a call that is not signed in', async () => {
    await addNotification(userId);

    const noToken = await api.patch('/api/notifications/read');
    expect(noToken.status).toBe(400);
    expect(noToken.body).toMatchObject({ msg: 'invalid_request' });

    const badToken = await api.patch('/api/notifications/read').set(withToken('not-a-real-token'));
    expect(badToken.status).toBe(401);
    expect(badToken.body).toEqual({ msg: 'invalid_token', error: 'Invalid jwt_token' });

    expect((await getSavedNotifications(userId))[0]?.read_at).toBeNull();
  });

  // Input: two unread notifications and one read earlier
  // Expected status code: 200
  // Expected behavior: the unread ones are marked read, the one read earlier keeps when it
  //   was read, and listing them shows nothing unread
  // Expected output: { msg: "success" }, then GET /api/notifications gives unread_count 0
  //   and every notification is_read
  test('Marks every unread notification read', async () => {
    // 1. two unread notifications and one read earlier
    await addNotification(userId);
    await addNotification(userId);
    const readEarlier = await addNotification(userId, { readAt: READ_EARLIER });

    // 2. mark them read through the interface
    const response = await api.patch('/api/notifications/read').set(asUser(userId));

    // 3. check the reply and what was really saved
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    const saved = await getSavedNotifications(userId);
    expect(saved.every((notification) => notification.read_at !== null)).toBe(true);
    expect(saved.find((notification) => notification.notification_id === readEarlier)?.read_at).toBe(READ_EARLIER);

    // 4. the list now shows nothing unread
    const list = await api.get('/api/notifications').set(asUser(userId));
    expect(list.body.unread_count).toBe(0);
    expect((list.body.notifications as { is_read: boolean }[]).every((n) => n.is_read)).toBe(true);
  });

  // Input: an unread notification for the user and one for another user
  // Expected status code: 200
  // Expected behavior: only the signed in user's notification is marked read
  // Expected output: { msg: "success" }
  test('Only the user\'s own notifications are marked read', async () => {
    const otherId = await addUser('Oto');
    await addNotification(userId);
    await addNotification(otherId);

    const response = await api.patch('/api/notifications/read').set(asUser(userId));

    expect(response.status).toBe(200);
    expect((await getSavedNotifications(userId))[0]?.read_at).not.toBeNull();
    expect((await getSavedNotifications(otherId))[0]?.read_at).toBeNull();
  });

  // Input: the user marks their notifications read twice
  // Expected status code: 200 both times
  // Expected behavior: the second time changes nothing, the notification keeps when it was
  //   first read
  // Expected output: { msg: "success" } both times
  test('Marking read twice changes nothing the second time', async () => {
    await addNotification(userId);

    const first = await api.patch('/api/notifications/read').set(asUser(userId));
    const readAt = (await getSavedNotifications(userId))[0]?.read_at;
    const second = await api.patch('/api/notifications/read').set(asUser(userId));

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(second.body).toEqual({ msg: 'success' });
    expect(readAt).not.toBeNull();
    expect((await getSavedNotifications(userId))[0]?.read_at).toBe(readAt);
  });

  // Input: a signed in user with no notifications
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "success" }
  test('User has no notifications', async () => {
    const response = await api.patch('/api/notifications/read').set(asUser(userId));

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'success' });
    expect(await getSavedNotifications(userId)).toEqual([]);
  });
});
