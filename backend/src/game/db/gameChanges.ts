import { executeWrite, inTransaction, placeholders, type Queryable, selectRows } from '../../core/db/mysql';
import { type GameStatus, HAS_STARTED, ISO_UTC, type NewGame, toUtcDatetime } from './games';

// why the organizer can't change a game: it's gone, someone else's, cancelled or already started
export type GameChangeProblem = 'not_found' | 'not_organizer' | 'cancelled' | 'started';

export type UpdateGameResult =
  | { ok: true; changed: boolean }
  | { ok: false; problem: GameChangeProblem }
  // fewer spots than players already in the game
  | { ok: false; problem: 'too_few_players'; playerCount: number }
  // the new start is not before the game's existing end time
  | { ok: false; problem: 'ends_before_start' };

// what a change to a game checks first, read from its locked row
export interface LockedGame {
  organizerId: number;
  title: string;
  status: GameStatus;
  // UTC ISO 8601, like Game.endsAt
  endsAt: string | null;
  hasStarted: boolean;
  maxPlayers: number;
  playerCount: number;
  // whether the user passed to lockGame is one of its players
  isPlayer: boolean;
}

interface LockedGameRow {
  organizer_id: number;
  title: string;
  status: GameStatus;
  ends_at: string | null;
  has_started: number;
  max_players: number;
  player_count: number;
  is_player: number;
}

/*  locks the game's row until the transaction ends so a concurrent edit, cancel,
    completion, join or leave can't change it in between. Every change to a game
    locks it through here. Returns null if no game has the id
 */
export async function lockGame(connection: Queryable, gameId: number, userId: number): Promise<LockedGame | null> {
  const rows = await selectRows<LockedGameRow>(
    `SELECT g.organizer_id, g.title, g.status, g.max_players,
            DATE_FORMAT(g.ends_at, ${ISO_UTC}) AS ends_at,
            ${HAS_STARTED} AS has_started,
            (SELECT COUNT(*) FROM game_players p WHERE p.game_id = g.game_id) AS player_count,
            EXISTS (SELECT 1 FROM game_players p WHERE p.game_id = g.game_id AND p.user_id = ?) AS is_player
     FROM games g WHERE g.game_id = ? FOR UPDATE`,
    [userId, gameId],
    connection,
  );
  const row = rows[0];
  if (!row) return null;
  return {
    organizerId: row.organizer_id,
    title: row.title,
    status: row.status,
    endsAt: row.ends_at,
    hasStarted: Number(row.has_started) === 1,
    maxPlayers: row.max_players,
    playerCount: Number(row.player_count),
    isPlayer: Number(row.is_player) === 1,
  };
}

// why the organizer can't act on a game at all: it's gone, it isn't theirs, or it's cancelled
export type OrganizedGameProblem = 'not_found' | 'not_organizer' | 'cancelled';

/*  locks the game (see lockGame) and checks the user organizes it and it isn't
    cancelled, the checks before an edit, cancel or completion
 */
async function lockOrganizedGame(
  connection: Queryable,
  gameId: number,
  userId: number,
): Promise<{ ok: true; game: LockedGame } | { ok: false; problem: OrganizedGameProblem }> {
  const game = await lockGame(connection, gameId, userId);
  if (!game) return { ok: false, problem: 'not_found' };
  if (game.organizerId !== userId) return { ok: false, problem: 'not_organizer' };
  if (game.status === 'cancelled') return { ok: false, problem: 'cancelled' };
  return { ok: true, game };
}

/*  locks the game and checks the user may still change it: they organize it and it's
    neither cancelled nor started (a completed game has started)
 */
export async function lockGameForChange(
  connection: Queryable,
  gameId: number,
  userId: number,
): Promise<{ ok: true; game: LockedGame } | { ok: false; problem: GameChangeProblem }> {
  const locked = await lockOrganizedGame(connection, gameId, userId);
  if (!locked.ok) return locked;
  if (locked.game.hasStarted) return { ok: false, problem: 'started' };
  return locked;
}

/*  replaces the organizer's game details with changes (organizerId is ignored, the
    organizer stays the same). endsAt is only changed when keepEndsAt is false, the
    app has no end time field so edits from it keep the existing one.
    max_players can't go below the players already in the game
 */
