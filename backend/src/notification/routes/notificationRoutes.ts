import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { getIncomingFriendRequests } from '../../friend/db/friends';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import {
  countUnreadNotifications,
  getNotifications,
  markNotificationsRead,
  type SavedNotification,
} from '../db/notifications';

function notificationJson(notification: SavedNotification) {
  return {
    notification_id: notification.notificationId,
    type: notification.type,
    title: notification.title,
    body: notification.body,
    game_id: notification.gameId,
    created_at: notification.createdAt,
    is_read: notification.isRead,
  };
}

// the Notifications screen: listing the signed in user's notifications and marking them seen
export function notificationsRouter(): Router {
  const router = Router();

  /*  the user's newest notifications, newest first, with unread_count for the badge on the
      bell and friend_request_count for the Friend requests button (friend requests aren't
      notifications, they're answered on their own screen)
   */
  router.get('/api/notifications', requireAuth, asyncRoute('Could not load your notifications, try again later',
    async (_req: Request, res: Response) => {
      const userId = getAuthUser(res).userId;
      const [notifications, unreadCount, friendRequests] = await Promise.all([
        getNotifications(userId),
        countUnreadNotifications(userId),
        getIncomingFriendRequests(userId),
      ]);
      return res.status(200).json({
        msg: 'success',
        notifications: notifications.map(notificationJson),
        unread_count: unreadCount,
        friend_request_count: friendRequests.length,
      });
    }));

  // marks all the user's notifications as seen, once the Notifications screen showed them
  router.patch('/api/notifications/read', requireAuth, asyncRoute('Could not update your notifications, try again later',
    async (_req: Request, res: Response) => {
      await markNotificationsRead(getAuthUser(res).userId);
      return res.status(200).json({ msg: 'success' });
    }));

  return router;
}
