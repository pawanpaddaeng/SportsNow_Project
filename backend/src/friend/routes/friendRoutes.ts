import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logInfo } from '../../core/logger';
import { type ErrorResponse, readGameId, responseFor, sendError } from '../../game/routes/gameErrors';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import { getFriends, removeFriend, type RemoveFriendProblem, searchUsers } from '../db/friends';
import { friendUserJson } from './friendJson';

// longest search text, longer than any name can be
const MAX_SEARCH_LENGTH = 100;

const REMOVE_ERRORS: Record<RemoveFriendProblem, ErrorResponse> = {
  own_friend: { status: 409, body: { msg: 'own_friend', error: "You can't remove yourself as a friend" } },
  not_friends: { status: 404, body: { msg: 'not_friends', error: "You're not friends with this user" } },
};

// searching for users, the friends list and removing a friend. Friend requests are in friendRequestRoutes
export function friendsRouter(): Router {
  const router = Router();

  // users whose name matches or is similar to q, best match first, at most 20. Errors: 400 invalid_request
  router.get('/api/users/search', requireAuth, asyncRoute('Could not search for users, try again later',
    async (req: Request, res: Response) => {
      const user = getAuthUser(res);
      const text = typeof req.query.q === 'string' ? req.query.q.trim() : '';
      if (text === '' || text.length > MAX_SEARCH_LENGTH) {
        return invalidRequest(res, `q must be text of 1 to ${MAX_SEARCH_LENGTH} characters`);
      }

      const users = await searchUsers(text, user.userId);
      return res.status(200).json({
        msg: 'success',
        users: users.map((found) => ({ ...friendUserJson(found), friendship_status: found.friendshipStatus })),
      });
    }));

  /*  the user's friends in name order, each with chat_id, the chat the two share, so the
      app opens it straight from the list. Its messages work like a game chat's
   */
  router.get('/api/friends', requireAuth, asyncRoute('Could not load your friends, try again later',
    async (_req: Request, res: Response) => {
      const friends = await getFriends(getAuthUser(res).userId);
      return res.status(200).json({
        msg: 'success',
        friends: friends.map((friend) => ({ ...friendUserJson(friend), chat_id: friend.chatId })),
      });
    }));

  /*  stops being friends with userId, for both users, and deletes the chat they shared with
      its messages. The other user isn't notified. Errors: 400 invalid_request,
      404 not_friends, 409 own_friend
   */
  router.delete('/api/friends/:userId', requireAuth, asyncRoute('Could not remove this friend, try again later',
    async (req: Request<{ userId: string }>, res: Response) => {
      const user = getAuthUser(res);
      // user ids are INT UNSIGNED like game ids, so they're read the same way
      const friendId = readGameId(req.params.userId);
      if (friendId === null) return invalidRequest(res, 'userId must be a positive whole number');

      const result = await removeFriend(user.userId, friendId);
      if (!result.ok) return sendError(res, responseFor(REMOVE_ERRORS, result.problem));
      logInfo(`User ${user.userId} removed friend ${friendId}`);

      return res.status(200).json({ msg: 'success' });
    }));

  return router;
}