export async function updateGame(
  gameId: number,
  userId: number,
  changes: NewGame,
  keepEndsAt: boolean,
): Promise<UpdateGameResult> {
  return inTransaction(async (connection) => {
    const locked = await lockGameForChange(connection, gameId, userId);
    if (!locked.ok) return locked;

    const { playerCount } = locked.game;
    if (changes.maxPlayers < playerCount) {
      return { ok: false, problem: 'too_few_players', playerCount };
    }

    const endsAt = keepEndsAt
      ? (locked.game.endsAt ? new Date(locked.game.endsAt) : null)
      : changes.endsAt;
    if (endsAt && endsAt.getTime() <= changes.startsAt.getTime()) {
      return { ok: false, problem: 'ends_before_start' };
    }

    // a new start time clears start_reminder_sent, so players are reminded before the new time.
    // MySQL sets the values in order, so it's compared before starts_at changes
    const startsAt = toUtcDatetime(changes.startsAt);
    const result = await executeWrite(
      `UPDATE games
       SET start_reminder_sent = IF(starts_at = ?, start_reminder_sent, FALSE),
           title = ?, sport = ?, description = ?, starts_at = ?, ends_at = ?,
           location_name = ?, address = ?, latitude = ?, longitude = ?,
           max_players = ?, skill_level = ?
       WHERE game_id = ?`,
      [
        startsAt, changes.title, changes.sport, changes.description,
        startsAt, endsAt ? toUtcDatetime(endsAt) : null,
        changes.locationName, changes.address, changes.latitude, changes.longitude,
        changes.maxPlayers, changes.skillLevel, gameId,
      ],
      connection,
    );
    // changedRows is 0 when every value was already the same
    return { ok: true, changed: result.changedRows > 0 };
  });
}

export type CancelGameResult = { ok: true } | { ok: false; problem: GameChangeProblem };

// marks the organizer's game cancelled, it stays so players can see what happened
export async function cancelGame(gameId: number, userId: number): Promise<CancelGameResult> {
  return inTransaction(async (connection) => {
    const locked = await lockGameForChange(connection, gameId, userId);
    if (!locked.ok) return locked;

    await executeWrite(
      "UPDATE games SET status = 'cancelled' WHERE game_id = ?",
      [gameId],
      connection,
    );
    return { ok: true };
  });
}

// why the organizer can't mark a game completed: as for edits, or it hasn't started or is already completed
export type CompleteGameProblem = OrganizedGameProblem | 'not_started' | 'completed';

export type CompleteGameResult = { ok: true } | { ok: false; problem: CompleteGameProblem };

/*  the organizer marks their game completed once it has started, with the players who
    didn't show up. Players can then leave it, the organizer included. An id that isn't
    a player is skipped since they may have left meanwhile, and the organizer can't be a
    no-show. The game stays locked until the end so a concurrent cancel or leave can't
    change it in between
 */
export async function completeGame(
  gameId: number,
  userId: number,
  noShowUserIds: number[] = [],
): Promise<CompleteGameResult> {
  return inTransaction(async (connection) => {
    const locked = await lockOrganizedGame(connection, gameId, userId);
    if (!locked.ok) return locked;
    if (locked.game.status === 'completed') return { ok: false, problem: 'completed' };
    if (!locked.game.hasStarted) return { ok: false, problem: 'not_started' };

    // kept apart from game_players so leaving the game doesn't erase it from a player's score
    const noShow = noShowUserIds.length > 0
      ? `p.user_id <> ? AND p.user_id IN (${placeholders(noShowUserIds.length)})`
      : 'FALSE';
    await executeWrite(
      `INSERT INTO game_attendance (game_id, user_id, no_show, played_at)
       SELECT p.game_id, p.user_id, ${noShow}, g.starts_at
       FROM game_players p
       JOIN games g ON g.game_id = p.game_id
       WHERE p.game_id = ?`,
      [...(noShowUserIds.length > 0 ? [userId, ...noShowUserIds] : []), gameId],
      connection,
    );
    await executeWrite(
      "UPDATE games SET status = 'completed', completed_at = UTC_TIMESTAMP() WHERE game_id = ?",
      [gameId],
      connection,
    );
    return { ok: true };
  });
}

