import * as mysqlDb from '../../../../src/core/db/mysql';
import * as gamesDb from '../../../../src/game/db/games';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, GAME_ID, signIn, testGame, testPlayer, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Mocked: GET /api/games/{gameId}', () => {
  // Mocked behavior: getGameById returns the game, getGamePlayers an empty list, and
  //   selectRows no rows for findOrganizer's own lookup in the users table, so neither finds
  //   the organizer. Sign in is mocked only so the route can run
  // Input: a signed in user opens game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not load the game, try again later" }
  test('Organizer not found', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testPlayer());

    // 2. a game whose organizer can't be found anywhere
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    jest.spyOn(gamesDb, 'getGamePlayers').mockResolvedValue([]);
    jest.spyOn(mysqlDb, 'selectRows').mockResolvedValue([]);

    // 3. open the game through the interface
    const response = await withToken(token).get(`/api/games/${GAME_ID}`);

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load the game, try again later' });
  });
});
