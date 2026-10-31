import { Router, type Request, type Response } from 'express';
import { getGameChatId } from '../../chat/db/chats';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logError, logInfo } from '../../core/logger';
import { type PushMessage, sendPushToUser, sendPushToUsers } from '../../core/notifications/push';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import {
  cancelGame,
  completeGame,
  createGame,
  findOrganizer,
  type Game,
  type GamePlayer,
  getDiscoverGames,
  getGameById,
  getGamePlayers,
  getGamesForUser,
  getUsersToNotify,
  leaveGame,
  removePlayer,
  updateGame,
} from '../db/games';
import { countPendingJoinRequests, getJoinRequestStatus } from '../db/joinRequests';
import { parseCoordinates } from '../validation/coordinates';
import { parseNewGame, type CreateGameBody } from '../validation/newGame';
import { discoverGameJson, gameJson, gamePlayerJson, organizedGameJson } from './gameJson';
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
} from './gameErrors';

type ViewerRole = 'organizer' | 'player' | 'viewer';

/*  the signed in user's relation to the game, decides what the app shows. An organizer
    who left their cancelled or completed game only views it, like anyone else
 */
function viewerRole(game: Game, players: GamePlayer[], userId: number): ViewerRole {
  if (!players.some((player) => player.userId === userId)) return 'viewer';
  return game.organizerId === userId ? 'organizer' : 'player';
}

/*  tells the game's players and pending requesters (except the organizer) about a
    change. Best effort: the app gets its reply without waiting, failures are only logged
 */
function notifyGameUsers(gameId: number, organizerId: number, message: PushMessage): void {
  getUsersToNotify(gameId, organizerId)
    .then((userIds) => sendPushToUsers(userIds, message))
    .then((devices) => { logInfo(`Game ${gameId} "${message.title}" notification sent to ${devices} device(s)`); })
    .catch((error: unknown) => { logError(`Notifying players of game ${gameId} failed:`, error); });
}

// tells one user about their game, best effort: the app gets its reply without waiting for it
function notifyUser(userId: number, message: PushMessage): void {
  sendPushToUser(userId, message)
    .then((devices) => { logInfo(`"${message.title}" notification sent to ${devices} device(s)`); })
    .catch(logNotificationError);
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

// creating, listing, discovering, viewing, editing, cancelling, completing and leaving games, and removing players
export function gamesRouter(): Router {
  const router = Router();

  /*  create game: makes the game described in the body for the signed in user, who
      becomes its organizer and first player. Responds with the stored game, or
      400 invalid_request with the problem if a field is missing or invalid
   */
  router.post('/api/games', requireAuth, asyncRoute('Could not create game, try again later',
    async (req: Request<Record<string, never>, unknown, CreateGameBody | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const parsed = parseNewGame(req.body, user.userId);
      if (!parsed.ok) return invalidRequest(res, parsed.error);

      const gameId = await createGame(parsed.value);
      logInfo(`Game created id: ${gameId}`);
      // read it back so the app gets the stored values (times, player count, status, ...)
      const created = await readBackGame(gameId, 'creating it');

      return res.status(201).json({ msg: 'success', game: gameJson(created) });
    }));

  /*  the signed in user's games, split into the ones they organized and the ones
      they joined, soonest first. Organized games also have pending_requests, the
      join requests waiting for the organizer. Responds msg "no_games" if they have none yet
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
        organized_games: organized.map(organizedGameJson),
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
      if (!point) return invalidRequest(res, 'lat (-90 to 90) and lng (-180 to 180) are required numbers');

      const games = await getDiscoverGames(user.userId, point.latitude, point.longitude);
      if (games.length === 0) {
        return res.status(200).json({ msg: 'no_games' });
      }
      return res.status(200).json({ msg: 'success', games: games.map(discoverGameJson) });
    }));

  /*  game details: the game with its organizer, players and what the signed in user
      can do with it. viewer_role is "organizer", "player" (joined) or "viewer".
      pending_requests is only sent to the organizer and join_request_status
      (null if they never asked) only to viewers, both are null otherwise. chat_id is
      null for viewers, only players can open the game's chat.
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
      const organizer = await findOrganizer(game, players);
      if (!organizer) {
        throw new Error(`Organizer ${game.organizerId} of game ${gameId} not found`);
      }
      const role = viewerRole(game, players, user.userId);

      return res.status(200).json({
        msg: 'success',
        game: gameJson(game),
        organizer: gamePlayerJson(organizer),
        players: players.map(gamePlayerJson),
        viewer_role: role,
        pending_requests: role === 'organizer' ? await countPendingJoinRequests(gameId) : null,
        join_request_status: role === 'viewer' ? await getJoinRequestStatus(gameId, user.userId) : null,
        chat_id: role === 'viewer' ? null : await getGameChatId(gameId),
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
      if (!parsed.ok) return invalidRequest(res, parsed.error);
      const keepEndsAt = req.body?.ends_at === undefined;

      const before = await getGameById(gameId);
      if (!before) return sendError(res, gameChangeError('not_found'));

      const result = await updateGame(gameId, user.userId, parsed.value, keepEndsAt);
      if (!result.ok) {
        if (result.problem === 'too_few_players') {
          return invalidRequest(res, `max_players can't be below the ${result.playerCount} players already in the game`);
        }
        if (result.problem === 'ends_before_start') {
          return invalidRequest(res, 'starts_at must be before the game ends');
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

  /*  complete game: the organizer marks a game that has started as completed, after
      which anyone can leave it. Responds with the completed game and notifies its players.
      Errors: 400 invalid_request, 403 forbidden, 404 not_found,
      409 game_cancelled / game_not_started / game_completed
   */
  router.post('/api/games/:gameId/complete', requireAuth, asyncRoute('Could not complete game, try again later',
    async (req: Request<{ gameId: string }>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;

      const result = await completeGame(gameId, user.userId);
      if (!result.ok) return sendError(res, responseFor(COMPLETE_GAME_ERRORS, result.problem));

      const game = await readBackGame(gameId, 'completing it');
      logInfo(`Game completed id: ${gameId}`);

      notifyGameUsers(gameId, user.userId, {
        title: 'Game completed',
        body: `${game.title} was marked completed by the organizer`,
        data: { type: 'game_completed', gameId: String(gameId) },
      });

      return res.status(200).json({ msg: 'success', game: gameJson(game) });
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
