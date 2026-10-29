import request from 'supertest';

import { createApp } from '../../../src/app';
import { createAuthToken } from '../../../src/user/auth/jwt';
import * as gamesDb from '../../../src/game/db/games';
import { type Game } from '../../../src/game/db/games';
import { pool } from '../../../src/core/db/mysql';
import * as usersDb from '../../../src/user/db/users';
import { type User } from '../../../src/user/db/users';

// the server reads its settings from the environment when it loads; with every
// database call mocked it needs no real ones (jest.mock runs before the imports)
jest.mock('../../../src/core/config/env', () => ({
  env: {
    port: 3000,
    mysqlUrl: 'mysql://test:test@localhost:3306/test',
    mysqlUser: 'test',
    mysqlPassword: 'test',
    mysqlDatabase: 'test',
    jwtSecret: 'test-jwt-secret',
  },
}));

const GAME_ID = 42;

const organizer: User = {
  userId: 1,
  googleId: 'google-organizer',
  email: 'organizer@example.com',
  firstName: 'Olivia',
  lastName: 'Organizer',
  birthYear: 1995,
  // before the token is made, so requireAuth accepts it
  createdAt: new Date('2026-01-01T00:00:00Z'),
};

afterEach(() => {
  jest.restoreAllMocks();
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/complete
describe('Mocked: POST /api/games/{gameId}/complete', () => {
  // Mocked behavior: the games database is replaced by one game kept in memory that
  //   has started. getGameById returns it and completeGame marks it completed, like
  //   the real UPDATE. Sign in and notifications are mocked only so the route can run
  // Input: the organizer marks their started game 42 as completed
  // Expected status code: 200
  // Expected behavior: the game goes from "scheduled" (in progress) to "completed"
  // Expected output: { msg: "success", game } with game.status "completed"
  test('Organizer marks their started game completed', async () => {
    // 1. sign in: requireAuth looks the organizer up (not what this test checks)
    jest.spyOn(usersDb, 'findUserById').mockResolvedValue(organizer);
    const token = createAuthToken(organizer.userId);
    // nobody to notify after completing, notifications aren't checked here
    jest.spyOn(gamesDb, 'getUsersToNotify').mockResolvedValue([]);

    // 2. make a mock game: the organizer's game that has started (in progress), kept in memory
    const game: Game = {
      gameId: GAME_ID,
      organizerId: organizer.userId,
      title: 'Sunday Soccer',
      sport: 'soccer',
      description: null,
      startsAt: '2026-01-06T18:00:00Z',
      endsAt: null,
      locationName: 'Jericho Park',
      address: '3941 Point Grey Rd, Vancouver',
      latitude: 49.2722,
      longitude: -123.1957,
      maxPlayers: 10,
      playerCount: 3,
      skillLevel: 'any',
      status: 'scheduled',
      hasStarted: true,
      createdAt: '2026-01-01T00:00:00Z',
    };
    jest.spyOn(gamesDb, 'getGameById').mockImplementation((gameId) =>
      Promise.resolve(gameId === game.gameId ? game : null));
    const completeGame = jest.spyOn(gamesDb, 'completeGame').mockImplementation(() => {
      game.status = 'completed';
      return Promise.resolve({ ok: true });
    });
    expect(game.status).toBe('scheduled');

    // 3. mark it completed through the interface
    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/complete`)
      .set('Authorization', `Bearer ${token}`);

    // 4. check it was completed
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: GAME_ID, status: 'completed' });
    expect(completeGame).toHaveBeenCalledWith(GAME_ID, organizer.userId);
    expect(game.status).toBe('completed');
  });
});
