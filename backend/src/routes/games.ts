import { Router, type Request, type Response } from 'express';
import { getAuthUser, requireAuth } from '../auth/middleware';
import {
  cancelGame,
  countPendingJoinRequests,
  createGame,
  discoverGameJson,
  type Game,
  gameJson,
  gamePlayerJson,
  getDiscoverGames,
  getGameById,
  getGamePlayers,
  getGamesForUser,
  getJoinRequestStatus,
  getUsersToNotify,
  updateGame,
} from '../db/games';
import { findUserById } from '../db/users';
import { asyncRoute } from '../http/asyncRoute';
import { logInfo } from '../logger';
import { type PushMessage, sendPushToUser, sendPushToUsers } from '../notifications/push';
import { parseCoordinates } from '../validation/coordinates';
import { parseNewGame, type CreateGameBody } from '../validation/newGame';
import { gameChangeError, logNotificationError, readBackGame, requireGameId, sendError } from './shared';

// how long after creating a game the debug notification is sent
const DEBUG_NOTIFICATION_DELAY_MS = 5000;

/*  tells the game's players and pending requesters (except the organizer) about a
    change. Best effort: the app gets its reply without waiting, failures are only logged
 */
function notifyGameUsers(gameId: number, organizerId: number, message: PushMessage): void {
  getUsersToNotify(gameId, organizerId)
    .then((userIds) => sendPushToUsers(userIds, message))
    .then((devices) => { logInfo(`Game ${gameId} "${message.title}" notification sent to ${devices} device(s)`); })
    .catch((error: unknown) => { console.error(`Notifying players of game ${gameId} failed:`, error); });
}

// what an edit changed, in words for the notification, e.g. ["time", "location"]
function describeChanges(before: Game, after: Game): string[] {
  const changes: string[] = [];
  if (before.title !== after.title) changes.push('title');
  if (before.sport !== after.sport) changes.push('sport');
  if (before.startsAt !== after.startsAt || before.endsAt !== after.endsAt) changes.push('time');
  if (before.locationName !== after.locationName || before.address !== after.address ||
      before.latitude !== after.latitude || before.longitude !== after.longitude) {
    changes.push('location');
  }
  if (before.maxPlayers !== after.maxPlayers) changes.push('max players');
  if (before.skillLevel !== after.skillLevel) changes.push('skill level');
  if (before.description !== after.description) changes.push('description');
  return changes;
}

