import type { ResultSetHeader, RowDataPacket } from 'mysql2';
import { pool } from './mysql';

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
  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const [result] = await connection.execute<ResultSetHeader>(
      `INSERT INTO games (organizer_id, title, sport, description, starts_at, ends_at,
                          location_name, address, latitude, longitude, max_players, skill_level)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        game.organizerId, game.title, game.sport, game.description,
        toUtcDatetime(game.startsAt), game.endsAt ? toUtcDatetime(game.endsAt) : null,
        game.locationName, game.address, game.latitude, game.longitude,
        game.maxPlayers, game.skillLevel,
      ],
    );
    await connection.execute<ResultSetHeader>(
      'INSERT INTO game_players (game_id, user_id) VALUES (?, ?)',
      [result.insertId, game.organizerId],
    );

    await connection.commit();
    return result.insertId;
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    connection.release();
  }
}

export type GameStatus = 'scheduled' | 'cancelled';

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
  createdAt: string;
}

interface GameRow extends RowDataPacket {
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
  // TIMESTAMP column read as seconds since 1970 so time zones can't shift it
  created_at_unix: number;
}

// DATETIME values are already UTC, format them as ISO strings in SQL so the
// connection's time zone can't shift them
const ISO_UTC = "'%Y-%m-%dT%H:%i:%sZ'";

// columns selected for a game, shared by every query that returns games
const GAME_COLUMNS = `
  g.game_id, g.organizer_id, g.title, g.sport, g.description,
  DATE_FORMAT(g.starts_at, ${ISO_UTC}) AS starts_at,
  DATE_FORMAT(g.ends_at, ${ISO_UTC}) AS ends_at,
  g.location_name, g.address, g.latitude, g.longitude,
  g.max_players, g.skill_level, g.status,
  UNIX_TIMESTAMP(g.created_at) AS created_at_unix,
  (SELECT COUNT(*) FROM game_players p WHERE p.game_id = g.game_id) AS player_count`;

export async function getGameById(gameId: number): Promise<Game | null> {
  const [rows] = await pool.execute<GameRow[]>(
    `SELECT ${GAME_COLUMNS} FROM games g WHERE g.game_id = ? LIMIT 1`,
    [gameId],
  );
  const row = rows[0];
  return row ? toGame(row) : null;
}

export interface UserGames {
  // games the user created
  organized: Game[];
  // games the user is a player in but didn't create
  joined: Game[];
}

/*  every game the user organized or joined, soonest first. Includes past and
    cancelled games, each has its status and times so the app can group them
 */
export async function getGamesForUser(userId: number): Promise<UserGames> {
  const [rows] = await pool.execute<GameRow[]>(
    `SELECT ${GAME_COLUMNS}
     FROM games g
     WHERE g.organizer_id = ?
        OR EXISTS (SELECT 1 FROM game_players p WHERE p.game_id = g.game_id AND p.user_id = ?)
     ORDER BY g.starts_at, g.game_id`,
    [userId, userId],
  );
  const games = rows.map(toGame);
  return {
    organized: games.filter((game) => game.organizerId === userId),
    joined: games.filter((game) => game.organizerId !== userId),
  };
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
    // same format as the other times, without milliseconds
    createdAt: new Date(Number(row.created_at_unix) * 1000).toISOString().replace('.000Z', 'Z'),
  };
}

/*  JSON shape of a game sent to the app. Use this for every endpoint that
    returns games (create, get my games, ...) so the app has one game model
 */
export function gameJson(game: Game) {
  return {
    game_id: game.gameId,
    organizer_id: game.organizerId,
    title: game.title,
    sport: game.sport,
    description: game.description,
    starts_at: game.startsAt,
    ends_at: game.endsAt,
    location_name: game.locationName,
    address: game.address,
    latitude: game.latitude,
    longitude: game.longitude,
    max_players: game.maxPlayers,
    player_count: game.playerCount,
    skill_level: game.skillLevel,
    status: game.status,
    created_at: game.createdAt,
  };
}
