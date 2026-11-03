import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logInfo } from '../../core/logger';
import { sendPushToUsers } from '../../core/notifications/push';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import { checkInvites, type InviteProblem } from '../db/invites';
import { parseInvite, type InviteBody } from '../validation/invite';
import {
  type ErrorResponse,
  GAME_CHANGE_ERRORS,
  logNotificationError,
  requireGameId,
  responseFor,
  sendError,
} from './gameErrors';

// responses for friends the organizer can't invite, the game's problems as for edits
const INVITE_ERRORS: Record<InviteProblem, ErrorResponse> = {
  ...GAME_CHANGE_ERRORS,
  not_organizer: { status: 403, body: { msg: 'forbidden', error: 'Only the organizer can invite friends to this game' } },
  game_full: { status: 409, body: { msg: 'game_full', error: 'This game is full' } },
  not_friend: { status: 403, body: { msg: 'not_friend', error: 'You can only invite your friends' } },
  already_player: { status: 409, body: { msg: 'already_player', error: 'One of these friends is already in this game' } },
};

// the organizer inviting their friends to a game
export function invitesRouter(): Router {
  const router = Router();

  /*  invite friends: the organizer invites { friend_ids } to their game before it starts.
      Each friend gets a notification that opens the game, where they can send a join
      request. Nothing is stored. Responds 201 { msg: "success" }.
      Errors: 400 invalid_request, 403 forbidden (not the organizer) / not_friend,
      404 not_found, 409 game_cancelled / game_started / game_full / already_player
   */
  router.post('/api/games/:gameId/invites', requireAuth, asyncRoute('Could not send your invites, try again later',
    async (req: Request<{ gameId: string }, unknown, InviteBody | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;
      const parsed = parseInvite(req.body);
      if (!parsed.ok) return invalidRequest(res, parsed.error);
      const friendIds = parsed.value;

      const result = await checkInvites(gameId, user.userId, friendIds);
      if (!result.ok) return sendError(res, responseFor(INVITE_ERRORS, result.problem));
      logInfo(`${friendIds.length} friend(s) invited to game id: ${gameId}`);

      // best effort, the app gets its reply without waiting for the notifications
      sendPushToUsers(friendIds, {
        title: 'Game invite',
        body: `${user.firstName} invited you to ${result.title}`,
        data: { type: 'game_invite', gameId: String(gameId) },
      }).then((devices) => { logInfo(`Game invite sent to ${devices} device(s)`); })
        .catch(logNotificationError);

      return res.status(201).json({ msg: 'success' });
    }));

  return router;
}