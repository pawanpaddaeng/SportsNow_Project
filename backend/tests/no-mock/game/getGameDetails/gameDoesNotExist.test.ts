import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

// far above any id the tests create
const MISSING_GAME_ID = 999999999;

let userId: number;

// a real user in the database, so the real sign in check accepts their token
beforeEach(async () => {
  userId = await createUser({
    googleId: 'game-details-test-gameDoesNotExist',
    email: 'game-details-test@example.com',
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

// Interface GET /api/games/{gameId}
describe('Unmocked: GET /api/games/{gameId}', () => {
  // Input: a signed in user opens an id with no game
  // Expected status code: 404
  // Expected behavior: nothing changes
  // Expected output: { msg: "not_found", error: "This game does not exist" }
  test("Game doesn't exist", async () => {
    const response = await request(createApp())
      .get(`/api/games/${MISSING_GAME_ID}`)
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ msg: 'not_found', error: 'This game does not exist' });
  });
});
