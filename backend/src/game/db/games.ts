import { createGameChat } from '../../chat/db/chats';
import { executeWrite, inTransaction, selectRows } from '../../core/db/mysql';
import { RATING_WINDOW_HOURS } from '../../review/ratingWindow';
import type { JoinRequestStatus } from './joinRequests';

export type SkillLevel = 'any' | 'beginner' | 'intermediate' | 'advanced';

// what the organizer sets for a game, shared by a new game and one read back
interface GameFields {
  organizerId: number;
  title: string;
  sport: string;
  description: string | null;
  locationName: string;
  address: string;
  latitude: number;
  longitude: number;
  maxPlayers: number;
  skillLevel: SkillLevel;
}

// fields needed to create a game, matching the games table
export interface NewGame extends GameFields {
  startsAt: Date;
  endsAt: Date | null;
}

// games stores times as UTC DATETIME values ("YYYY-MM-DD HH:MM:SS")
export function toUtcDatetime(date: Date): string {
  return date.toISOString().slice(0, 19).replace('T', ' ');
}

/*  creates the game, adds the organizer as its first player and opens its chat in one
    transaction, so a game never exists without its organizer or chat. Returns game_id
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
    await createGameChat(connection, result.insertId);
    return result.insertId;
  });
}

export type GameStatus = 'scheduled' | 'cancelled' | 'completed';

// a game as read back from the database, with how many players are in it
export interface Game extends GameFields {
  gameId: number;
  // UTC times as ISO 8601 strings, e.g. "2026-10-18T02:00:00Z"
  startsAt: string;
  endsAt: string | null;
  playerCount: number;
  status: GameStatus;
  // starts_at has passed by the database's clock. A started game stays 'scheduled'
  // (shown as in progress) until the organizer marks it completed
  hasStarted: boolean;
  // when its players can no longer rate each other (UTC ISO 8601), null until it's completed
  ratingClosesAt: string | null;
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
  rating_closes_at: string | null;
  // TIMESTAMP column read as seconds since 1970 so time zones can't shift it
  created_at_unix: number;
}

// DATETIME values are already UTC, format them as ISO strings in SQL so the
// connection's time zone can't shift them
export const ISO_UTC = "'%Y-%m-%dT%H:%i:%sZ'";

/*  whether game g has started, by the database's clock. The one place "started" is
    decided: the rules use it and every game sent to the app carries it as has_started
 */
export const HAS_STARTED = 'g.starts_at <= UTC_TIMESTAMP()';

// columns selected for a game, shared by every query that returns games
const GAME_COLUMNS = `
  g.game_id, g.organizer_id, g.title, g.sport, g.description,
  DATE_FORMAT(g.starts_at, ${ISO_UTC}) AS starts_at,
  DATE_FORMAT(g.ends_at, ${ISO_UTC}) AS ends_at,
  g.location_name, g.address, g.latitude, g.longitude,
  g.max_players, g.skill_level, g.status,
  ${HAS_STARTED} AS has_started,
  DATE_FORMAT(g.completed_at + INTERVAL ${RATING_WINDOW_HOURS} HOUR, ${ISO_UTC}) AS rating_closes_at,
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
    ratingClosesAt: row.rating_closes_at,
    // same format as the other times, without milliseconds
    createdAt: new Date(Number(row.created_at_unix) * 1000).toISOString().replace('.000Z', 'Z'),
  };
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

// a game that's still on (scheduled, started or not) with the user as one of its players
export interface ActiveGame {
  gameId: number;
  organizerId: number;
  title: string;
  locationName: string;
}

interface ActiveGameRow {
  game_id: number;
  organizer_id: number;
  title: string;
  location_name: string;
}

// the games the user is playing in or organizing that are still on, e.g. to tell them the user deleted their account
export async function getActiveGamesOfUser(userId: number): Promise<ActiveGame[]> {
  const rows = await selectRows<ActiveGameRow>(
    `SELECT g.game_id, g.organizer_id, g.title, g.location_name
     FROM games g
     JOIN game_players p ON p.game_id = g.game_id
     WHERE p.user_id = ? AND g.status = 'scheduled'`,
    [userId],
  );
  return rows.map((row) => ({
    gameId: row.game_id,
    organizerId: row.organizer_id,
    title: row.title,
    locationName: row.location_name,
  }));
}

// a player in a game, with the name shown on the Game Details screen
export interface GamePlayer {
  userId: number;
  firstName: string;
  lastName: string;
  noShow: boolean;
}

interface GamePlayerRow {
  user_id: number;
  first_name: string;
  last_name: string;
  no_show: number;
}

function toPlayer(row: GamePlayerRow): GamePlayer {
  return {
    userId: row.user_id,
    firstName: row.first_name,
    lastName: row.last_name,
    noShow: Number(row.no_show) === 1,
  };
}

// everyone in the game (organizer included), in the order they joined
export async function getGamePlayers(gameId: number): Promise<GamePlayer[]> {
  const rows = await selectRows<GamePlayerRow>(
    `SELECT u.user_id, u.first_name, u.last_name, COALESCE(a.no_show, FALSE) AS no_show
     FROM game_players p
     JOIN users u ON u.user_id = p.user_id
     LEFT JOIN game_attendance a ON a.game_id = p.game_id AND a.user_id = p.user_id
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
    'SELECT user_id, first_name, last_name, 0 AS no_show FROM users WHERE user_id = ? LIMIT 1',
    [game.organizerId],
  );
  return rows[0] ? toPlayer(rows[0]) : null;
}
