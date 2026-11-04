import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
// test files run at the same time on one database and Discover sees everyone's games,
// so each Discover test searches its own remote spot
const SEARCH_LATITUDE = -10;
const SEARCH_LONGITUDE = -150;

let userId: number;
let organizerId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `discover-games-test-${Date.now()}-${Math.random()}`,
    email: 'discover-games-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  userId = await addUser('Uma');
  organizerId = await addUser('Omar');
});

// deleting the users also deletes their games, game players, chats and join requests
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(userId);
  await deleteUser(organizerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's location, where they have a pending join request for a nearby game
  // Expected status code: 200
  // Expected behavior: the game is listed with the user's request status, nothing changes
  // Expected output: { msg: "success", games } with the game, join_request_status "pending"
  test('Request status', async () => {
    // 1. a nearby game the user asked to join
    const gameId = await createGame({
      organizerId,
      title: 'Requested Game',
      sport: 'basketball',
      description: null,
      startsAt: new Date(Date.now() + ONE_DAY_MS),
      endsAt: null,
      locationName: 'Test Field',
      address: '1 Test Rd',
      latitude: SEARCH_LATITUDE,
      longitude: SEARCH_LONGITUDE,
      maxPlayers: 10,
      skillLevel: 'any',
    });
    await executeWrite(
      "INSERT INTO join_requests (game_id, user_id, skill_level, status) VALUES (?, ?, 'beginner', 'pending')",
      [gameId, userId],
    );

    // 2. discover games through the interface
    const response = await request(createApp())
      .get('/api/games/discover')
      .query({ lat: SEARCH_LATITUDE, lng: SEARCH_LONGITUDE })
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    // 3. check the game shows the pending request
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.games).toHaveLength(1);
    expect(response.body.games[0].game.game_id).toBe(gameId);
    expect(response.body.games[0].join_request_status).toBe('pending');
  });
});
