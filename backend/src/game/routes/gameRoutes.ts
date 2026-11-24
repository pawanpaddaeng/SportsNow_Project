import { Router, type Request, type Response } from 'express';
import { getGameChatId } from '../../chat/db/chats';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logInfo } from '../../core/logger';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import {
  createGame,
  findOrganizer,
  type Game,
  type GamePlayer,
  getDiscoverGames,
  getGamePlayers,
  getGamesForUser,
} from '../db/games';
import { countPendingJoinRequests, getJoinRequestStatus } from '../db/joinRequests';
import { parseCoordinates } from '../validation/coordinates';
import { parseNewGame, type CreateGameBody } from '../validation/newGame';
import { discoverGameJson, gameJson, gamePlayerJson, organizedGameJson } from './gameJson';
import { readBackGame, requireGame } from './gameErrors';

type ViewerRole = 'organizer' | 'player' | 'viewer';

/*  the signed in user's relation to the game, decides what the app shows. An organizer
    who left their cancelled or completed game only views it, like anyone else
 */
function viewerRole(game: Game, players: GamePlayer[], userId: number): ViewerRole {
  if (!players.some((player) => player.userId === userId)) return 'viewer';
  return game.organizerId === userId ? 'organizer' : 'player';
}

// creating, listing, discovering and viewing games. Changes to a game are in gameChangeRoutes
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
      const game = await requireGame(req.params.gameId, res);
      if (!game) return;
      const { gameId } = game;

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
        viewer_id: user.userId,
        viewer_role: role,
        pending_requests: role === 'organizer' ? await countPendingJoinRequests(gameId) : null,
        join_request_status: role === 'viewer' ? await getJoinRequestStatus(gameId, user.userId) : null,
        chat_id: role === 'viewer' ? null : await getGameChatId(gameId),
      });
    }));

  return router;
}
