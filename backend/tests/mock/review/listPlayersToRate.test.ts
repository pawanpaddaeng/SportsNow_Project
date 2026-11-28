import * as gamesDb from '../../../src/game/db/games';
import * as ratingsDb from '../../../src/review/db/ratings';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, GAME_ID, signIn, testGame, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/games/{gameId}/ratings
describe('Mocked: GET /api/games/{gameId}/ratings', () => {
  // Mocked behavior: getGameById throws, as when the database is down
  // Input: a player who showed up opens the list
  // Expected status code: 500
  // Expected behavior: the error is handled and the players are never read
  // Expected output: { msg: "server_error", error: "Could not load the players, try again later" }
  test('Database fails while reading the game', async () => {
    const token = signIn(testPlayer());
    jest.spyOn(gamesDb, 'getGameById').mockRejectedValue(new Error('Forced error'));
    const getPlayersToRate = jest.spyOn(ratingsDb, 'getPlayersToRate');

    const response = await api.get(`/api/games/${GAME_ID}/ratings`).set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load the players, try again later' });
    expect(getPlayersToRate).not.toHaveBeenCalled();
  });

  // Mocked behavior: getGameById finds the game, then getPlayersToRate throws
  // Input: a player who showed up opens the list
  // Expected status code: 500
  // Expected behavior: the error is handled and the server keeps running
  // Expected output: { msg: "server_error", error: "Could not load the players, try again later" }
  test('Database fails while reading the players', async () => {
    const token = signIn(testPlayer());
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame({ status: 'completed' }));
    const getPlayersToRate = jest.spyOn(ratingsDb, 'getPlayersToRate').mockRejectedValue(new Error('Forced error'));

    const response = await api.get(`/api/games/${GAME_ID}/ratings`).set(withToken(token));

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load the players, try again later' });
    expect(getPlayersToRate).toHaveBeenCalledTimes(1);
  });
});