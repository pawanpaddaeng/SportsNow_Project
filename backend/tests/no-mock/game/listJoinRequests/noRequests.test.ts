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
    googleId: 'list-join-requests-test-noRequests',
    email: 'list-join-requests-test@example.com',
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

// Interface GET /api/games/{gameId}/join-requests
describe('Unmocked: GET /api/games/{gameId}/join-requests', () => {
  // Input: the organizer opens the list for their game, which nobody has asked to join
  // Expected status code: 200
  // Expected behavior: nothing changes
  // Expected output: { msg: "success", game, requests } with requests an empty list
  test('No requests', async () => {
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
      .get(`/api/games/${gameId}/join-requests`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.requests).toEqual([]);
  });
});
