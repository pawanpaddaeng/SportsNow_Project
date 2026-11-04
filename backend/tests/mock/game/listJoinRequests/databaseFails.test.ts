import request from 'supertest';

import { createApp } from '../../../../src/app';
import * as gamesDb from '../../../../src/game/db/games';
import * as joinRequestsDb from '../../../../src/game/db/joinRequests';
import type * as MockEnv from '../../mockEnv';
import { cleanUpAfterTests, signIn, testGame, testOrganizer } from '../../helpers';

// the server reads its settings when it loads, mocked ones are enough (jest.mock runs before the imports)
jest.mock('../../../../src/core/config/env', () => jest.requireActual<typeof MockEnv>('../../mockEnv'));

const GAME_ID = 42;

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
    const organizer = testOrganizer();
    const token = signIn(organizer);

    // 2. make reading the requests fail
    jest.spyOn(gamesDb, 'getGameById').mockResolvedValue(
      testGame({ gameId: GAME_ID, organizerId: organizer.userId }));
    const getPendingJoinRequests = jest.spyOn(joinRequestsDb, 'getPendingJoinRequests')
      .mockRejectedValue(new Error('database down'));

    // 3. open the list through the interface
    const response = await request(createApp())
      .get(`/api/games/${GAME_ID}/join-requests`)
      .set('Authorization', `Bearer ${token}`);

    // 4. check the error reply
    expect(response.status).toBe(500);
    expect(response.body).toEqual({ msg: 'server_error', error: 'Could not load join requests, try again later' });
    expect(getPendingJoinRequests).toHaveBeenCalledWith(GAME_ID);
  });
});
