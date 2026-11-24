import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logError, logInfo } from '../../core/logger';
import { type PushMessage, sendPushToUser, sendPushToUsers } from '../../core/notifications/push';
import { RATING_WINDOW_HOURS } from '../../review/ratingWindow';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import { cancelGame, completeGame, leaveGame, removePlayer, updateGame } from '../db/gameChanges';
import { type Game, getGameById, getUsersToNotify } from '../db/games';
import { parseCompleteGame, type CompleteGameBody } from '../validation/completeGame';
import { parseNewGame, type CreateGameBody } from '../validation/newGame';
import { gameJson } from './gameJson';
import {
  COMPLETE_GAME_ERRORS,
  gameChangeError,
  LEAVE_GAME_ERRORS,
  logNotificationError,
  readBackGame,
  readGameId,
  REMOVE_PLAYER_ERRORS,
  requireGameId,
  responseFor,
  sendError,
  sendUpdateGameError,
} from './gameErrors';

/*  tells the game's players and pending requesters (except the organizer) about a
    change. Best effort: the app gets its reply without waiting, failures are only logged
 */
function notifyGameUsers(gameId: number, organizerId: number, message: PushMessage): void {
  getUsersToNotify(gameId, organizerId)
    .then((userIds) => sendPushToUsers(userIds, message))
    .then((devices) => { logInfo(`Game ${gameId} "${message.title}" notification sent to ${devices} device(s)`); })
    .catch((error: unknown) => { logError(`Notifying players of game ${gameId} failed:`, error); });
}

// after the organizer changed the game: reads it back, tells its users and replies with it
async function replyWithChangedGame(
  res: Response,
  gameId: number,
  organizerId: number,
  change: string,
  message: (game: Game) => PushMessage,
): Promise<Response> {
  const game = await readBackGame(gameId, change);
  logInfo(`Game changed id: ${gameId} (${change})`);
  notifyGameUsers(gameId, organizerId, message(game));
  return res.status(200).json({ msg: 'success', game: gameJson(game) });
}

// tells one user about their game, best effort: the app gets its reply without waiting for it
function notifyUser(userId: number, message: PushMessage): void {
  sendPushToUser(userId, message)
    .then((devices) => { logInfo(`"${message.title}" notification sent to ${devices} device(s)`); })
    .catch(logNotificationError);
}

// the parts of a game an edit can change, named as in the notification, with the fields that make up each
const EDITABLE_PARTS: { name: string; fields: (game: Game) => unknown[] }[] = [
  { name: 'title', fields: (game) => [game.title] },
  { name: 'sport', fields: (game) => [game.sport] },
  { name: 'time', fields: (game) => [game.startsAt, game.endsAt] },
  { name: 'location', fields: (game) => [game.locationName, game.address, game.latitude, game.longitude] },
  { name: 'max players', fields: (game) => [game.maxPlayers] },
  { name: 'skill level', fields: (game) => [game.skillLevel] },
  { name: 'description', fields: (game) => [game.description] },
];

// what an edit changed, in words for the notification, e.g. ["time", "location"]
function describeChanges(before: Game, after: Game): string[] {
  return EDITABLE_PARTS
    .filter((part) => JSON.stringify(part.fields(before)) !== JSON.stringify(part.fields(after)))
    .map((part) => part.name);
}

