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
    googleId: `complete-game-test-${Date.now()}-${Math.random()}`,
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
  // Input: the organizer marks their started game as completed
  // Expected status code: 200
  // Expected behavior: the game goes from "scheduled" (in progress) to "completed" in the database
  // Expected output: { msg: "success", game } with game.status "completed"
  test('Organizer marks their started game completed', async () => {
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
    expect((await getGameById(gameId))?.status).toBe('scheduled');

    // 2. mark it completed through the interface
    const response = await request(createApp())
      .post(`/api/games/${gameId}/complete`)
      .set('Authorization', `Bearer ${createAuthToken(organizerId)}`);

    // 3. check it was completed
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: gameId, status: 'completed' });
    expect((await getGameById(gameId))?.status).toBe('completed');
  });
});
