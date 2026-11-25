import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { logInfo } from '../../core/logger';
import {
  type ErrorResponse,
  responseFor,
  sendError,
  withGame,
  withGameId,
} from '../../game/routes/gameErrors';
import { getAuthUser, requireAuth } from '../../user/auth/middleware';
import {
  createRating,
  type CreateRatingProblem,
  getPlayersToRate,
  type PlayerToRate,
} from '../db/ratings';
import { getBadges } from '../db/badges';
import { getReputationScore } from '../db/reputation';
import { parseRating, type RatingBody } from '../validation/rating';

const NOT_ALLOWED: ErrorResponse = {
  status: 403,
  body: { msg: 'forbidden', error: 'Only players who showed up to a completed game can rate in it' },
};

const RATING_CLOSED: ErrorResponse = {
  status: 403,
  body: { msg: 'rating_closed', error: 'Rating for this game has closed' },
};

const CREATE_RATING_ERRORS: Record<CreateRatingProblem, ErrorResponse> = {
  not_allowed: NOT_ALLOWED,
  rating_closed: RATING_CLOSED,
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

  /*  the other players who showed up and whether the user already rated them, for a day
      after the game is marked completed. Errors: 400 invalid_request, 403 forbidden (didn't
      show up, or the game isn't completed) / rating_closed, 404 not_found
   */
  router.get('/api/games/:gameId/ratings', requireAuth, asyncRoute('Could not load the players, try again later',
    withGame(async (user, game, _req, res) => {
      const { gameId } = game;
      const result = await getPlayersToRate(gameId, user.userId);
      if (!result.ok) return sendError(res, result.problem === 'rating_closed' ? RATING_CLOSED : NOT_ALLOWED);

      return res.status(200).json({
        msg: 'success',
        game_title: game.title,
        organizer_id: game.organizerId,
        players: result.players.map(playerToRateJson),
      });
    })));

  /*  records the user's rating of another player, once per player per game, within a day
      of the game being marked completed. Every category is required. Errors: 400
      invalid_request, 403 forbidden / rating_closed, 404 player_not_found, 409 rated_self /
      already_rated
   */
  router.post('/api/games/:gameId/ratings', requireAuth, asyncRoute('Could not submit your rating, try again later',
    withGameId<RatingBody | undefined>(async (user, gameId, req, res) => {
      const parsed = parseRating(req.body);
      if (!parsed.ok) return invalidRequest(res, parsed.error);

      const result = await createRating(gameId, user.userId, parsed.value.ratedUserId, parsed.value.scores);
      if (!result.ok) return sendError(res, responseFor(CREATE_RATING_ERRORS, result.problem));

      logInfo(`Rating recorded for game id: ${gameId}`);
      return res.status(201).json({ msg: 'success' });
    })));

  // the signed in user's reputation score, shown on their profile
  router.get('/api/users/me/stats', requireAuth, asyncRoute('Could not load your rating, try again later',
    async (_req: Request, res: Response) => {
      const user = getAuthUser(res);
      return res.status(200).json({ msg: 'success', reputation_score: await getReputationScore(user.userId) });
    }));

  /*  the ids of the badges the signed in user earned, in the order the app shows them, for
      their profile. Any they now qualify for are saved first, and saved badges are never removed
   */
  router.get('/api/users/me/badges', requireAuth, asyncRoute('Could not load your badges, try again later',
    async (_req: Request, res: Response) => {
      const user = getAuthUser(res);
      return res.status(200).json({ msg: 'success', badges: await getBadges(user.userId) });
    }));

  return router;
}
