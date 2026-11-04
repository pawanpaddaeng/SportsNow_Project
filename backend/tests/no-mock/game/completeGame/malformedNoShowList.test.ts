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
    googleId: 'complete-game-test-malformedNoShowList',
    email: 'complete-game-test@example.com',
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

// Interface POST /api/games/{gameId}/complete
describe('Unmocked: POST /api/games/{gameId}/complete', () => {
  // Input: the organizer completes their started game with a no-show id that isn't a user id
  // Expected status code: 400
  // Expected behavior: the game is left as it was
  // Expected output: { msg: "invalid_request", error: "no_show_user_ids must be a list of user ids" }
  test('A malformed no-show list is rejected', async () => {
    // 1. the organizer's game that started yesterday
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

    // 2. complete it with a malformed list through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/complete`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ no_show_user_ids: ['seven'] });

    // 3. check it was rejected and the game didn't change
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'no_show_user_ids must be a list of user ids',
    });
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });
});
