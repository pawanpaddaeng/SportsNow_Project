import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool, selectRows } from '../../../../src/core/db/mysql';
import { createGame } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;

beforeEach(async () => {
  organizerId = await createUser({
    googleId: 'send-join-request-test-ownGame',
    email: 'send-join-request-test@example.com',
    firstName: 'Olivia',
    lastName: 'Tester',
    birthYear: 1995,
  });
});

// deleting the user also deletes their games, game players, chats and join requests
// (ON DELETE CASCADE), so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/join-requests
describe('Unmocked: POST /api/games/{gameId}/join-requests', () => {
  // Input: the organizer asks to join their own game
  // Expected status code: 409
  // Expected behavior: nothing is saved
  // Expected output: { msg: "own_game", error: "You organize this game" }
  test('Own game', async () => {
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

    const response = await request(createApp())
      .post(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({ skill_level: 'beginner' });

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'own_game', error: 'You organize this game' });
    const rows = await selectRows<{ request_id: number }>(
      'SELECT request_id FROM join_requests WHERE game_id = ?', [gameId]);
    expect(rows).toEqual([]);
  });
});
