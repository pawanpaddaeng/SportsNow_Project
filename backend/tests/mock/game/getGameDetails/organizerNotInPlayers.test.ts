import * as mysqlDb from '../../../../src/core/db/mysql';
import * as gamesDb from '../../../../src/game/db/games';
import * as joinRequestsDb from '../../../../src/game/db/joinRequests';
import type * as MockEnv from '../../mockEnv';
import {
  api,
  cleanUpAfterTests,
  GAME_ID,
  signIn,
  testGame,
  testOrganizer,
  testPlayer,
  withToken,
} from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/games/{gameId}
describe('Mocked: GET /api/games/{gameId}', () => {
  // Mocked behavior: getGameById returns the game and getGamePlayers an empty list, so the
  //   organizer isn't in the players. selectRows stands in for findOrganizer's own lookup
  //   in the users table. Sign in and the request status are mocked only so the route can run
  // Input: a signed in user opens game 42
  // Expected status code: 200
  // Expected behavior: the organizer comes from the user lookup
  // Expected output: { msg: "success", organizer, ... } with the organizer's details
  test('Organizer not in players list', async () => {
    // 1. sign in (not what this test checks)
    const token = signIn(testPlayer());
    jest.spyOn(joinRequestsDb, 'getJoinRequestStatus').mockResolvedValue(null);

    // 2. a game whose players don't include its organizer
    const organizer = testOrganizer();
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    jest.spyOn(gamesDb, 'getGamePlayers').mockResolvedValue([]);

    // 3. the user lookup finds the organizer
    const selectRows = jest.spyOn(mysqlDb, 'selectRows').mockResolvedValue([{
      user_id: organizer.userId,
      first_name: organizer.firstName,
      last_name: organizer.lastName,
      no_show: 0,
    }]);

    // 4. open the game through the interface
    const response = await api.get(`/api/games/${GAME_ID}`).set(withToken(token));

    // 5. check the organizer came from the lookup
    expect(response.status).toBe(200);
    expect(response.body.msg).toBe('success');
    expect(response.body.organizer).toEqual({
      user_id: organizer.userId,
      first_name: organizer.firstName,
      last_name: organizer.lastName,
      no_show: false,
    });
    expect(selectRows).toHaveBeenCalledTimes(1);
  });
});
