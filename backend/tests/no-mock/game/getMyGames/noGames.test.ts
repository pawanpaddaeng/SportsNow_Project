import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

let userId: number;

// a real user in the database, so the real sign in check accepts their token
beforeEach(async () => {
  userId = await createUser({
    googleId: `get-my-games-test-${Date.now()}-${Math.random()}`,
    email: 'get-my-games-test@example.com',
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

// Interface GET /api/games/mine
describe('Unmocked: GET /api/games/mine', () => {
  // Input: a signed in user who has no games
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "no_games" }
  test('No games', async () => {
    const response = await request(createApp())
      .get('/api/games/mine')
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ msg: 'no_games' });
  });
});
