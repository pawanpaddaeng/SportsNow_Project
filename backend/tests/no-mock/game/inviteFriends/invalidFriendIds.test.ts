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
    googleId: `invite-friends-test-${Date.now()}-${Math.random()}`,
    email: 'invite-friends-test@example.com',
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

// Interface POST /api/games/{gameId}/invites
describe('Unmocked: POST /api/games/{gameId}/invites', () => {
  // Input: friend_ids missing, empty, or with something that isn't a user id
  // Expected status code: 400
  // Expected behavior: the invite is rejected
  // Expected output: { msg: "invalid_request", error: "friend_ids must be a list of 1 to 100 user ids" }
  test.each([
    ['missing', {}],
    ['empty', { friend_ids: [] }],
    ['text', { friend_ids: ['5'] }],
    ['not whole', { friend_ids: [5.5] }],
    ['zero', { friend_ids: [0] }],
  ])('friend_ids %s is rejected', async (_name, body) => {
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
      .post(`/api/games/${gameId}/invites`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'friend_ids must be a list of 1 to 100 user ids',
    });
  });
});
