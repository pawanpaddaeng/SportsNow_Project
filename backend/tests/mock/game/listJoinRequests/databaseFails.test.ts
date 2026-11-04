import * as gamesDb from '../../../../src/game/db/games';
import * as joinRequestsDb from '../../../../src/game/db/joinRequests';
import type * as MockEnv from '../../mockEnv';
import { api, cleanUpAfterTests, GAME_ID, signIn, testGame, testOrganizer, withToken } from '../../helpers';

jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

cleanUpAfterTests();

// Interface GET /api/games/{gameId}/join-requests
describe('Mocked: GET /api/games/{gameId}/join-requests', () => {
  // Mocked behavior: getGameById returns the organizer's game and getPendingJoinRequests
  //   throws, as if the database failed. Sign in is mocked only so the route can run
  // Input: the organizer opens the list for game 42
  // Expected status code: 500
  // Expected behavior: the error is handled and nothing changes
  // Expected output: { msg: "server_error", error: "Could not load join requests, try again later" }
  test('Database fails', async () => {
    // 1. sign in as the organizer (not what this test checks)
    const token = signIn(testOrganizer());

    // 2. make reading the requests fail
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(testGame());
    const getPendingJoinRequests = jest.spyOn(joinRequestsDb, 'getPendingJoinRequests')
      .mockRejectedValue(new Error('database down'));

    // 3. open the list through the interface
    const response = await api.get(`/api/games/${GAME_ID}/join-requests`).set(withToken(token));

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load join requests, try again later' });
    expect(getPendingJoinRequests).toHaveBeenCalledWith(GAME_ID);
  });
});
