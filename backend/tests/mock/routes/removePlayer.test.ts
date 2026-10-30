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
const PLAYER_ID = 2;

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

// Interface DELETE /api/games/{gameId}/players/{userId}
describe('Mocked: DELETE /api/games/{gameId}/players/{userId}', () => {
  // Mocked behavior: the games database is replaced by one upcoming game kept in memory,
  //   with its players in a list. getGameById returns it, and removePlayer takes the
  //   player out of the list like the real DELETE. Sign in and the removed player's
  //   notification are mocked only so the route can run
  // Input: the organizer removes player 2 from upcoming game 42, which has the organizer
  //   and player 2 in it
  // Expected status code: 200
  // Expected behavior: player 2 is no longer in the game, only the organizer is left
  // Expected output: { msg: "success", game } with the game's player_count 1
  test('Organizer removes the second player', async () => {
    // 1. sign in: requireAuth looks the organizer up (not what this test checks)
    jest.spyOn(usersDb, 'findUserById').mockResolvedValue(organizer);
    const token = createAuthToken(organizer.userId);
    // the removed player is told, notifications aren't checked here
    jest.spyOn(push, 'sendPushToUser').mockResolvedValue(0);

    // 2. make a mock game: an upcoming game with the organizer and a second player in it
    const players = [organizer.userId, PLAYER_ID];
    const game: Game = {
      gameId: GAME_ID,
      organizerId: organizer.userId,
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
    const removePlayer = jest.spyOn(gamesDb, 'removePlayer').mockImplementation((_gameId, _organizerId, playerId) => {
      players.splice(players.indexOf(playerId), 1);
      return Promise.resolve({ ok: true, title: game.title });
    });
    expect(players).toEqual([organizer.userId, PLAYER_ID]);

    // 3. remove the player through the interface
    const response = await request(createApp())
      .delete(`/api/games/${GAME_ID}/players/${PLAYER_ID}`)
      .set('Authorization', `Bearer ${token}`);

    // 4. check only the organizer is left in the game
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.game).toMatchObject({ game_id: GAME_ID, player_count: 1 });
    expect(removePlayer).toHaveBeenCalledWith(GAME_ID, organizer.userId, PLAYER_ID);
    expect(players).toEqual([organizer.userId]);
  });
});
