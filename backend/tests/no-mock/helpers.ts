import path from 'path';

import { executeWrite, pool } from '../../src/core/db/mysql';
import { createGame, type NewGame } from '../../src/game/db/games';
import { createUser, deleteUser } from '../../src/user/db/users';

// setup shared by the unmocked tests, which save real rows in one shared database

export const ONE_DAY_MS = 24 * 60 * 60 * 1000;

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
