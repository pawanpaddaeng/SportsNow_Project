import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import {
  api,
  cleanUpAfterTests,
  GAME_ID,
  signIn,
  testGame,
  testOrganizer,
  validGameBody,
  withToken,
} from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface PUT /api/games/{gameId}
describe('Mocked: PUT /api/games/{gameId}', () => {
  // Mocked behavior: getGameById returns the game and updateGame throws, as if the database
  //   failed while saving. Sign in is mocked only so the route can run
  // Input: the organizer edits their game
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not update game, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. make saving fail
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    const updateGame = jest.spyOn(gamesDb, 'updateGame').mockRejectedValue(new Error('database down'));

    // 3. edit it through the interface
    const response = await api.put(`/api/games/${GAME_ID}`).set(withToken(token))
      .send(validGameBody());

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not update game, try again later' });
    expect(updateGame).toHaveBeenCalledTimes(1);
  });
});
