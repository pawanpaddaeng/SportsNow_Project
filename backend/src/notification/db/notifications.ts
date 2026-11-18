import { executeWrite, selectRows } from '../../core/db/mysql';

// what a notification is about, matches the notifications.type column
export type NotificationType =
  | 'friend_request_accepted'
  | 'join_request'
  | 'join_request_approved'
  | 'join_request_declined'
  | 'game_invite'
  | 'game_starting';

// most notifications the Notifications screen lists, newest first
export const NOTIFICATION_PAGE_SIZE = 50;

// a notification to save for a user
export interface NewNotification {
  type: NotificationType;
  title: string;
  body: string;
  // the game it's about, null for friend notifications
  gameId: number | null;
}

// a saved notification, as the Notifications screen lists it
export interface SavedNotification extends NewNotification {
  notificationId: number;
  // UTC ISO 8601, e.g. "2026-11-18T02:00:00Z"
  createdAt: string;
  isRead: boolean;
}

// saves one notification for each user, unread
export async function saveNotifications(userIds: number[], notification: NewNotification): Promise<void> {
  if (userIds.length === 0) return;
  // one row per user, each with the same 5 values
  const rows = userIds.map(() => '(?, ?, ?, ?, ?)').join(', ');
  const values = userIds.flatMap((userId) =>
    [userId, notification.type, notification.title, notification.body, notification.gameId]);
  await executeWrite(
    `INSERT INTO notifications (user_id, type, title, body, game_id) VALUES ${rows}`,
    values,
  );
}

interface NotificationRow {
  notification_id: number;
  type: NotificationType;
  title: string;
  body: string;
  game_id: number | null;
  // selected as UNIX_TIMESTAMP(created_at) so time zones don't affect it
  created_at_unix: number;
  is_read: number;
}

// the user's newest NOTIFICATION_PAGE_SIZE notifications, newest first
export async function getNotifications(userId: number): Promise<SavedNotification[]> {
  const rows = await selectRows<NotificationRow>(
    `SELECT notification_id, type, title, body, game_id,
            UNIX_TIMESTAMP(created_at) AS created_at_unix, read_at IS NOT NULL AS is_read
     FROM notifications
     WHERE user_id = ?
     ORDER BY created_at DESC, notification_id DESC
     LIMIT ${NOTIFICATION_PAGE_SIZE}`,
    [userId],
  );
  return rows.map((row) => ({
    notificationId: row.notification_id,
    type: row.type,
    title: row.title,
    body: row.body,
    gameId: row.game_id,
    createdAt: new Date(Number(row.created_at_unix) * 1000).toISOString().replace('.000Z', 'Z'),
    isRead: Number(row.is_read) === 1,
  }));
}

// how many of the user's notifications they haven't seen yet, for the badge on the bell
export async function countUnreadNotifications(userId: number): Promise<number> {
  const rows = await selectRows<{ unread: number }>(
    'SELECT COUNT(*) AS unread FROM notifications WHERE user_id = ? AND read_at IS NULL',
    [userId],
  );
  return Number(rows.at(0)?.unread ?? 0);
}

// marks all the user's notifications as seen, returns how many were unread
export async function markNotificationsRead(userId: number): Promise<number> {
  const result = await executeWrite(
    'UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE user_id = ? AND read_at IS NULL',
    [userId],
  );
  return result.affectedRows;
}
