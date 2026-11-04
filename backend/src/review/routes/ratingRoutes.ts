import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logInfo } from '../../core/logger';
import { getGameById } from '../../game/db/games';
import {
  type ErrorResponse,
  gameChangeError,
  requireGameId,
  responseFor,
  sendError,
} from '../../game/routes/gameErrors';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import {
  createRating,
  type CreateRatingProblem,
  getPlayersToRate,
  getReputationScore,
  type PlayerToRate,
} from '../db/ratings';
import { parseRating, type RatingBody } from '../validation/rating';

const NOT_ALLOWED: ErrorResponse = {
  status: 403,
  body: { msg: 'forbidden', error: 'Only players who showed up to a completed game can rate in it' },
};

const CREATE_RATING_ERRORS: Record<CreateRatingProblem, ErrorResponse> = {
  not_allowed: NOT_ALLOWED,
  self: { status: 409, body: { msg: 'rated_self', error: "You can't rate yourself" } },
  player_not_found: { status: 404, body: { msg: 'player_not_found', error: "This player didn't show up to the game" } },
  already_rated: { status: 409, body: { msg: 'already_rated', error: 'You already rated this player' } },
};

function playerToRateJson(player: PlayerToRate) {
  return {
    user_id: player.userId,
    first_name: player.firstName,
    last_name: player.lastName,
    rated: player.rated,
  };
}

// rating the other players after a game. Players see their own score, but never what they were rated in a game
export function ratingsRouter(): Router {
  const router = Router();

  /*  the other players who showed up and whether the user already rated them.
      Errors: 400 invalid_request, 403 forbidden (didn't show up, or the game isn't
      completed), 404 not_found
   */
  router.get('/api/games/:gameId/ratings', requireAuth, asyncRoute('Could not load the players, try again later',
    async (req: Request<{ gameId: string }>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;

      const game = await getGameById(gameId);
      if (!game) return sendError(res, gameChangeError('not_found'));
      const players = await getPlayersToRate(gameId, user.userId);
      if (!players) return sendError(res, NOT_ALLOWED);

      return res.status(200).json({
        msg: 'success',
        game_title: game.title,
        organizer_id: game.organizerId,
        players: players.map(playerToRateJson),
      });
    }));

  /*  records the user's rating of another player, once per player per game. Every
      category is required. Errors: 400 invalid_request, 403 forbidden,
      404 player_not_found, 409 rated_self / already_rated
   */
  router.post('/api/games/:gameId/ratings', requireAuth, asyncRoute('Could not submit your rating, try again later',
    async (req: Request<{ gameId: string }, unknown, RatingBody | undefined>, res: Response) => {
      const user = getAuthUser(res);
      const gameId = requireGameId(req.params.gameId, res);
      if (gameId === null) return;
      const parsed = parseRating(req.body);
      if (!parsed.ok) return invalidRequest(res, parsed.error);

      const result = await createRating(gameId, user.userId, parsed.value.ratedUserId, parsed.value.scores);
      if (!result.ok) return sendError(res, responseFor(CREATE_RATING_ERRORS, result.problem));

      logInfo(`Rating recorded for game id: ${gameId}`);
      return res.status(201).json({ msg: 'success' });
    }));

  // the signed in user's reputation score, shown on their profile
  router.get('/api/users/me/stats', requireAuth, asyncRoute('Could not load your rating, try again later',
    async (_req: Request, res: Response) => {
      const user = getAuthUser(res);
      return res.status(200).json({ msg: 'success', reputation_score: await getReputationScore(user.userId) });
    }));

  return router;
}
