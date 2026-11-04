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
    googleId: `update-game-test-${Date.now()}-${Math.random()}`,
    email: 'update-game-test@example.com',
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

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: a user who isn't the organizer edits the game
  // Expected status code: 403
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "forbidden", error: "Only the organizer can change this game" }
  test('Not the organizer', async () => {
    const gameId = await createGame({
      organizerId,
      title: 'Original Title',
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
      .put(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(otherUserId)}`)
      .send({
        title: 'New Title',
        sport: 'basketball',
        starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
        location_name: 'UBC Rec Centre',
        address: '6000 Student Union Blvd',
        latitude: 49.26,
        longitude: -123.25,
        max_players: 10,
        skill_level: 'any',
      });

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ msg: 'forbidden', error: 'Only the organizer can change this game' });
    expect((await getGameById(gameId))?.title).toBe('Original Title');
  });
});
