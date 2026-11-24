import { executeWrite, selectRows } from '../../../src/core/db/mysql';
import { completeGame } from '../../../src/game/db/gameChanges';
import { addPlayer, addStartedGame } from '../helpers';

// setup and checks shared by the unmocked review tests, straight on the database

// the five rating categories, each 1 to 6 stars
export interface Stars {
  punctuality: number;
  sportsmanship: number;
  skill: number;
  reliability: number;
  overall: number;
}

// every category given the same number of stars
export function allStars(stars: number): Stars {
  return { punctuality: stars, sportsmanship: stars, skill: stars, reliability: stars, overall: stars };
}

/*  saves a game that started yesterday with these players besides the organizer, then marks
    it completed now, the way the organizer does, with noShowIds as the players who didn't
    show up. Returns its id
 */
export async function addCompletedGame(organizerId: number, playerIds: number[], noShowIds: number[] = []): Promise<number> {
  const gameId = await addStartedGame(organizerId);
  for (const playerId of playerIds) {
    await addPlayer(gameId, playerId);
  }
  const result = await completeGame(gameId, organizerId, noShowIds);
  if (!result.ok) throw new Error(`Could not complete the test game: ${result.problem}`);
  return gameId;
}

// moves when the game was marked completed to this many minutes ago, to test the rating window
export async function setCompletedMinutesAgo(gameId: number, minutes: number): Promise<void> {
  await executeWrite('UPDATE games SET completed_at = UTC_TIMESTAMP() - INTERVAL ? MINUTE WHERE game_id = ?',
    [minutes, gameId]);
}

// takes the player out of the game's players, as leaving it does. Their attendance stays
export async function removeFromPlayers(gameId: number, userId: number): Promise<void> {
  await executeWrite('DELETE FROM game_players WHERE game_id = ? AND user_id = ?', [gameId, userId]);
}

// saves a rating straight in the database, every category given the same stars
export async function addRating(gameId: number, raterId: number, ratedId: number, stars: number): Promise<void> {
  await executeWrite(
    `INSERT INTO player_ratings (game_id, rater_id, rated_id, punctuality, sportsmanship, skill, reliability, overall)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [gameId, raterId, ratedId, stars, stars, stars, stars, stars],
  );
}

/*  saves that the user was in the game, played at playedAt ("2026-03-01 18:00:00"), as
    marking it completed does
 */
export async function addAttendance(gameId: number, userId: number, noShow: boolean, playedAt: string): Promise<void> {
  await executeWrite('INSERT INTO game_attendance (game_id, user_id, no_show, played_at) VALUES (?, ?, ?, ?)',
    [gameId, userId, noShow, playedAt]);
}

export interface SavedRating extends Stars {
  rater_id: number;
  rated_id: number;
}

// every rating really saved for the game, by rater then rated player
export function getRatings(gameId: number): Promise<SavedRating[]> {
  return selectRows<SavedRating>(
    `SELECT rater_id, rated_id, punctuality, sportsmanship, skill, reliability, overall
     FROM player_ratings WHERE game_id = ? ORDER BY rater_id, rated_id`,
    [gameId],
  );
}

// whether the game's players were already reminded that rating closes soon
export async function wasReminded(gameId: number): Promise<boolean> {
  const rows = await selectRows<{ rating_reminder_sent: number }>(
    'SELECT rating_reminder_sent FROM games WHERE game_id = ?', [gameId]);
  return Number(rows[0]?.rating_reminder_sent) === 1;
}