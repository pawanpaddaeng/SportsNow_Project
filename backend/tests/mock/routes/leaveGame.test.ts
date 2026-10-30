import request from 'supertest';

import { createApp } from '../../../src/app';
import { createAuthToken } from '../../../src/user/auth/jwt';
import * as gamesDb from '../../../src/game/db/games';
import { type Game } from '../../../src/game/db/games';
import { pool } from '../../../src/core/db/mysql';
import * as usersDb from '../../../src/user/db/users';
import { type User } from '../../../src/user/db/users';
import * as push from '../../../src/core/notifications/push';

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
const ORGANIZER_ID = 1;
const OTHER_PLAYER_ID = 3;

const player: User = {
  userId: 2,
  googleId: 'google-player',
  email: 'player@example.com',
  firstName: 'Paul',
  lastName: 'Player',
  birthYear: 1998,
  // before the token is made, so requireAuth accepts it
  createdAt: new Date('2026-01-01T00:00:00Z'),
};

afterEach(() => {
  jest.restoreAllMocks();
});

afterAll(async () => {
  await pool.end();
});

// Interface POST /api/games/{gameId}/leave
describe('Mocked: POST /api/games/{gameId}/leave', () => {
  // Mocked behavior: the games database is replaced by one upcoming game kept in memory,
  //   with its players in a list. getGameById returns it, and leaveGame takes the user
  //   out of the list like the real DELETE. Sign in and the organizer's "Player left"
  //   notification are mocked only so the route can run
  // Input: a player leaves upcoming game 42, which also has another player in it
  // Expected status code: 200
  // Expected behavior: the player is no longer in the game; the other player still is,
  //   and the game still exists
  // Expected output: { msg: "success", game } with the game's player_count down by one
  test('Player leaves a game that has another player', async () => {
    // 1. sign in: requireAuth looks the player up (not what this test checks)
    jest.spyOn(usersDb, 'findUserById').mockResolvedValue(player);
    const token = createAuthToken(player.userId);
    // the organizer is told a player left, notifications aren't checked here
    jest.spyOn(push, 'sendPushToUser').mockResolvedValue(0);

    // 2. make a mock game: an upcoming game with the player and another player in it
    const players = [ORGANIZER_ID, player.userId, OTHER_PLAYER_ID];
    const game: Game = {
      gameId: GAME_ID,
      organizerId: ORGANIZER_ID,
      title: 'Sunday Soccer',
      sport: 'soccer',
      description: null,
      startsAt: '2030-01-06T18:00:00Z',
      endsAt: null,
      locationName: 'Jericho Park',
      address: '3941 Point Grey Rd, Vancouver',
      latitude: 49.2722,
      longitude: -123.1957,
      maxPlayers: 10,
      playerCount: players.length,
      skillLevel: 'any',
      status: 'scheduled',
      hasStarted: false,
      createdAt: '2026-10-01T00:00:00Z',
    };
    jest.spyOn(gamesDb, 'getGameById').mockImplementation((gameId) =>
      Promise.resolve(gameId === game.gameId ? { ...game, playerCount: players.length } : null));
    const leaveGame = jest.spyOn(gamesDb, 'leaveGame').mockImplementation((_gameId, userId) => {
      players.splice(players.indexOf(userId), 1);
      return Promise.resolve({
        ok: true,
        organizerId: game.organizerId,
        title: game.title,
        status: game.status,
        deleted: players.length === 0,
      });
    });
    expect(players).toContain(player.userId);

    // 3. leave it through the interface
    const response = await request(createApp())
      .post(`/api/games/${GAME_ID}/leave`)
      .set('Authorization', `Bearer ${token}`);

    // 4. check the player left, and the other player and the game are still there
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: GAME_ID, status: 'scheduled', player_count: 2 });
    expect(leaveGame).toHaveBeenCalledWith(GAME_ID, player.userId);
    expect(players).not.toContain(player.userId);
    expect(players).toContain(OTHER_PLAYER_ID);
    await expect(gamesDb.getGameById(GAME_ID)).resolves.not.toBeNull();
  });
});
