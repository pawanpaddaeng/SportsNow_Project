import type { ResultSetHeader, RowDataPacket } from 'mysql2';
import { inTransaction, type JoinRequestStatus } from './games';

export type RequestSkillLevel = 'beginner' | 'intermediate' | 'advanced' | 'competitive';
export type PlayFrequency = 'rarely' | 'monthly' | 'weekly' | 'several_per_week';

// answers from the join request popup, matching the join_requests table
export interface NewJoinRequest {
  skillLevel: RequestSkillLevel;
  playFrequency: PlayFrequency | null;
  message: string | null;
}

// why a user can't ask to join a game
export type JoinRequestProblem =
  | 'not_found'
  | 'own_game'
  | 'cancelled'
  | 'started'
  | 'already_player'
  | 'game_full'
  | 'already_requested';

export type CreateJoinRequestResult =
  // organizerId and title so the organizer can be notified
  | { ok: true; requestId: number; organizerId: number; title: string }
  | { ok: false; problem: JoinRequestProblem };

interface JoinableGameRow extends RowDataPacket {
  organizer_id: number;
  title: string;
  status: 'scheduled' | 'cancelled';
  has_started: number;
  max_players: number;
  player_count: number;
  is_player: number;
}

interface ExistingRequestRow extends RowDataPacket {
  request_id: number;
  status: JoinRequestStatus;
}

/*  asks to join the game as userId. A user has one request per game: asking again
    after being declined, cancelling or leaving puts that request back to pending with
    the new answers. The game's row stays locked until the end so a concurrent request,
    approval, edit or cancel can't change it in between
 */
export async function createJoinRequest(
  gameId: number,
  userId: number,
  request: NewJoinRequest,
): Promise<CreateJoinRequestResult> {
  return inTransaction(async (connection) => {
    const [games] = await connection.execute<JoinableGameRow[]>(
      `SELECT g.organizer_id, g.title, g.status, g.max_players,
              g.starts_at <= UTC_TIMESTAMP() AS has_started,
              (SELECT COUNT(*) FROM game_players p WHERE p.game_id = g.game_id) AS player_count,
              EXISTS (SELECT 1 FROM game_players p WHERE p.game_id = g.game_id AND p.user_id = ?) AS is_player
       FROM games g WHERE g.game_id = ? FOR UPDATE`,
      [userId, gameId],
    );
    const game = games[0];
    if (!game) return { ok: false, problem: 'not_found' };
    if (game.organizer_id === userId) return { ok: false, problem: 'own_game' };
    if (game.status === 'cancelled') return { ok: false, problem: 'cancelled' };
    if (Number(game.has_started) === 1) return { ok: false, problem: 'started' };
    if (Number(game.is_player) === 1) return { ok: false, problem: 'already_player' };
    if (Number(game.player_count) >= game.max_players) return { ok: false, problem: 'game_full' };

    const [existing] = await connection.execute<ExistingRequestRow[]>(
      'SELECT request_id, status FROM join_requests WHERE game_id = ? AND user_id = ? FOR UPDATE',
      [gameId, userId],
    );
    const previous = existing[0];
    if (previous?.status === 'pending') return { ok: false, problem: 'already_requested' };

    let requestId: number;
    if (previous) {
      await connection.execute<ResultSetHeader>(
        `UPDATE join_requests
         SET skill_level = ?, play_frequency = ?, message = ?, status = 'pending',
             created_at = CURRENT_TIMESTAMP, responded_at = NULL
         WHERE request_id = ?`,
        [request.skillLevel, request.playFrequency, request.message, previous.request_id],
      );
      requestId = previous.request_id;
    } else {
      const [result] = await connection.execute<ResultSetHeader>(
        `INSERT INTO join_requests (game_id, user_id, skill_level, play_frequency, message)
         VALUES (?, ?, ?, ?, ?)`,
        [gameId, userId, request.skillLevel, request.playFrequency, request.message],
      );
      requestId = result.insertId;
    }

    return { ok: true, requestId, organizerId: game.organizer_id, title: game.title };
  });
}
