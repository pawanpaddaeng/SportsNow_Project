import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logInfo } from '../../core/logger';
import { sendPushToUser } from '../../core/notifications/push';
import { type ErrorResponse, logNotificationError, readGameId, responseFor, sendError } from '../../game/routes/gameErrors';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import {
  decideFriendRequest,
  type FriendDecisionProblem,
  type FriendRequestProblem,
  type FriendUser,
  getFriends,
  getIncomingFriendRequests,
  searchUsers,
  sendFriendRequest,
} from '../db/friends';

// longest search text, longer than any name can be
const MAX_SEARCH_LENGTH = 100;

const SEND_ERRORS: Record<FriendRequestProblem, ErrorResponse> = {
  own_request: { status: 409, body: { msg: 'own_request', error: "You can't send a friend request to yourself" } },
  user_not_found: { status: 404, body: { msg: 'user_not_found', error: 'This user does not exist' } },
  already_friends: { status: 409, body: { msg: 'already_friends', error: "You're already friends" } },
  already_requested: { status: 409, body: { msg: 'already_requested', error: 'You already sent this person a friend request' } },
  request_received: { status: 409, body: { msg: 'request_received', error: 'This person already sent you a friend request' } },
};

const DECISION_ERRORS: Record<FriendDecisionProblem, ErrorResponse> = {
  request_not_found: { status: 404, body: { msg: 'request_not_found', error: 'This friend request does not exist' } },
  already_responded: { status: 409, body: { msg: 'already_responded', error: 'This request was already answered' } },
};

function friendUserJson(user: FriendUser) {
  return { user_id: user.userId, first_name: user.firstName, last_name: user.lastName };
}

// searching for users, friend requests and the friends list
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
      return res.status(200).json({ msg: 'success', users: users.map(friendUserJson) });
    }));

  router.get('/api/friends', requireAuth, asyncRoute('Could not load your friends, try again later',
    async (_req: Request, res: Response) => {
      const friends = await getFriends(getAuthUser(res).userId);
      return res.status(200).json({ msg: 'success', friends: friends.map(friendUserJson) });
    }));

  // requests sent to the signed in user that are waiting for an answer, newest first
  router.get('/api/friends/requests', requireAuth, asyncRoute('Could not load your friend requests, try again later',
    async (_req: Request, res: Response) => {
      const requests = await getIncomingFriendRequests(getAuthUser(res).userId);
      return res.status(200).json({
        msg: 'success',
        requests: requests.map((request) => ({ request_id: request.requestId, sender: friendUserJson(request.sender) })),
      });
    }));

  /*  sends a friend request to { user_id }. Responds 201 with the pending request and
      notifies the other user. Errors: 400 invalid_request, 404 user_not_found,
      409 own_request / already_friends / already_requested / request_received
   */
  router.post('/api/friends/requests', requireAuth, asyncRoute('Could not send your friend request, try again later',
    async (req: Request<Record<string, never>, unknown, { user_id?: unknown } | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const rawId = req.body?.user_id;
      // user ids are INT UNSIGNED like game ids, so they're read the same way
      const receiverId = typeof rawId === 'number' ? readGameId(String(rawId)) : null;
      if (receiverId === null) return invalidRequest(res, 'user_id must be a positive whole number');

      const result = await sendFriendRequest(user.userId, receiverId);
      if (!result.ok) return sendError(res, responseFor(SEND_ERRORS, result.problem));
      logInfo(`Friend request ${result.requestId} sent`);

      // best effort, the app gets its reply without waiting for the notification
      sendPushToUser(receiverId, {
        title: 'New friend request',
        body: `${user.firstName} sent you a friend request`,
        data: { type: 'friend_request' },
      }).catch(logNotificationError);

      return res.status(201).json({
        msg: 'success',
        request: { request_id: result.requestId, user_id: receiverId, status: 'pending' },
      });
    }));

  /*  the receiver answers a pending request ({ decision: "accept" | "decline" }) and the
      sender is notified. Errors: 400 invalid_request, 404 request_not_found, 409 already_responded
   */
  router.patch('/api/friends/requests/:requestId', requireAuth, asyncRoute('Could not save your answer, try again later',
    async (req: Request<{ requestId: string }, unknown, { decision?: unknown } | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const requestId = readGameId(req.params.requestId);
      if (requestId === null) return invalidRequest(res, 'requestId must be a positive whole number');
      const decision = req.body?.decision;
      if (decision !== 'accept' && decision !== 'decline') {
        return invalidRequest(res, 'decision must be "accept" or "decline"');
      }
      const accept = decision === 'accept';

      const result = await decideFriendRequest(requestId, user.userId, accept);
      if (!result.ok) return sendError(res, responseFor(DECISION_ERRORS, result.problem));
      logInfo(`Friend request ${requestId} ${accept ? 'accepted' : 'declined'}`);

      // best effort, the app gets its reply without waiting for the notification
      sendPushToUser(result.senderId, accept
        ? {
          title: 'Friend request accepted',
          body: `${user.firstName} accepted your friend request`,
          data: { type: 'friend_request_accepted' },
        }
        : {
          title: 'Friend request declined',
          body: `${user.firstName} declined your friend request`,
          data: { type: 'friend_request_declined' },
        }).catch(logNotificationError);

      return res.status(200).json({ msg: 'success' });
    }));

  return router;
}