// editing, cancelling, completing and leaving games, and removing players
export function gameChangesRouter(): Router {
  const router = Router();

  /*  edit game: the organizer replaces the game's details. The body has the same
      fields as create game; ends_at is kept as it is when left out. Responds with the
      updated game and notifies its players and pending requesters if anything changed.
      Errors: 400 invalid_request (bad field, or max_players below the current players),
      403 forbidden (not the organizer), 404 not_found, 409 game_cancelled / game_started
   */
  router.put('/api/games/:gameId', requireAuth, asyncRoute('Could not update game, try again later',
    async (req: Request<{ gameId: string }, unknown, CreateGameBody | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;
      const parsed = parseNewGame(req.body, user.userId);
      if (!parsed.ok) return invalidRequest(res, parsed.error);
      const keepEndsAt = req.body?.ends_at === undefined;

      const before = await getGameById(gameId);
      if (!before) return sendError(res, gameChangeError('not_found'));

      const result = await updateGame(gameId, user.userId, parsed.value, keepEndsAt);
      if (!result.ok) return sendUpdateGameError(res, result);

      const after = await readBackGame(gameId, 'updating it');
      logInfo(`Game updated id: ${gameId}`);

      const changes = describeChanges(before, after);
      if (result.changed && changes.length > 0) {
        notifyGameUsers(gameId, user.userId, {
          title: 'Game updated',
          body: `${after.title}: the organizer changed the ${changes.join(', ')}`,
          data: { type: 'game_updated', gameId: String(gameId) },
        });
      }

      return res.status(200).json({ msg: 'success', game: gameJson(after) });
    }));

  /*  cancel game: the organizer cancels a game that hasn't started. It stays with
      status "cancelled" so players can see what happened, and they and anyone with a
      pending join request are notified. Responds with the cancelled game.
      Errors: 400 invalid_request, 403 forbidden, 404 not_found, 409 game_cancelled / game_started
   */
  router.post('/api/games/:gameId/cancel', requireAuth, asyncRoute('Could not cancel game, try again later',
    async (req: Request<{ gameId: string }>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;

      const result = await cancelGame(gameId, user.userId);
      if (!result.ok) return sendError(res, gameChangeError(result.problem));

      return replyWithChangedGame(res, gameId, user.userId, 'cancelling it', (game) => ({
        title: 'Game cancelled',
        body: `${game.title} at ${game.locationName} was cancelled by the organizer`,
        data: { type: 'game_cancelled', gameId: String(gameId) },
      }));
    }));

  /*  complete game: the organizer marks a game that has started as completed, after
      which anyone can leave it. The optional no_show_user_ids in the body are the players
      who didn't show up. Responds with the completed game and notifies its players.
      Errors: 400 invalid_request, 403 forbidden, 404 not_found,
      409 game_cancelled / game_not_started / game_completed
   */
  router.post('/api/games/:gameId/complete', requireAuth, asyncRoute('Could not complete game, try again later',
    async (req: Request<{ gameId: string }, unknown, CompleteGameBody | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;
      const parsed = parseCompleteGame(req.body);
      if (!parsed.ok) return invalidRequest(res, parsed.error);

      const result = await completeGame(gameId, user.userId, parsed.value);
      if (!result.ok) return sendError(res, responseFor(COMPLETE_GAME_ERRORS, result.problem));

      return replyWithChangedGame(res, gameId, user.userId, 'completing it', (game) => ({
        title: 'Game completed',
        body: `${game.title} was marked completed by the organizer. Rate your teammates within ${RATING_WINDOW_HOURS} hours`,
        data: { type: 'game_completed', gameId: String(gameId) },
      }));
    }));

  /*  leave game: the signed in user stops being a player. Players can leave at any
      time, even mid-game. The organizer can only leave once the game is cancelled or
      completed (until then they cancel it, or mark it completed once it started).
      Leaving a cancelled or completed game takes it off their Home screen, and the last
      player to leave deletes it. Responds with the game after leaving, or game null and
      game_deleted true when it was deleted, and tells the organizer when a player leaves
      a game that's on.
      Errors: 400 invalid_request, 404 not_found,
      409 not_player / organizer_must_cancel / organizer_must_complete
   */
  router.post('/api/games/:gameId/leave', requireAuth, asyncRoute('Could not leave the game, try again later',
    async (req: Request<{ gameId: string }>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;

      const result = await leaveGame(gameId, user.userId);
      if (!result.ok) return sendError(res, responseFor(LEAVE_GAME_ERRORS, result.problem));

      // the last player left a cancelled or completed game, nobody is left to tell
      if (result.deleted) {
        logInfo(`Last player left, game deleted id: ${gameId}`);
        return res.status(200).json({ msg: 'success', game: null, game_deleted: true });
      }

      const game = await readBackGame(gameId, 'leaving it');
      logInfo(`User left game id: ${gameId}`);

      if (result.status === 'scheduled') {
        notifyUser(result.organizerId, {
          title: 'Player left',
          body: `${user.firstName} left ${result.title}`,
          data: { type: 'player_left', gameId: String(gameId) },
        });
      }

      return res.status(200).json({ msg: 'success', game: gameJson(game), game_deleted: false });
    }));

  /*  remove player: the organizer takes a player out of their game before it starts.
      The player can send a new join request later, and is notified. Responds with the
      game after removing them.
      Errors: 400 invalid_request, 403 forbidden, 404 not_found / player_not_found,
      409 game_cancelled / game_started / organizer_self
   */
  router.delete('/api/games/:gameId/players/:userId', requireAuth, asyncRoute('Could not remove the player, try again later',
    async (req: Request<{ gameId: string; userId: string }>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = readGameId(req.params.gameId);
      // user ids are INT UNSIGNED like game ids, so they're read the same way
      const playerId = readGameId(req.params.userId);
      if (gameId === null || playerId === null) {
        return invalidRequest(res, 'gameId and userId must be positive whole numbers');
      }

      const result = await removePlayer(gameId, user.userId, playerId);
      if (!result.ok) return sendError(res, responseFor(REMOVE_PLAYER_ERRORS, result.problem));

      const game = await readBackGame(gameId, 'removing a player');
      logInfo(`Player ${playerId} removed from game id: ${gameId}`);

      notifyUser(playerId, {
        title: 'Removed from game',
        body: `The organizer removed you from ${result.title}`,
        data: { type: 'removed_from_game', gameId: String(gameId) },
      });

      return res.status(200).json({ msg: 'success', game: gameJson(game) });
    }));

  return router;
}
