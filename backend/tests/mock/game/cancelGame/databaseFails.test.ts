import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { api, cleanUpAfterTests, GAME_ID, signIn, testOrganizer, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface POST /api/games/{gameId}/cancel
describe('Mocked: POST /api/games/{gameId}/cancel', () => {
  // Mocked behavior: cancelGame throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: the organizer cancels game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not cancel game, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. make cancelling fail
    const cancelGame = jest.spyOn(gamesDb, 'cancelGame').mockRejectedValue(new Error('database down'));

    // 3. cancel it through the interface
    const response = await api.post(`/api/games/${GAME_ID}/cancel`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not cancel game, try again later' });
    expect(cancelGame).toHaveBeenCalledWith(GAME_ID, organizer.userId);
  });
});
