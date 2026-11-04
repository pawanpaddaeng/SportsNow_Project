import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

let userId: number;

// a real user in the database, so the real sign in check accepts their token
beforeEach(async () => {
  userId = await createUser({
    googleId: `game-details-test-${Date.now()}-${Math.random()}`,
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
  // Input: a signed in user opens /api/games/abc
  // Expected status code: 400
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "invalid_request", error: "gameId must be a positive whole number" }
  test('Bad id', async () => {
    const response = await request(createApp())
      .get('/api/games/abc')
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ msg: 'invalid_request', error: 'gameId must be a positive whole number' });
  });
});
