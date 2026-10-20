import type { ResultSetHeader, RowDataPacket } from 'mysql2';
import { type GameChangeProblem, inTransaction, type JoinRequestStatus, lockGameForChange } from './games';
import { pool } from './mysql';

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

// a request waiting for the organizer, with who asked and the popup's answers
export interface PendingJoinRequest {
  requestId: number;
  userId: number;
  firstName: string;
  lastName: string;
  skillLevel: RequestSkillLevel;
  playFrequency: PlayFrequency | null;
  message: string | null;
  // UTC ISO 8601, e.g. "2026-10-18T02:00:00Z"
  createdAt: string;
}

interface PendingJoinRequestRow extends RowDataPacket {
  request_id: number;
  user_id: number;
  first_name: string;
  last_name: string;
  skill_level: RequestSkillLevel;
  play_frequency: PlayFrequency | null;
  message: string | null;
  // TIMESTAMP column read as seconds since 1970 so time zones can't shift it
  created_at_unix: number;
}

// the game's requests still waiting for the organizer, newest first
export async function getPendingJoinRequests(gameId: number): Promise<PendingJoinRequest[]> {
  const [rows] = await pool.execute<PendingJoinRequestRow[]>(
    `SELECT r.request_id, r.skill_level, r.play_frequency, r.message,
            UNIX_TIMESTAMP(r.created_at) AS created_at_unix,
            u.user_id, u.first_name, u.last_name
     FROM join_requests r
     JOIN users u ON u.user_id = r.user_id
     WHERE r.game_id = ? AND r.status = 'pending'
     ORDER BY r.created_at DESC, r.request_id DESC`,
    [gameId],
  );
  return rows.map((row) => ({
    requestId: row.request_id,
    userId: row.user_id,
    firstName: row.first_name,
    lastName: row.last_name,
    skillLevel: row.skill_level,
    playFrequency: row.play_frequency,
    message: row.message,
    createdAt: new Date(Number(row.created_at_unix) * 1000).toISOString().replace('.000Z', 'Z'),
  }));
}

// JSON shape of a pending request sent to the organizer's app
export function pendingJoinRequestJson(request: PendingJoinRequest) {
  return {
    request_id: request.requestId,
    user: { user_id: request.userId, first_name: request.firstName, last_name: request.lastName },
    skill_level: request.skillLevel,
    play_frequency: request.playFrequency,
    message: request.message,
    created_at: request.createdAt,
  };
}

// why the organizer can't answer a request: a problem with the game, or with the request itself
export type JoinRequestDecisionProblem = GameChangeProblem | 'request_not_found' | 'already_responded' | 'game_full';

export type DecideJoinRequestResult =
  // requesterId and title so the requester can be notified
  | { ok: true; requesterId: number; title: string }
  | { ok: false; problem: JoinRequestDecisionProblem };

interface LockedRequestRow extends RowDataPacket {
  user_id: number;
  status: JoinRequestStatus;
}

/*  the organizer approves or declines a pending request for their game. Approving adds
    the requester to game_players, so the player count goes up, unless the game is
    already full. The game's row stays locked until the end so two approvals at once
    can't go over max_players
 */
export async function decideJoinRequest(
  gameId: number,
  requestId: number,
  organizerId: number,
  approve: boolean,
): Promise<DecideJoinRequestResult> {
  return inTransaction(async (connection) => {
    const locked = await lockGameForChange(connection, gameId, organizerId);
    if (!locked.ok) return locked;

    const [requests] = await connection.execute<LockedRequestRow[]>(
      'SELECT user_id, status FROM join_requests WHERE request_id = ? AND game_id = ? FOR UPDATE',
      [requestId, gameId],
    );
    const request = requests[0];
    if (!request) return { ok: false, problem: 'request_not_found' };
    if (request.status !== 'pending') return { ok: false, problem: 'already_responded' };

    if (approve) {
      if (Number(locked.game.player_count) >= locked.game.max_players) {
        return { ok: false, problem: 'game_full' };
      }
      // IGNORE: if they somehow joined already, approving just closes the request
      await connection.execute<ResultSetHeader>(
        'INSERT IGNORE INTO game_players (game_id, user_id) VALUES (?, ?)',
        [gameId, request.user_id],
      );
    }

    await connection.execute<ResultSetHeader>(
      'UPDATE join_requests SET status = ?, responded_at = CURRENT_TIMESTAMP WHERE request_id = ?',
      [approve ? 'approved' : 'declined', requestId],
    );
    return { ok: true, requesterId: request.user_id, title: locked.game.title };
  });
}
