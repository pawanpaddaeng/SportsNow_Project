import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { api, cleanUpAfterTests, signIn, testOrganizer, validGameBody, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface POST /api/games
describe('Mocked: POST /api/games', () => {
  // Mocked behavior: createGame throws, as if the database failed while saving.
  //   Sign in is mocked only so the route can run
  // Input: a valid game from a signed in user
  // Expected status code: 500
  // Expected behavior: the error is handled and the route stops, so the game is never read back
  // Expected output: { msg: "server_error", error: "Could not create game, try again later" }
  test('Database fails while saving', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. make saving fail
    const createGame = jest.spyOn(gamesDb, 'createGame').mockRejectedValue(new Error('database down'));
    const getGameById = jest.spyOn(gamesDb, 'getGameById');

    // 3. create a valid game starting tomorrow through the interface
    const response = await api.post('/api/games').set(withToken(token))
      .send(validGameBody());

    // 4. check the error reply, and that the route stopped at the failed save
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not create game, try again later' });
    expect(createGame).toHaveBeenCalledTimes(1);
    expect(getGameById).not.toHaveBeenCalled();
  });
});
