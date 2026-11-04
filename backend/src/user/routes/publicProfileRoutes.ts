import { Router, type Request, type Response } from 'express';
import { asyncRoute } from '../../core/http/asyncRoute';
import { invalidRequest } from '../../core/http/responses';
import { type FriendshipStatus, getFriendshipStatus } from '../../social/db/friends';
import { getReputation, type Reputation } from '../../playerReview/db/reputation';
import { getAuthUser, requireAuth } from '../auth/middleware';
import { findUserById, type User } from '../db/users';
import { MIN_AGE } from '../validation/profile';

// largest users.user_id (INT UNSIGNED)
const MAX_USER_ID = 4294967295;

// the user id from /api/users/:userId, or null if it isn't a valid id
function readUserId(raw: string): number | null {
  const userId = /^\d{1,10}$/.test(raw) ? Number(raw) : NaN;
  return Number.isSafeInteger(userId) && userId >= 1 && userId <= MAX_USER_ID ? userId : null;
}

// shown instead of the birth year: the oldest age in each range, then its label
const AGE_RANGES: [number, string][] = [
  [24, `${MIN_AGE}–24`],
  [34, '25–34'],
  [44, '35–44'],
  [54, '45–54'],
  [64, '55–64'],
];

/*  the age range of someone born in birthYear. Only the year is stored, so their age is
    the one they turn this year
 */
export function ageRange(birthYear: number, currentYear: number): string {
  const age = currentYear - birthYear;
  const range = AGE_RANGES.find(([oldest]) => age <= oldest);
  return range ? range[1] : '65+';
}

// how the viewer is connected to the profile's user, 'self' on their own profile
type ProfileConnection = FriendshipStatus | 'self';

/*  the only details of a user other players see. Email and birth year are private, so
    they're never part of it
 */
function publicProfileJson(user: User, reputation: Reputation, connection: ProfileConnection) {
  return {
    user_id: user.userId,
    first_name: user.firstName,
    last_name: user.lastName,
    age_range: ageRange(user.birthYear, new Date().getUTCFullYear()),
    games_played: reputation.gamesPlayed,
    reputation_score: reputation.score,
    badges: reputation.badges,
    friendship_status: connection,
  };
}

// another player's public profile, opened by tapping their name in the app
export function publicProfileRouter(): Router {
  const router = Router();

  /*  public profile: name, age range, games played, reputation score and badges, and how
      the signed in user is connected to them (friendship_status, "self" for their own).
      Errors: 400 invalid_request, 404 user_not_found.
      Mounted after the routers for /api/users/me and /api/users/search, so those aren't read as ids
   */
  router.get('/api/users/:userId', requireAuth, asyncRoute('Could not load this profile, try again later',
    async (req: Request<{ userId: string }>, res: Response) => {
      const viewer = getAuthUser(res);
      const userId = readUserId(req.params.userId);
      if (userId === null) return invalidRequest(res, 'userId must be a positive whole number');

      const user = await findUserById(userId);
      if (!user) return res.status(404).json({ msg: 'user_not_found', error: 'This player does not exist' });

      const connection: ProfileConnection = userId === viewer.userId
        ? 'self'
        : await getFriendshipStatus(viewer.userId, userId);
      const reputation = await getReputation(userId);
      return res.status(200).json({ msg: 'success', profile: publicProfileJson(user, reputation, connection) });
    }));

  return router;
}