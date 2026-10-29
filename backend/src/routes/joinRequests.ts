import { Router, type Request, type Response } from 'express';
import { getAuthUser, requireAuth } from '../auth/middleware';
import { getGameById } from '../db/games';
import {
  createJoinRequest,
  decideJoinRequest,
  getPendingJoinRequests,
  type JoinRequestDecisionProblem,
  type JoinRequestProblem,
} from '../db/joinRequests';
import { asyncRoute } from '../http/asyncRoute';
import { invalidRequest } from '../http/responses';
import { logInfo } from '../logger';
import { sendPushToUser } from '../notifications/push';
import { parseJoinRequest, type JoinRequestBody } from '../validation/joinRequest';
import { gameJson, pendingJoinRequestJson } from './gameJson';
import {
  type ErrorResponse,
  GAME_CHANGE_ERRORS,
  gameChangeError,
  logNotificationError,
  readBackGame,
  readGameId,
  requireGameId,
  responseFor,
  sendError,
} from './shared';

// responses for a join request that can't be made, a missing, cancelled or started game as for edits
const JOIN_REQUEST_ERRORS: Record<JoinRequestProblem, ErrorResponse> = {
  not_found: GAME_CHANGE_ERRORS.not_found,
  cancelled: GAME_CHANGE_ERRORS.cancelled,
  started: GAME_CHANGE_ERRORS.started,
  own_game: { status: 409, body: { msg: 'own_game', error: 'You organize this game' } },
  already_player: { status: 409, body: { msg: 'already_player', error: "You're already in this game" } },
  game_full: { status: 409, body: { msg: 'game_full', error: 'This game is full' } },
  already_requested: {
    status: 409,
    body: { msg: 'already_requested', error: 'You already asked to join this game, wait for the organizer to respond' },
  },
};

// responses for an approval or decline the organizer can't make
const DECISION_ERRORS: Record<JoinRequestDecisionProblem, ErrorResponse> = {
  ...GAME_CHANGE_ERRORS,
  request_not_found: { status: 404, body: { msg: 'request_not_found', error: 'This join request does not exist' } },
  already_responded: { status: 409, body: { msg: 'already_responded', error: 'This request was already answered or withdrawn' } },
  game_full: { status: 409, body: { msg: 'game_full', error: 'This game is full, add spots before approving more players' } },
};

// asking to join a game, and the organizer listing and answering those requests
export function joinRequestsRouter(): Router {
  const router = Router();

  /*  join request: the signed in user asks the organizer to let them join, with the
      answers from the join request popup. Responds 201 with the pending request and
      notifies the organizer. Errors: 400 invalid_request, 404 not_found,
      409 own_game / already_player / already_requested / game_full / game_cancelled / game_started
   */
  router.post('/api/games/:gameId/join-requests', requireAuth, asyncRoute('Could not send your request, try again later',
    async (req: Request<{ gameId: string }, unknown, JoinRequestBody | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;
      const parsed = parseJoinRequest(req.body);
      if (!parsed.ok) return invalidRequest(res, parsed.error);

      const result = await createJoinRequest(gameId, user.userId, parsed.value);
      if (!result.ok) return sendError(res, responseFor(JOIN_REQUEST_ERRORS, result.problem));
      logInfo(`Join request ${result.requestId} sent for game id: ${gameId}`);

      // best effort, the app gets its reply without waiting for the notification
      sendPushToUser(result.organizerId, {
        title: 'New join request',
        body: `${user.firstName} wants to join ${result.title}`,
        data: { type: 'join_request', gameId: String(gameId) },
      }).then((devices) => { logInfo(`Join request notification sent to ${devices} device(s)`); })
        .catch(logNotificationError);

      return res.status(201).json({
        msg: 'success',
        request: { request_id: result.requestId, game_id: gameId, status: 'pending' },
      });
    }));

  /*  the organizer's view of who asked to join: the game and its pending requests,
      newest first (an empty list when there are none).
      Errors: 400 invalid_request, 403 forbidden (not the organizer), 404 not_found
   */
  router.get('/api/games/:gameId/join-requests', requireAuth, asyncRoute('Could not load join requests, try again later',
    async (req: Request<{ gameId: string }>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;

      const game = await getGameById(gameId);
      if (!game) return sendError(res, gameChangeError('not_found'));
      if (game.organizerId !== user.userId) {
        return res.status(403).json({ msg: 'forbidden', error: 'Only the organizer can see join requests' });
      }

      const requests = await getPendingJoinRequests(gameId);
      return res.status(200).json({
        msg: 'success',
        game: gameJson(game),
        requests: requests.map(pendingJoinRequestJson),
      });
    }));

  /*  the organizer approves or declines a pending request ({ decision: "approve" | "decline" }).
      Approving adds the requester to the game. Responds with the game after the decision
      and notifies the requester. Errors: 400 invalid_request, 403 forbidden,
      404 not_found / request_not_found, 409 game_cancelled / game_started / game_full / already_responded
   */
  router.patch('/api/games/:gameId/join-requests/:requestId', requireAuth, asyncRoute('Could not save your answer, try again later',
    async (req: Request<{ gameId: string; requestId: string }, unknown, { decision?: unknown } | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = readGameId(req.params.gameId);
      // request ids are INT UNSIGNED like game ids, so they're read the same way
      const requestId = readGameId(req.params.requestId);
      if (gameId === null || requestId === null) {
        return invalidRequest(res, 'gameId and requestId must be positive whole numbers');
      }
      const decision = req.body?.decision;
      if (decision !== 'approve' && decision !== 'decline') {
        return invalidRequest(res, 'decision must be "approve" or "decline"');
      }
      const approve = decision === 'approve';

      const result = await decideJoinRequest(gameId, requestId, user.userId, approve);
      if (!result.ok) return sendError(res, responseFor(DECISION_ERRORS, result.problem));

      const game = await readBackGame(gameId, `answering request ${requestId}`);
      logInfo(`Join request ${requestId} ${approve ? 'approved' : 'declined'} for game id: ${gameId}`);

      // best effort, the app gets its reply without waiting for the notification
      sendPushToUser(result.requesterId, approve
        ? {
          title: 'Request approved',
          body: `You're in! You joined ${result.title}`,
          data: { type: 'join_request_approved', gameId: String(gameId) },
        }
        : {
          title: 'Request declined',
          body: `The organizer declined your request to join ${result.title}`,
          data: { type: 'join_request_declined', gameId: String(gameId) },
        }).then((devices) => { logInfo(`Join request answer sent to ${devices} device(s)`); })
        .catch(logNotificationError);

      return res.status(200).json({
        msg: 'success',
        request: { request_id: requestId, status: approve ? 'approved' : 'declined' },
        game: gameJson(game),
      });
    }));

  return router;
}
