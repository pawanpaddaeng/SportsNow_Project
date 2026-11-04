import request from 'supertest';

import { createApp } from '../../../../src/app';
import { pool } from '../../../../src/core/db/mysql';
import { createGame, getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;
let otherUserId: number;

function addUser(firstName: string): Promise<number> {
  return createUser({
    googleId: `cancel-game-test-${Date.now()}-${Math.random()}`,
    email: 'cancel-game-test@example.com',
    firstName,
    lastName: 'Tester',
    birthYear: 1995,
  });
}

beforeEach(async () => {
  organizerId = await addUser('Olivia');
  otherUserId = await addUser('Omar');
});

// deleting the users also deletes their games, game players and chats (ON DELETE CASCADE),
// so only what this test made is removed
afterEach(async () => {
  await deleteUser(organizerId);
  await deleteUser(otherUserId);
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/cancel
describe('Unmocked: POST /api/games/{gameId}/cancel', () => {
  // Input: a user who isn't the organizer cancels the game
  // Expected status code: 403
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "forbidden", error: "Only the organizer can change this game" }
  test('Not the organizer', async () => {
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
      .post(`/api/games/${gameId}/cancel`)
      .set('Authorization', `Bearer ${createAuthToken(otherUserId)}`);

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can change this game' });
    expect((await getGameById(gameId))?.status).toBe('scheduled');
  });
});
