import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
// far above any id the tests create
const MISSING_GAME_ID = 999999999;

let userId: number;

// a real user in the database, so the real sign in check accepts their token
beforeEach(async () => {
  userId = await createUser({
    googleId: `update-game-test-${Date.now()}-${Math.random()}`,
    email: 'update-game-test@example.com',
    firstName: 'Uma',
    lastName: 'Tester',
    birthYear: 1995,
  });
});

afterEach(async () => {
  await deleteUser(userId);
});

afterAll(async () => {
  await pool.end();
});

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: a signed in user edits an id with no game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "not_found", error: "This game does not exist" }
  test("Game doesn't exist", async () => {
    const response = await request(createApp())
      .put(`/api/games/${MISSING_GAME_ID}`)
      .set('Authorization', `Bearer ${createAuthToken(userId)}`)
      .send({
        title: 'New Title',
        sport: 'basketball',
        starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
        location_name: 'UBC Rec Centre',
        address: '6000 Student Union Blvd',
        latitude: 49.26,
        longitude: -123.25,
        max_players: 10,
        skill_level: 'any',
      });

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });
});