// creating, listing, discovering, viewing, editing and cancelling games
export function gamesRouter(): Router {
  const router = Router();

  /*  create game: makes the game described in the body for the signed in user, who
      becomes its organizer and first player. Responds with the stored game, or
      400 invalid_request with the problem if a field is missing or invalid.
      Also sends the organizer a push notification after a few seconds to test notifications
   */
  router.post('/api/games', requireAuth, asyncRoute('Could not create game, try again later',
    async (req: Request<Record<string, never>, unknown, CreateGameBody | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const parsed = parseNewGame(req.body, user.userId);
      if (!parsed.ok) {
        return res.status(400).json({ msg: 'invalid_request', error: parsed.error });
      }
      const game = parsed.value;

      const gameId = await createGame(game);
      logInfo(`Game created id: ${gameId}`);
      // read it back so the app gets the stored values (times, player count, status, ...)
      const created = await readBackGame(gameId, 'creating it');

      // debugging: notify the organizer shortly after to check push notifications work
      setTimeout(() => {
        sendPushToUser(user.userId, {
          title: 'Game created',
          body: `${game.title} at ${game.locationName} was created`,
          data: { type: 'game_created', gameId: String(gameId) },
        }).then((devices) => { logInfo(`Debug game notification sent to ${devices} device(s)`); })
          .catch(logNotificationError);
      }, DEBUG_NOTIFICATION_DELAY_MS);

      return res.status(201).json({ msg: 'success', game: gameJson(created) });
    }));

  /*  the signed in user's games, split into the ones they organized and the ones
      they joined, soonest first. Responds msg "no_games" if they have none yet
   */
  router.get('/api/games/mine', requireAuth, asyncRoute('Could not load games, try again later',
    async (_req: Request, res: Response) => {
      const user = getAuthUser(res);
      const { organized, joined } = await getGamesForUser(user.userId);
      if (organized.length === 0 && joined.length === 0) {
        return res.status(200).json({ msg: 'no_games' });
      }
      return res.status(200).json({
        msg: 'success',
        organized_games: organized.map(gameJson),
        joined_games: joined.map(gameJson),
      });
    }));

  /*  discover: upcoming games near ?lat=&lng= (within MAX_DISCOVER_DISTANCE_M) that the
      signed in user can ask to join, closest first. Each has distance_m and the user's
      join_request_status (null if they never asked). Responds msg "no_games" when none
      are nearby, 400 invalid_request for missing or out of range coordinates.
      Registered before /api/games/:gameId so "discover" isn't read as an id
   */
  router.get('/api/games/discover', requireAuth, asyncRoute('Could not load games, try again later',
    async (req: Request, res: Response) => {
      const user = getAuthUser(res);
      const point = parseCoordinates(req.query.lat, req.query.lng);
      if (!point) {
        return res.status(400).json({
          msg: 'invalid_request',
          error: 'lat (-90 to 90) and lng (-180 to 180) are required numbers',
        });
      }

      const games = await getDiscoverGames(user.userId, point.latitude, point.longitude);
      if (games.length === 0) {
        return res.status(200).json({ msg: 'no_games' });
      }
      return res.status(200).json({ msg: 'success', games: games.map(discoverGameJson) });
    }));

  /*  game details: the game with its organizer, players and what the signed in user
      can do with it. viewer_role is "organizer", "player" (joined) or "viewer".
      pending_requests is only sent to the organizer and join_request_status
      (null if they never asked) only to viewers, both are null otherwise.
      Responds 400 invalid_request for a malformed id, 404 not_found if no game has it.
      Registered after /api/games/mine so "mine" isn't read as an id
   */
  router.get('/api/games/:gameId', requireAuth, asyncRoute('Could not load the game, try again later',
    async (req: Request<{ gameId: string }>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;

      const game = await getGameById(gameId);
      if (!game) return sendError(res, gameChangeError('not_found'));

      const players = await getGamePlayers(gameId);
      // the organizer is always a player, look them up only if that ever isn't the case
      const organizerPlayer = players.find((player) => player.userId === game.organizerId);
      const organizerUser = organizerPlayer ? null : await findUserById(game.organizerId);
      const organizer = organizerPlayer ?? (organizerUser && {
        userId: organizerUser.userId,
        firstName: organizerUser.firstName,
        lastName: organizerUser.lastName,
      });
      if (!organizer) {
        throw new Error(`Organizer ${game.organizerId} of game ${gameId} not found`);
      }

      const isPlayer = players.some((player) => player.userId === user.userId);
      const role = game.organizerId === user.userId ? 'organizer' : (isPlayer ? 'player' : 'viewer');

      return res.status(200).json({
        msg: 'success',
        game: gameJson(game),
        organizer: gamePlayerJson(organizer),
        players: players.map(gamePlayerJson),
        viewer_role: role,
        pending_requests: role === 'organizer' ? await countPendingJoinRequests(gameId) : null,
        join_request_status: role === 'viewer' ? await getJoinRequestStatus(gameId, user.userId) : null,
      });
    }));

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
      if (!parsed.ok) {
        return res.status(400).json({ msg: 'invalid_request', error: parsed.error });
      }
      const keepEndsAt = req.body?.ends_at === undefined;

      const before = await getGameById(gameId);
      if (!before) return sendError(res, gameChangeError('not_found'));

      const result = await updateGame(gameId, user.userId, parsed.value, keepEndsAt);
      if (!result.ok) {
        if (result.problem === 'too_few_players') {
          return res.status(400).json({
            msg: 'invalid_request',
            error: `max_players can't be below the ${result.playerCount} players already in the game`,
          });
        }
        if (result.problem === 'ends_before_start') {
          return res.status(400).json({ msg: 'invalid_request', error: 'starts_at must be before the game ends' });
        }
        return sendError(res, gameChangeError(result.problem));
      }

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

      const game = await readBackGame(gameId, 'cancelling it');
      logInfo(`Game cancelled id: ${gameId}`);

      notifyGameUsers(gameId, user.userId, {
        title: 'Game cancelled',
        body: `${game.title} at ${game.locationName} was cancelled by the organizer`,
        data: { type: 'game_cancelled', gameId: String(gameId) },
      });

      return res.status(200).json({ msg: 'success', game: gameJson(game) });
    }));

  return router;
}
