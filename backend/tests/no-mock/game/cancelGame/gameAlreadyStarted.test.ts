import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createGame, getGameById } from '../../../../src/game/db/games';
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
  // Input: the organizer cancels their game that started yesterday
  // Expected status code: 409
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "game_started", error } with the reason
  test('Game already started', async () => {
    const gameId = await createGame({
      organizerId,
      title: 'Evening Basketball',
      sport: 'basketball',
      description: null,
      startsAt: new Date(Date.now() - ONE_DAY_MS),
      endsAt: null,
      locationName: 'UBC Rec Centre',
      address: '6000 Student Union Blvd',
      latitude: 49.26,
      longitude: -123.25,
      maxPlayers: 10,
      skillLevel: 'any',
    });

    const response = await request(createApp())
      .post(`/api/games/${gameId}/cancel`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    expect(response.status).toBe(409);
    expect(response.body.msg).toBe('game_started');
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });
});
