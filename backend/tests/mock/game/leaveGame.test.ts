import * as logger from '../../../src/core/logger';
import * as gamesDb from '../../../src/game/db/games';
import type * as MockEnv from '../mockEnv';
import { api, cleanUpAfterTests, GAME_ID, signIn, testPlayer, withToken } from '../helpers';

jest.mock('../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../mockEnv'));

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/leave
describe('Mocked: POST /api/games/{gameId}/leave', () => {
  // Mocked behavior: leaveGame throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: a player leaves game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not leave the game, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the player (not what this test checks)
    const player = testPlayer();
    const token = signIn(player);

    // 2. make leaving fail
    const leaveGame = jest.spyOn(gamesDb, 'leaveGame').mockRejectedValue(new Error('database down'));

    // 3. leave it through the interface
    const response = await api.post(`/api/games/${GAME_ID}/leave`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not leave the game, try again later' });
    expect(leaveGame).toHaveBeenCalledWith(GAME_ID, player.userId);
  });

  // Mocked behavior: leaveGame reports a problem the route has no response for, which the
  //   compiler stops our own database code from doing. logError is watched. Sign in is mocked
  //   only so the route can run
  // Input: a player leaves game 42
  // Expected status code: 500
  // Expected behavior: the unknown problem is logged and answered as a server error, instead
  //   of a crash or a reply without a status
  // Expected output: { msg: "server_error", error: "Could not leave the game, try again later" }
  test('Database reports a problem the route does not know', async () => {
    // 1. sign in as the player (not what this test checks)
    const token = signIn(testPlayer());

    // 2. a leave that fails with a problem no response is listed for
    const unknownProblem = { ok: false, problem: 'unexpected' } as unknown as Awaited<ReturnType<typeof gamesDb.leaveGame>>;
    jest.spyOn(gamesDb, 'leaveGame').mockResolvedValue(unknownProblem);
    const logError = jest.spyOn(logger, 'logError').mockImplementation(() => undefined);

    // 3. leave it through the interface
    const response = await api.post(`/api/games/${GAME_ID}/leave`).set(withToken(token));

    // 4. check the error reply and the log
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not leave the game, try again later' });
    expect(logError).toHaveBeenCalledWith(`POST /api/games/${GAME_ID}/leave failed:`, expect.any(Error));
  });
});
