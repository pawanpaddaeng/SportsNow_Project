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
    googleId: 'update-game-test-badField',
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
  // Input: the organizer edits their game with sport "chess"
  // Expected status code: 400
  // Expected behavior: the game doesn't change
  // Expected output: { msg: "invalid_request", error } listing the allowed sports
  test('Bad field', async () => {
    // 1. a game to edit
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

    // 2. edit it with a sport that isn't allowed through the interface
    const response = await request(createApp())
      .put(`/api/games/${gameId}`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`)
      .send({
        title: 'New Title',
        sport: 'chess',
        starts_at: new Date(Date.now() + ONE_DAY_MS).toISOString(),
        location_name: 'UBC Rec Centre',
        address: '6000 Student Union Blvd',
        latitude: 49.26,
        longitude: -123.25,
        max_players: 10,
        skill_level: 'any',
      });

    // 3. check it was rejected and nothing changed
    expect(response.status).toBe(400);
    expect(response.body).toEqual({
      msg: 'invalid_request',
      error: 'sport must be one of: basketball, soccer, volleyball, tennis, other',
    });
    const saved = await getGameById(gameId);
    expect(saved?.title).toBe('Original Title');
    expect(saved?.sport).toBe('basketball');
  });
});
