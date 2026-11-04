import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testOrganizer, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const LATITUDE = 49.26;
const LONGITUDE = -123.25;

cleanUpAfterTests();

// Interface GET /api/games/discover
describe('Mocked: GET /api/games/discover', () => {
  // Mocked behavior: getDiscoverGames throws, as if the database failed.
  //   Sign in is mocked only so the route can run
  // Input: a signed in user's location
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not load games, try again later" }
  test('Database fails', async () => {
    // 1. sign in (not what this test checks)
    const user = testOrganizer();
    const token = signIn(user);

    // 2. make reading the games fail
    const getDiscoverGames = jest.spyOn(gamesDb, 'getDiscoverGames').mockRejectedValue(new Error('database down'));

    // 3. discover games through the interface
    const response = await withToken(token).get('/api/games/discover')
      .query({ lat: LATITUDE, lng: LONGITUDE });

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load games, try again later' });
    expect(getDiscoverGames).toHaveBeenCalledWith(user.userId, LATITUDE, LONGITUDE);
  });
});