/*  why the user can't leave a game: it's gone, they aren't in it, or they organize it
    and it's still on (they cancel it instead) or started (they mark it completed first)
 */
export type LeaveGameProblem = 'not_found' | 'not_player' | 'organizer_must_cancel' | 'organizer_must_complete';

export type LeaveGameResult =
  // organizerId, title and status so the organizer can be told when a player leaves a game that's on.
  // deleted when the user was the last player, so the game no longer exists
  | { ok: true; organizerId: number; title: string; status: GameStatus; deleted: boolean }
  | { ok: false; problem: LeaveGameProblem };

/*  removes the user from the game's players. A player can leave at any time, even
    mid-game. The organizer can only leave once the game is cancelled or completed.
    Leaving a game that's cancelled or completed just takes it off their Home screen.
    Leaving before the game starts marks the player's approved join request cancelled
    so they can ask to join again. When the last player leaves the game is deleted,
    with its players and join requests. That can only be a cancelled or completed game,
    since the organizer stays a player until then. The game stays locked until the end
    so a concurrent approval, edit, cancel or completion can't change it in between
 */
export async function leaveGame(gameId: number, userId: number): Promise<LeaveGameResult> {
  return inTransaction(async (connection) => {
    const game = await lockGame(connection, gameId, userId);
    if (!game) return { ok: false, problem: 'not_found' };
    if (!game.isPlayer) return { ok: false, problem: 'not_player' };
    if (game.status === 'scheduled' && game.organizerId === userId) {
      return { ok: false, problem: game.hasStarted ? 'organizer_must_complete' : 'organizer_must_cancel' };
    }

    await executeWrite(
      'DELETE FROM game_players WHERE game_id = ? AND user_id = ?',
      [gameId, userId],
      connection,
    );
    // once the game started the request stays approved as a record that they played
    if (game.status === 'scheduled' && !game.hasStarted) {
      await executeWrite(
        `UPDATE join_requests SET status = 'cancelled'
         WHERE game_id = ? AND user_id = ? AND status = 'approved'`,
        [gameId, userId],
        connection,
      );
    }

    // the locked count still includes the user who just left
    const deleted = game.playerCount <= 1;
    if (deleted) {
      // join_requests and any game_players rows go with it (ON DELETE CASCADE)
      await executeWrite('DELETE FROM games WHERE game_id = ?', [gameId], connection);
    }
    return { ok: true, organizerId: game.organizerId, title: game.title, status: game.status, deleted };
  });
}

/*  why the organizer can't remove a player: as for edits (only their own game, until it
    starts), or the player is the organizer themselves or isn't in the game
 */
export type RemovePlayerProblem = GameChangeProblem | 'organizer_self' | 'player_not_found';

export type RemovePlayerResult =
  // title so the removed player can be told which game
  | { ok: true; title: string }
  | { ok: false; problem: RemovePlayerProblem };

/*  the organizer removes a player from their game before it starts. The player's
    approved join request is marked declined, so they can send a new request later.
    The game stays locked until the end so a concurrent approval, edit, cancel or
    leave can't change it in between
 */
export async function removePlayer(gameId: number, organizerId: number, playerId: number): Promise<RemovePlayerResult> {
  return inTransaction(async (connection) => {
    const locked = await lockGameForChange(connection, gameId, organizerId);
    if (!locked.ok) return locked;
    if (playerId === organizerId) return { ok: false, problem: 'organizer_self' };

    const removed = await executeWrite(
      'DELETE FROM game_players WHERE game_id = ? AND user_id = ?',
      [gameId, playerId],
      connection,
    );
    if (removed.affectedRows === 0) return { ok: false, problem: 'player_not_found' };

    await executeWrite(
      `UPDATE join_requests SET status = 'declined', responded_at = CURRENT_TIMESTAMP
       WHERE game_id = ? AND user_id = ? AND status = 'approved'`,
      [gameId, playerId],
      connection,
    );
    return { ok: true, title: locked.game.title };
  });
}
