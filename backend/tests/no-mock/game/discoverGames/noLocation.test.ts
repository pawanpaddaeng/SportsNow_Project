import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

let userId: number;

// a real user in the database, so the real sign in check accepts their token
beforeEach(async () => {
  userId = await createUser({
    googleId: 'discover-games-test-noLocation',
    email: 'discover-games-test@example.com',
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

// Interface GET /api/games/discover
describe('Unmocked: GET /api/games/discover', () => {
  // Input: a signed in user's request with lng but no lat in the query
  // Expected status code: 400
  // Expected behavior: the request is rejected, nothing changes
  // Expected output: { msg: "invalid_request", error: "lat (-90 to 90) and lng (-180 to 180) are required numbers" }
  test('No location', async () => {
    const response = await request(createApp())
      .get('/api/games/discover')
      .query({ lng: 0 })
      .set('Authorization', `Bearer ${createAuthToken(userId)}`);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'lat (-90 to 90) and lng (-180 to 180) are required numbers',
    });
  });
});
