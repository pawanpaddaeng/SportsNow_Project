import { executeWrite, inTransaction, type Queryable, selectRows } from '../../core/db/mysql';
import type { JoinRequestStatus } from './joinRequests';

export type SkillLevel = 'any' | 'beginner' | 'intermediate' | 'advanced';

// fields needed to create a game, matching the games table
export interface NewGame {
  organizerId: number;
  title: string;
  sport: string;
  description: string | null;
  startsAt: Date;
  endsAt: Date | null;
  locationName: string;
  address: string;
  latitude: number;
  longitude: number;
  maxPlayers: number;
  skillLevel: SkillLevel;
}

// games stores times as UTC DATETIME values ("YYYY-MM-DD HH:MM:SS")
function toUtcDatetime(date: Date): string {
  return date.toISOString().slice(0, 19).replace('T', ' ');
}

/*  creates the game and adds the organizer as its first player in one
    transaction, so a game never exists without its organizer. Returns game_id
 */
export async function createGame(game: NewGame): Promise<number> {
  return inTransaction(async (connection) => {
    const result = await executeWrite(
      `INSERT INTO games (organizer_id, title, sport, description, starts_at, ends_at,
                          location_name, address, latitude, longitude, max_players, skill_level)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        game.organizerId, game.title, game.sport, game.description,
        toUtcDatetime(game.startsAt), game.endsAt ? toUtcDatetime(game.endsAt) : null,
        game.locationName, game.address, game.latitude, game.longitude,
        game.maxPlayers, game.skillLevel,
      ],
      connection,
    );
    await executeWrite(
      'INSERT INTO game_players (game_id, user_id) VALUES (?, ?)',
      [result.insertId, game.organizerId],
      connection,
    );
    return result.insertId;
  });
}

export type GameStatus = 'scheduled' | 'cancelled' | 'completed';

// a game as read back from the database, with how many players are in it
export interface Game {
  gameId: number;
  organizerId: number;
  title: string;
  sport: string;
  description: string | null;
  // UTC times as ISO 8601 strings, e.g. "2026-10-18T02:00:00Z"
  startsAt: string;
  endsAt: string | null;
  locationName: string;
  address: string;
  latitude: number;
  longitude: number;
  maxPlayers: number;
  playerCount: number;
  skillLevel: SkillLevel;
  status: GameStatus;
  // starts_at has passed by the database's clock. A started game stays 'scheduled'
  // (shown as in progress) until the organizer marks it completed
  hasStarted: boolean;
  createdAt: string;
}

interface GameRow {
  game_id: number;
  organizer_id: number;
  title: string;
  sport: string;
  description: string | null;
  starts_at: string;
  ends_at: string | null;
  location_name: string;
  address: string;
  // mysql2 returns DECIMAL columns as strings
  latitude: string;
  longitude: string;
  max_players: number;
  player_count: number;
  skill_level: SkillLevel;
  status: GameStatus;
  has_started: number;
  // TIMESTAMP column read as seconds since 1970 so time zones can't shift it
  created_at_unix: number;
}

// DATETIME values are already UTC, format them as ISO strings in SQL so the
// connection's time zone can't shift them
const ISO_UTC = "'%Y-%m-%dT%H:%i:%sZ'";

/*  whether game g has started, by the database's clock. The one place "started" is
    decided: the rules use it and every game sent to the app carries it as has_started
 */
const HAS_STARTED = 'g.starts_at <= UTC_TIMESTAMP()';

// columns selected for a game, shared by every query that returns games
const GAME_COLUMNS = `
  g.game_id, g.organizer_id, g.title, g.sport, g.description,
  DATE_FORMAT(g.starts_at, ${ISO_UTC}) AS starts_at,
  DATE_FORMAT(g.ends_at, ${ISO_UTC}) AS ends_at,
  g.location_name, g.address, g.latitude, g.longitude,
  g.max_players, g.skill_level, g.status,
  ${HAS_STARTED} AS has_started,
  UNIX_TIMESTAMP(g.created_at) AS created_at_unix,
  (SELECT COUNT(*) FROM game_players p WHERE p.game_id = g.game_id) AS player_count`;

// join requests of game g still waiting for the organizer, the badge on Home and Game Details
export const PENDING_REQUESTS_COUNT =
  "(SELECT COUNT(*) FROM join_requests r WHERE r.game_id = g.game_id AND r.status = 'pending')";

export async function getGameById(gameId: number): Promise<Game | null> {
  const rows = await selectRows<GameRow>(
    `SELECT ${GAME_COLUMNS} FROM games g WHERE g.game_id = ? LIMIT 1`,
    [gameId],
  );
  const row = rows[0];
  return row ? toGame(row) : null;
}

// a game the user organizes, with the join requests waiting for them (the badge on Home)
export interface OrganizedGame {
  game: Game;
  pendingRequests: number;
}

export interface UserGames {
  // games the user created
  organized: OrganizedGame[];
  // games the user is a player in but didn't create
  joined: Game[];
}

interface UserGameRow extends GameRow {
  pending_requests: number;
}

/*  every game the user is still in, organized or joined, soonest first. Includes past,
    cancelled and completed games, each has its status and times so the app can tag them.
    The organizer is a player of their game until they leave it after cancelling or
    completing it, so membership alone decides which games are listed
 */
export async function getGamesForUser(userId: number): Promise<UserGames> {
  const rows = await selectRows<UserGameRow>(
    `SELECT ${GAME_COLUMNS}, ${PENDING_REQUESTS_COUNT} AS pending_requests
     FROM games g
     WHERE EXISTS (SELECT 1 FROM game_players p WHERE p.game_id = g.game_id AND p.user_id = ?)
     ORDER BY g.starts_at, g.game_id`,
    [userId],
  );
  return {
    organized: rows
      .filter((row) => row.organizer_id === userId)
      .map((row) => ({ game: toGame(row), pendingRequests: Number(row.pending_requests) })),
    joined: rows.filter((row) => row.organizer_id !== userId).map(toGame),
  };
}

// how far Discover looks for games around the user
export const MAX_DISCOVER_DISTANCE_M = 50_000;
// most games sent to Discover at once, the closest ones
const MAX_DISCOVER_GAMES = 50;

// a game on the Discover screen, with how far it is and the user's request for it
export interface DiscoverGame {
  game: Game;
  distanceMeters: number;
  joinRequestStatus: JoinRequestStatus | null;
}

interface DiscoverGameRow extends GameRow {
  distance_m: number;
  join_request_status: JoinRequestStatus | null;
}

/*  upcoming scheduled games within MAX_DISCOVER_DISTANCE_M of the point that the
    user could ask to join (not organized or already joined by them), closest first.
    Full games are included so the app can show them as full
 */
export async function getDiscoverGames(userId: number, latitude: number, longitude: number): Promise<DiscoverGame[]> {
  // ST_Distance_Sphere takes POINT(longitude, latitude) and returns metres
  const rows = await selectRows<DiscoverGameRow>(
    `SELECT ${GAME_COLUMNS},
            ROUND(ST_Distance_Sphere(POINT(g.longitude, g.latitude), POINT(?, ?))) AS distance_m,
            (SELECT r.status FROM join_requests r
             WHERE r.game_id = g.game_id AND r.user_id = ?) AS join_request_status
     FROM games g
     WHERE g.status = 'scheduled'
       AND NOT (${HAS_STARTED})
       AND g.organizer_id <> ?
       AND NOT EXISTS (SELECT 1 FROM game_players p WHERE p.game_id = g.game_id AND p.user_id = ?)
     HAVING distance_m <= ${MAX_DISCOVER_DISTANCE_M}
     ORDER BY distance_m, g.starts_at, g.game_id
     LIMIT ${MAX_DISCOVER_GAMES}`,
    [longitude, latitude, userId, userId, userId],
  );
  return rows.map((row) => ({
    game: toGame(row),
    distanceMeters: Number(row.distance_m),
    joinRequestStatus: row.join_request_status,
  }));
}

function toGame(row: GameRow): Game {
  return {
    gameId: row.game_id,
    organizerId: row.organizer_id,
    title: row.title,
    sport: row.sport,
    description: row.description,
    startsAt: row.starts_at,
    endsAt: row.ends_at,
    locationName: row.location_name,
    address: row.address,
    latitude: Number(row.latitude),
    longitude: Number(row.longitude),
    maxPlayers: row.max_players,
    playerCount: Number(row.player_count),
    skillLevel: row.skill_level,
    status: row.status,
    hasStarted: Number(row.has_started) === 1,
    // same format as the other times, without milliseconds
    createdAt: new Date(Number(row.created_at_unix) * 1000).toISOString().replace('.000Z', 'Z'),
  };
}

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

/*  locks the game (see lockGame) and checks the user may still change it: they
    organize it and it's neither cancelled nor started (a completed game has started)
 */
export async function lockGameForChange(
  connection: Queryable,
  gameId: number,
  userId: number,
): Promise<{ ok: true; game: LockedGame } | { ok: false; problem: GameChangeProblem }> {
  const game = await lockGame(connection, gameId, userId);
  if (!game) return { ok: false, problem: 'not_found' };
  if (game.organizerId !== userId) return { ok: false, problem: 'not_organizer' };
  if (game.status === 'cancelled') return { ok: false, problem: 'cancelled' };
  if (game.hasStarted) return { ok: false, problem: 'started' };
  return { ok: true, game };
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

    const result = await executeWrite(
      `UPDATE games
       SET title = ?, sport = ?, description = ?, starts_at = ?, ends_at = ?,
           location_name = ?, address = ?, latitude = ?, longitude = ?,
           max_players = ?, skill_level = ?
       WHERE game_id = ?`,
      [
        changes.title, changes.sport, changes.description,
        toUtcDatetime(changes.startsAt), endsAt ? toUtcDatetime(endsAt) : null,
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
export type CompleteGameProblem = Exclude<GameChangeProblem, 'started'> | 'not_started' | 'completed';

export type CompleteGameResult = { ok: true } | { ok: false; problem: CompleteGameProblem };

/*  the organizer marks their game completed once it has started. Players can then
    leave it, the organizer included. The game stays locked until the end so a
    concurrent cancel or leave can't change it in between
 */
export async function completeGame(gameId: number, userId: number): Promise<CompleteGameResult> {
  return inTransaction(async (connection) => {
    const game = await lockGame(connection, gameId, userId);
    if (!game) return { ok: false, problem: 'not_found' };
    if (game.organizerId !== userId) return { ok: false, problem: 'not_organizer' };
    if (game.status === 'cancelled') return { ok: false, problem: 'cancelled' };
    if (game.status === 'completed') return { ok: false, problem: 'completed' };
    if (!game.hasStarted) return { ok: false, problem: 'not_started' };

    await executeWrite(
      "UPDATE games SET status = 'completed' WHERE game_id = ?",
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
  // organizerId, title and status so the organizer can be told when a player leaves a game that's on
  | { ok: true; organizerId: number; title: string; status: GameStatus }
  | { ok: false; problem: LeaveGameProblem };

/*  removes the user from the game's players. A player can leave at any time, even
    mid-game. The organizer can only leave once the game is cancelled or completed.
    Leaving a game that's cancelled or completed just takes it off their Home screen.
    Leaving before the game starts marks the player's approved join request cancelled
    so they can ask to join again. The game stays locked until the end so a concurrent
    approval, edit, cancel or completion can't change it in between
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
    return { ok: true, organizerId: game.organizerId, title: game.title, status: game.status };
  });
}

interface UserIdRow {
  user_id: number;
}

/*  everyone a change to the game should be told about: its players and anyone
    waiting on a join request, except the organizer who made the change
 */
export async function getUsersToNotify(gameId: number, organizerId: number): Promise<number[]> {
  const rows = await selectRows<UserIdRow>(
    `SELECT user_id FROM game_players WHERE game_id = ? AND user_id <> ?
     UNION
     SELECT user_id FROM join_requests WHERE game_id = ? AND status = 'pending' AND user_id <> ?`,
    [gameId, organizerId, gameId, organizerId],
  );
  return rows.map((row) => row.user_id);
}

// a player in a game, with the name shown on the Game Details screen
export interface GamePlayer {
  userId: number;
  firstName: string;
  lastName: string;
}

interface GamePlayerRow {
  user_id: number;
  first_name: string;
  last_name: string;
}

function toPlayer(row: GamePlayerRow): GamePlayer {
  return { userId: row.user_id, firstName: row.first_name, lastName: row.last_name };
}

// everyone in the game (organizer included), in the order they joined
export async function getGamePlayers(gameId: number): Promise<GamePlayer[]> {
  const rows = await selectRows<GamePlayerRow>(
    `SELECT u.user_id, u.first_name, u.last_name
     FROM game_players p
     JOIN users u ON u.user_id = p.user_id
     WHERE p.game_id = ?
     ORDER BY p.joined_at, u.user_id`,
    [gameId],
  );
  return rows.map(toPlayer);
}

/*  the game's organizer. They're added as a player when the game is created, so
    they're normally in players and only looked up separately if that ever isn't so
 */
export async function findOrganizer(game: Game, players: GamePlayer[]): Promise<GamePlayer | null> {
  const player = players.find((p) => p.userId === game.organizerId);
  if (player) return player;
  const rows = await selectRows<GamePlayerRow>(
    'SELECT user_id, first_name, last_name FROM users WHERE user_id = ? LIMIT 1',
    [game.organizerId],
  );
  return rows[0] ? toPlayer(rows[0]) : null;
}
