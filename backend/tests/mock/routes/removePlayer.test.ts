import request from 'supertest';

import { createApp } from '../../../src/app';
import * as gamesDb from '../../../src/game/db/games';
import * as push from '../../../src/core/notifications/push';
import type * as MockEnv from '../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testOrganizer } from '../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

const GAME_ID = 42;
const PLAYER_ID = 2;

cleanUpAfterTests();

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
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);
    // the removed player is told, notifications aren't checked here
    jest.spyOn(push, 'sendPushToUser').mockResolvedValue(0);

    // 2. make a mock game: an upcoming game with the organizer and a second player in it
    const players = [organizer.userId, PLAYER_ID];
    const game = testGame({ gameId: GAME_ID, organizerId: organizer.userId });
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
