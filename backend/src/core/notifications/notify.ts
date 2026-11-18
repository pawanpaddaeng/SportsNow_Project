import { type NewNotification, saveNotifications } from '../../notification/db/notifications';
import { logError } from '../logger';
import { type PushMessage, sendPushToUser } from './push';

/*  saves the notification for the Notifications screen and sends it as a push to each
    user's devices. The push's data carries the type and game id, so tapping it opens the
    same screen as tapping the saved one. Returns how many devices the push reached.
    A failed save is only logged, so the push is still sent
 */
export async function notifyUsers(userIds: number[], notification: NewNotification): Promise<number> {
  if (userIds.length === 0) return 0;

  const saved = saveNotifications(userIds, notification).catch((error: unknown) => {
    logError(`Saving a ${notification.type} notification failed:`, error);
  });

  const data: Record<string, string> = { type: notification.type };
  if (notification.gameId !== null) data.gameId = String(notification.gameId);
  const push: PushMessage = { title: notification.title, body: notification.body, data };

  const [, reached] = await Promise.all([
    saved,
    Promise.all(userIds.map((userId) => sendPushToUser(userId, push))),
  ]);
  return reached.reduce((total, devices) => total + devices, 0);
}

// notifyUsers for one user
export function notifyUser(userId: number, notification: NewNotification): Promise<number> {
  return notifyUsers([userId], notification);
}
