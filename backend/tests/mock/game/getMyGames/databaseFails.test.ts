import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { api, cleanUpAfterTests, signIn, testOrganizer, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/games/mine
describe('Mocked: GET /api/games/mine', () => {
  // Mocked behavior: getGamesForUser throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: a signed in user asks for their games
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not load games, try again later" }
  test('Database fails', async () => {
    // 1. sign in (not what this test checks)
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. make reading the games fail
    const getGamesForUser = jest.spyOn(gamesDb, 'getGamesForUser').mockRejectedValue(new Error('database down'));

    // 3. get the user's games through the interface
    const response = await api.get('/api/games/mine').set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load games, try again later' });
    expect(getGamesForUser).toHaveBeenCalledWith(organizer.userId);
  });
});
