import { executeWrite, selectRows } from '../../../src/core/db/mysql';
import { type NotificationType } from '../../../src/notification/db/notifications';

// setup and checks shared by the unmocked notification tests, straight on the database

export interface TestNotification {
  type?: NotificationType;
  title?: string;
  body?: string;
  gameId?: number | null;
  // when it was made, e.g. "2026-01-01 10:00:00", so tests can check the order. Now by default
  createdAt?: string | null;
  // when it was read, e.g. "2026-01-02 10:00:00", null while unread
  readAt?: string | null;
}

/*  saves a notification for the user straight in the table and returns its id. By default
    an unread "Friend request accepted" one made now, with overrides for what the test is about
 */
export async function addNotification(userId: number, notification: TestNotification = {}): Promise<number> {
  const { insertId } = await executeWrite(
    `INSERT INTO notifications (user_id, type, title, body, game_id, created_at, read_at)
     VALUES (?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), ?)`,
    [
      userId,
      notification.type ?? 'friend_request_accepted',
      notification.title ?? 'Friend request accepted',
      notification.body ?? 'Sam accepted your friend request',
      notification.gameId ?? null,
      notification.createdAt ?? null,
      notification.readAt ?? null,
    ],
  );
  return insertId;
}

// saves count unread notifications for the user, all made at the same time
export async function addNotifications(userId: number, count: number): Promise<void> {
  const rows = Array<string>(count).fill("(?, 'friend_request_accepted', 'Title', 'Body')").join(', ');
  await executeWrite(
    `INSERT INTO notifications (user_id, type, title, body) VALUES ${rows}`,
    Array<number>(count).fill(userId),
  );
}

// a notification as really saved, for checking what a route saved
export interface SavedNotice {
  type: string;
  title: string;
  body: string;
  game_id: number | null;
}

// every notification really saved for the user, oldest first
export async function getNotices(userId: number): Promise<SavedNotice[]> {
  return selectRows<SavedNotice>(
    'SELECT type, title, body, game_id FROM notifications WHERE user_id = ? ORDER BY notification_id',
    [userId],
  );
}

// how long to wait for a notification a route saves after it replies
const SAVE_WAIT_MS = 3000;
const CHECK_EVERY_MS = 50;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => { setTimeout(resolve, ms); });
}

/*  routes reply before their notification is saved, so this waits until the user has count
    notifications (or SAVE_WAIT_MS passed) and returns what was saved
 */
export async function waitForNotices(userId: number, count = 1): Promise<SavedNotice[]> {
  for (let waited = 0; waited < SAVE_WAIT_MS; waited += CHECK_EVERY_MS) {
    const saved = await getNotices(userId);
    if (saved.length >= count) return saved;
    await sleep(CHECK_EVERY_MS);
  }
  return getNotices(userId);
}

// gives a notification a route might still save time to be saved, to check none was
export async function noticesAfterAWhile(userId: number): Promise<SavedNotice[]> {
  await sleep(CHECK_EVERY_MS * 6);
  return getNotices(userId);
}

export interface SavedNotificationRow {
  notification_id: number;
  // selected as a string so the exact value saved can be compared
  read_at: string | null;
}

// the user's notifications really saved, oldest id first, with when each was read
export async function getSavedNotifications(userId: number): Promise<SavedNotificationRow[]> {
  return selectRows<SavedNotificationRow>(
    `SELECT notification_id, DATE_FORMAT(read_at, '%Y-%m-%d %H:%i:%s') AS read_at
     FROM notifications WHERE user_id = ? ORDER BY notification_id`,
    [userId],
  );
}
