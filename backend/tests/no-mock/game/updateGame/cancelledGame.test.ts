import request from 'supertest';

import { createApp } from '../../../../src/app';
import { executeWrite, pool } from '../../../../src/core/db/mysql';
import { createGame, getGameById } from '../../../../src/game/db/games';
import { createAuthToken } from '../../../../src/user/auth/jwt';
import { createUser, deleteUser } from '../../../../src/user/db/users';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

let organizerId: number;

beforeEach(async () => {
  organizerId = await createUser({
    googleId: 'update-game-test-cancelledGame',
    email: 'update-game-test@example.com',
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

// Interface PUT /api/games/{gameId}
describe('Unmocked: PUT /api/games/{gameId}', () => {
  // Input: the organizer edits their cancelled game
  // Expected status code: 409
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "game_cancelled", error: "This game was cancelled" }
  test('Cancelled game', async () => {
    // 1. a cancelled game
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
    await executeWrite("UPDATE games SET status = 'cancelled' WHERE game_id = ?", [gameId]);

    // 2. edit it through the interface
    const response = await request(createApp())
      .put(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
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

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(409);
    expect(response.body).toEqual({ msg: 'game_cancelled', error: 'This game was cancelled' });
    expect((await getGameById(gameId))?.title).toBe('Original Title');
  });
});
