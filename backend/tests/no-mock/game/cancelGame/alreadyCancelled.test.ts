import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;

beforeEach(async () => {
  organizerId = await createUser({
    googleId: `cancel-game-test-${Date.now()}-${Math.random()}`,
    email: 'cancel-game-test@example.com',
    firstName: 'Olivia',
    lastName: 'Tester',
    birthYear: 1995,
  });
});

// deleting the user also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/cancel
describe('Unmocked: POST /api/games/{gameId}/cancel', () => {
  // Input: the organizer cancels the same game twice
  // Expected status code: 200 the first time, then 409
  // Expected behavior: the second cancel is rejected
  // Expected output: { msg: "game_cancelled", error: "This game was cancelled" } the second time
  test('Already cancelled', async () => {
    // 1. the organizer's upcoming game
    const gameId = await createGame({
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
    });
    const token = createAuthToken(organizerId);

    // 2. cancel it once through the interface
    const first = await request(createApp())
      .post(`/api/games/${gameId}/cancel`)
      .set('Authorization', `Bearer ${token}`);
    expect(first.status).toBe(200);

    // 3. cancel it again and check that's rejected
    const second = await request(createApp())
      .post(`/api/games/${gameId}/cancel`)
      .set('Authorization', `Bearer ${token}`);
    expect(second.status).toBe(409);
    expect(second.body).toEqual({ msg: 'game_cancelled', error: 'This game was cancelled' });
  });
});
