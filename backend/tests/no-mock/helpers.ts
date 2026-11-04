import path from 'path';

import { executeWrite, pool, selectRows } from '../../src/core/db/mysql';
import { createGame, type NewGame } from '../../src/game/db/games';
import { createAuthToken } from '../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../src/user/db/users';
import { api, type AuthHeader, ONE_DAY_MS, validGameBody, withToken } from '../sharedHelpers';

export { api, ONE_DAY_MS, validGameBody, withToken };

// setup shared by the unmocked tests, which save real rows in one shared database

// signs a call in as this user, e.g. api.get('/api/games/mine').set(asUser(userId))
export function asUser(userId: number): AuthHeader {
  return withToken(createAuthToken(userId));
}

// the users this test file made, so cleanUpAfterTests can remove them
let addedUserIds: number[] = [];

/*  saves a real user, so the real sign in check accepts their token. google_id must be
    unique and test files run at the same time on one database, so it's built from this
    test file's path and the user's name: the same on every run, never shared between files.
    Use a different name for each user in a file.
 */
export async function addUser(firstName: string): Promise<number> {
  const testFile = path.relative(__dirname, expect.getState().testPath ?? '');
  const userId = await createUser({
    googleId: `${testFile}-${firstName}`,
    email: 'no-mock-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
  addedUserIds.push(userId);
  return userId;
}

// saves an upcoming game, with overrides for what the test is about
export function addGame(organizerId: number, overrides: Partial<NewGame> = {}): Promise<number> {
  return createGame({
    organizerId,
    title: 'Evening Basketball',
    sport: 'basketball',
    description: null,
    startsAt: new Date(Date.now() + ONE_DAY_MS),
    endsAt: null,
    locationName: 'UBC Rec Centre',
    address: '6000 Student Union Blvd',
    latitude: 49.26,
    longitude: -123.25,
    maxPlayers: 10,
    skillLevel: 'any',
    ...overrides,
  });
}

// saves a game that started yesterday
export function addStartedGame(organizerId: number, overrides: Partial<NewGame> = {}): Promise<number> {
  return addGame(organizerId, { startsAt: new Date(Date.now() - ONE_DAY_MS), ...overrides });
}

// saves an upcoming game that was then cancelled
export async function addCancelledGame(organizerId: number, overrides: Partial<NewGame> = {}): Promise<number> {
  const gameId = await addGame(organizerId, overrides);
  await setGameStatus(gameId, 'cancelled');
  return gameId;
}

// a game id far above any id the tests create
export const MISSING_GAME_ID = 999999999;

// test files run at the same time on one database and Discover sees everyone's games, so each
// Discover test searches its own remote spot: this longitude, at a latitude no other test uses
export const SEARCH_LONGITUDE = -150;

// saves an upcoming game at that latitude of the Discover tests' longitude
export function addGameAt(organizerId: number, latitude: number, overrides: Partial<NewGame> = {}): Promise<number> {
  return addGame(organizerId, { latitude, longitude: SEARCH_LONGITUDE, ...overrides });
}

// puts the user in the game directly, without going through a join request
export async function addPlayer(gameId: number, userId: number): Promise<void> {
  await executeWrite('INSERT INTO game_players (game_id, user_id) VALUES (?, ?)', [gameId, userId]);
}

// saves a pending join request and returns its id
export async function addJoinRequest(gameId: number, userId: number): Promise<number> {
  const { insertId } = await executeWrite(
    "INSERT INTO join_requests (game_id, user_id, skill_level, status) VALUES (?, ?, 'beginner', 'pending')",
    [gameId, userId],
  );
  return insertId;
}

// makes two users friends, which is saved once in each direction
export async function addFriends(userId: number, friendId: number): Promise<void> {
  await executeWrite('INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)',
    [userId, friendId, friendId, userId]);
}

// puts the game straight into a status, without going through the interface
export async function setGameStatus(gameId: number, status: 'cancelled' | 'completed'): Promise<void> {
  await executeWrite('UPDATE games SET status = ? WHERE game_id = ?', [status, gameId]);
}

// who is really saved as playing in the game
export async function getPlayerIds(gameId: number): Promise<number[]> {
  const rows = await selectRows<{ user_id: number }>('SELECT user_id FROM game_players WHERE game_id = ?', [gameId]);
  return rows.map((row) => row.user_id);
}

// the join requests really saved for the game
export async function getJoinRequestIds(gameId: number): Promise<number[]> {
  const rows = await selectRows<{ request_id: number }>(
    'SELECT request_id FROM join_requests WHERE game_id = ?', [gameId]);
  return rows.map((row) => row.request_id);
}

// the status really saved for the join request, undefined if there is no such request
export async function getJoinRequestStatus(requestId: number): Promise<string | undefined> {
  const rows = await selectRows<{ status: string }>(
    'SELECT status FROM join_requests WHERE request_id = ?', [requestId]);
  return rows[0]?.status;
}

// removes each test's users after it, and closes the database pool after the file's tests.
// Deleting a user also deletes their games, game players, chats and join requests
// (ON DELETE CASCADE), so only what the test made is removed. They're deleted one at a time:
// users linked by those rows can deadlock if deleted at once
export function cleanUpAfterTests(): void {
  afterEach(async () => {
    const userIds = addedUserIds;
    addedUserIds = [];
    for (const userId of userIds) {
      await deleteUser(userId);
    }
  });

  afterAll(async () => {
    await pool.end();
  });
}
