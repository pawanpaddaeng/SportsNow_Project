import { selectRows } from '../../core/db/mysql';

/*  a player's record as other players see it. score and badges come from ratings, which
    aren't built yet (Rate Players), so for now score is null and badges is empty
 */
export interface Reputation {
  score: number | null;
  badges: string[];
  gamesPlayed: number;
}

/*  completed games the user played in: ones they organized, are still in, or were approved
    for. An approved request stays approved after the game starts, so it still counts after
    they leave the finished game. Games deleted because every player left are lost
 */
const GAMES_PLAYED = `
  SELECT COUNT(*) AS games_played
  FROM games g
  WHERE g.status = 'completed'
    AND (g.organizer_id = ?
         OR EXISTS (SELECT 1 FROM game_players p WHERE p.game_id = g.game_id AND p.user_id = ?)
         OR EXISTS (SELECT 1 FROM join_requests r
                    WHERE r.game_id = g.game_id AND r.user_id = ? AND r.status = 'approved'))`;

// the user's reputation score, badges and games played, used by the public profile
export async function getReputation(userId: number): Promise<Reputation> {
  const rows = await selectRows<{ games_played: number }>(GAMES_PLAYED, [userId, userId, userId]);
  return { score: null, badges: [], gamesPlayed: Number(rows[0]?.games_played ?? 0) };
}