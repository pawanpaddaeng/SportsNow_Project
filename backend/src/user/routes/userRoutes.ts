import { Router, type Request, type Response } from 'express';
import { getAuthUser, requireAuth } from '../auth/middleware';
import { deleteUser, findUserById, updateUserProfile, type User, userJson } from '../db/users';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logInfo } from '../../core/logger';
import { type PushMessage, sendPushToUsers } from '../../core/notifications/push';
import { getActiveGamesOfUser, getUsersToNotify } from '../../game/db/games';
import { logNotificationError } from '../../game/routes/gameErrors';
import { parseProfileFields, type ProfileBody } from '../validation/profile';

// a notification to send once the account is deleted
interface Notice {
  userIds: number[];
  message: PushMessage;
}

/*  who to tell about the user's games that are still on, worked out before deleting the
    account since its game players and join requests are deleted with it. Players and
    pending requesters of games they organize are told it's cancelled, organizers of
    games they joined that a player left
 */
async function accountDeletedNotices(user: User): Promise<Notice[]> {
  const games = await getActiveGamesOfUser(user.userId);
  return Promise.all(games.map(async (game): Promise<Notice> => {
    const gameId = String(game.gameId);
    if (game.organizerId === user.userId) {
      return {
        userIds: await getUsersToNotify(game.gameId, user.userId),
        message: {
          title: 'Game cancelled',
          body: `${game.title} at ${game.locationName} was cancelled, the organizer deleted their account`,
          data: { type: 'game_cancelled', gameId },
        },
      };
    }
    return {
      userIds: [game.organizerId],
      message: {
        title: 'Player left',
        body: `${user.firstName} left ${game.title}`,
        data: { type: 'player_left', gameId },
      },
    };
  }));
}

// best effort: the app gets its reply without waiting, failures are only logged
function sendNotices(notices: Notice[]): void {
  for (const notice of notices) {
    sendPushToUsers(notice.userIds, notice.message)
      .then((devices) => { logInfo(`"${notice.message.title}" notification sent to ${devices} device(s)`); })
      .catch(logNotificationError);
  }
}

// the signed in user's profile
export function usersRouter(): Router {
  const router = Router();

  router.get('/api/users/me', requireAuth, (_req: Request, res: Response) => {
    const user = getAuthUser(res);
    return res.status(200).json({ msg: 'success', user: userJson(user)});
  });

  router.put('/api/users/me', requireAuth, asyncRoute('Could not update profile, try again later',
    async (req: Request<Record<string, never>, unknown, ProfileBody | undefined>, res: Response) => {
      const parsed = parseProfileFields(req.body);
      if(!parsed.ok) return invalidRequest(res, parsed.error);

      const { userId } = getAuthUser(res);
      await updateUserProfile(userId, parsed.value);

      const updated = await findUserById(userId);
      if(!updated) {
        return res.status(404).json({ msg: 'no_user', error: 'No account found, sign up or sign in again' });
      }
      return res.status(200).json({ msg: 'success', user: userJson(updated) });
    }));

  /*  delete account: deletes the signed in user with everything tied to them (see
      deleteUser). Players of their games that are still on are told the game was
      cancelled, and organizers of games they joined that they left. Responds msg "success"
   */
  router.delete('/api/users/me', requireAuth, asyncRoute('Could not delete account, try again later',
    async (_req: Request, res: Response) => {
      const user = getAuthUser(res);
      const notices = await accountDeletedNotices(user);

      await deleteUser(user.userId);
      logInfo(`User deleted id: ${user.userId}`);

      sendNotices(notices);
      return res.status(200).json({ msg: 'success' });
    }));

  return router;
}
