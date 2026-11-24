import { placeholders, selectRows } from '../../core/db/mysql';
import { type PlayedGame, type RatingScores, reputationScore } from '../reputation';

// a player's record as other players see it. score is null if they aren't rated yet
export interface Reputation {
  score: number | null;
  gamesPlayed: number;
}

// the most recent games read for a score. Older ones count too little to change it
const GAMES_IN_SCORE = 50;

interface RecentGameRow {
  game_id: number;
  no_show: number;
  rater_count: number;
}

interface ReceivedRatingRow extends RatingScores {
  game_id: number;
}

/*  a player's reputation score, or null if they aren't rated yet. Read from their most
    recent games, newest first by when the game was, with how many others showed up to each.
    Worked out when it's read, since players can still rate a game after it's completed
 */
export async function getReputationScore(userId: number): Promise<number | null> {
  const games = await selectRows<RecentGameRow>(
    `SELECT a.game_id, a.no_show,
            (SELECT COUNT(*) FROM game_attendance other
             WHERE other.game_id = a.game_id AND other.no_show = FALSE AND other.user_id <> a.user_id) AS rater_count
     FROM game_attendance a
     WHERE a.user_id = ?
     ORDER BY a.played_at DESC, a.game_id DESC
     LIMIT ${GAMES_IN_SCORE}`,
    [userId],
  );
  // never played, and an empty IN () isn't valid SQL
  if (games.length === 0) return null;

  const ratings = await selectRows<ReceivedRatingRow>(
    `SELECT game_id, punctuality, sportsmanship, skill, reliability, overall
     FROM player_ratings
     WHERE rated_id = ? AND game_id IN (${placeholders(games.length)})`,
    [userId, ...games.map((game) => game.game_id)],
  );
  const played: PlayedGame[] = games.map((game) => ({
    noShow: Number(game.no_show) === 1,
    raterCount: Number(game.rater_count),
    ratings: ratings.filter((rating) => rating.game_id === game.game_id),
  }));
  return reputationScore(played);
}

/*  completed games the user showed up to, read from the same attendance the score uses so the
    two always agree. Attendance is kept after they leave a game, and after the game is deleted.
    COUNT always returns one row
 */
async function getGamesPlayed(userId: number): Promise<number> {
  const [row] = await selectRows<{ games_played: number }>(
    'SELECT COUNT(*) AS games_played FROM game_attendance WHERE user_id = ? AND no_show = FALSE',
    [userId],
  );
  return Number(row?.games_played);
}

// the user's reputation score and games played, used by the public profile
export async function getReputation(userId: number): Promise<Reputation> {
  return { score: await getReputationScore(userId), gamesPlayed: await getGamesPlayed(userId) };
}
