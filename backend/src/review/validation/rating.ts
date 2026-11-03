import type { ParseResult } from '../../core/validation';
import type { RatingScores } from '../reputation';

/*  body of POST /api/games/:gameId/ratings. Fields are unknown until checked since they
    come straight from the request
 */
export interface RatingBody {
  rated_user_id?: unknown;
  punctuality?: unknown;
  sportsmanship?: unknown;
  skill?: unknown;
  reliability?: unknown;
  overall?: unknown;
}

export interface NewRating {
  ratedUserId: number;
  scores: RatingScores;
}

function isStars(value: unknown): boolean {
  return Number.isInteger(value) && Number(value) >= 1 && Number(value) <= 5;
}

/*  checks who is rated and that every category is a whole number of stars from 1 to 5.
    Returns a description of the problem if anything is invalid, naming the categories
 */
export function parseRating(body: RatingBody | undefined): ParseResult<NewRating> {
  const { rated_user_id: ratedUserId, punctuality, sportsmanship, skill, reliability, overall } = body ?? {};
  if (!Number.isInteger(ratedUserId) || Number(ratedUserId) < 1) {
    return { ok: false, error: 'rated_user_id must be a user id' };
  }

  const stars = { punctuality, sportsmanship, skill, reliability, overall };
  const invalid = Object.entries(stars).filter(([, value]) => !isStars(value)).map(([category]) => category);
  if (invalid.length > 0) {
    return { ok: false, error: `${invalid.join(', ')} must be a whole number from 1 to 5` };
  }
  return { ok: true, value: { ratedUserId: Number(ratedUserId), scores: stars as RatingScores } };
}
