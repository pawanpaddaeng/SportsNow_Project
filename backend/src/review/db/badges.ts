import { executeWrite, selectRows } from '../../core/db/mysql';
import { type BadgeId, type BadgeProgress, inBadgeOrder, MVP_RATINGS, qualifyingBadges } from '../badges';
import { MAX_STARS } from '../reputation';
import { getReputationScore } from './reputation';

interface ProgressRow {
  games_joined: number;
  games_hosted: number;
  friends: number;
  ratings_given: number;
  games_played: number;
  ratings_received: number;
  perfect_ratings: number;
}

/*  what the user's badges are worked out from, read now. The score is only read when they
    have enough ratings for it to matter, since it's the slowest part
 */
export async function getBadgeProgress(userId: number): Promise<BadgeProgress> {
  const [row] = await selectRows<ProgressRow>(
    `SELECT
       (SELECT COUNT(*) FROM game_players p JOIN games g ON g.game_id = p.game_id
        WHERE p.user_id = ? AND g.organizer_id <> ?) AS games_joined,
       (SELECT COUNT(*) FROM games WHERE organizer_id = ?) AS games_hosted,
       (SELECT COUNT(*) FROM friendships WHERE user_id = ?) AS friends,
       (SELECT COUNT(*) FROM player_ratings WHERE rater_id = ?) AS ratings_given,
       (SELECT COUNT(*) FROM game_attendance WHERE user_id = ? AND no_show = FALSE) AS games_played,
       (SELECT COUNT(*) FROM player_ratings WHERE rated_id = ?) AS ratings_received,
       (SELECT COUNT(*) FROM player_ratings
        WHERE rated_id = ? AND punctuality = ? AND sportsmanship = ? AND skill = ? AND reliability = ?
          AND overall = ?) AS perfect_ratings`,
    [userId, userId, userId, userId, userId, userId, userId,
      userId, MAX_STARS, MAX_STARS, MAX_STARS, MAX_STARS, MAX_STARS],
  );
  const ratingsReceived = Number(row?.ratings_received);
  return {
    gamesJoined: Number(row?.games_joined),
    gamesHosted: Number(row?.games_hosted),
    friends: Number(row?.friends),
    ratingsGiven: Number(row?.ratings_given),
    gamesPlayed: Number(row?.games_played),
    ratingsReceived,
    perfectRatings: Number(row?.perfect_ratings),
    score: ratingsReceived >= MVP_RATINGS ? await getReputationScore(userId) : null,
  };
}

// the badges already saved for the user, in the order the app shows them
async function getSavedBadges(userId: number): Promise<BadgeId[]> {
  const rows = await selectRows<{ badge: string }>('SELECT badge FROM user_badges WHERE user_id = ?', [userId]);
  return inBadgeOrder(rows.map((row) => row.badge));
}

/*  the user's badges, in the order the app shows them. Any they qualify for now that aren't
    saved yet are saved first. A saved badge is never taken away, even if they no longer
    qualify (e.g. they removed a friend)
 */
export async function getBadges(userId: number): Promise<BadgeId[]> {
  const saved = await getSavedBadges(userId);
  const earned = qualifyingBadges(await getBadgeProgress(userId)).filter((badge) => !saved.includes(badge));
  if (earned.length === 0) return saved;

  // IGNORE: the same badge may be saved by another request reading the user's badges at the same time
  await executeWrite(
    `INSERT IGNORE INTO user_badges (user_id, badge) VALUES ${earned.map(() => '(?, ?)').join(', ')}`,
    earned.flatMap((badge) => [userId, badge]),
  );
  return inBadgeOrder([...saved, ...earned]);
}
