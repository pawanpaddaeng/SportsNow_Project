import request from 'supertest';

import { createApp } from '../../../src/app';
import * as gamesDb from '../../../src/game/db/games';
import * as push from '../../../src/core/notifications/push';
import type * as MockEnv from '../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testPlayer } from '../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const GAME_ID = 42;
const ORGANIZER_ID = 1;
const OTHER_PLAYER_ID = 3;

cleanUpAfterTests();

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
    // 1. sign in as the player (not what this test checks)
    const player = testPlayer();
    const token = signIn(player);
    // the organizer is told a player left, notifications aren't checked here
    jest.spyOn(push, 'sendPushToUser').mockResolvedValue(0);

    // 2. make a mock game: an upcoming game with the player and another player in it
    const players = [ORGANIZER_ID, player.userId, OTHER_PLAYER_ID];
    const game = testGame({ gameId: GAME_ID, organizerId: ORGANIZER_ID });
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
